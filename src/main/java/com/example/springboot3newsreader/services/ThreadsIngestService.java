package com.example.springboot3newsreader.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.springboot3newsreader.models.FeedItem;
import com.example.springboot3newsreader.models.NewsArticle;
import com.example.springboot3newsreader.repositories.NewsArticleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Ingest Threads posts via the Scrape Creators API.
 *
 * The Scrape Creators API returns a flat {@code posts[]} array (unlike the
 * previous RapidAPI which nested items inside
 * {@code data.mediaData.edges[].node.thread_items[].post}).
 */
@Service
public class ThreadsIngestService {

  private static final int MAX_TITLE_LENGTH = 160;
  private static final int MAX_SUMMARY_LENGTH = 255;
  private static final int MAX_TAGS_LENGTH = 255;
  private static final int MAX_SOURCE_URL_LENGTH = 1024;
  private static final int MAX_THUMBNAIL_URL_LENGTH = 255;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private ThreadsRapidApiClient threadsRapidApiClient;
  @Autowired
  private NewsArticleRepository newsArticleRepository;
  @Autowired
  private NewsArticleDedupeService newsArticleDedupeService;

  public List<NewsArticle> ingest(FeedItem feedItem) throws Exception {
    if (feedItem == null || feedItem.getUrl() == null || feedItem.getUrl().isBlank()) {
      return new ArrayList<>();
    }

    String username = extractUsername(feedItem.getUrl());
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Threads feed url must look like https://www.threads.com/@{username}");
    }

    System.out.println("[threads] ingest start: @" + username);
    JsonNode response = threadsRapidApiClient.fetchPostsByUsername(username);

    // Validate API response
    boolean success = response.path("success").asBoolean(false);
    if (!success) {
      throw new IllegalStateException("[threads] Scrape Creators API returned success=false for @" + username);
    }

    List<NewsArticle> articles = parsePosts(response, feedItem, username);

    int before = articles.size();
    articles = newsArticleDedupeService.filterNewArticles(articles);
    System.out.println("[threads] after dedupe: " + articles.size()
        + " (removed " + (before - articles.size()) + ")");
    return newsArticleRepository.saveAll(articles);
  }

  String extractUsername(String url) {
    if (url == null) {
      return null;
    }
    String normalized = url.trim();
    normalized = normalized.replace("https://", "").replace("http://", "");
    if (normalized.startsWith("www.")) {
      normalized = normalized.substring(4);
    }
    if (!normalized.startsWith("threads.com/@") && !normalized.startsWith("threads.net/@")) {
      return null;
    }
    String path = normalized.substring(normalized.indexOf("/@") + 2);
    if (path.isBlank()) {
      return null;
    }
    String username = path.split("[/?#]")[0].trim();
    if (username.startsWith("@")) {
      username = username.substring(1);
    }
    return username.isBlank() ? null : username;
  }

  /**
   * Parse the flat posts[] array from the Scrape Creators response.
   */
  private List<NewsArticle> parsePosts(JsonNode root, FeedItem feedItem, String expectedUsername)
      throws Exception {
    List<NewsArticle> articles = new ArrayList<>();
    JsonNode posts = root.path("posts");
    if (!posts.isArray()) {
      return articles;
    }

    for (JsonNode post : posts) {
      if (!post.isObject()) {
        continue;
      }

      // Filter: only keep posts from the expected user
      String postUsername = post.path("user").path("username").asText("");
      if (!postUsername.isBlank() && !postUsername.equalsIgnoreCase(expectedUsername)) {
        continue;
      }

      String postId = extractPostId(post);
      String text = extractPostText(post);
      if (postId == null || postId.isBlank() || text == null || text.isBlank()) {
        continue;
      }

      String username = postUsername.isBlank() ? expectedUsername : postUsername;

      NewsArticle article = new NewsArticle();
      article.setTitle(truncate(text, MAX_TITLE_LENGTH));
      article.setSummary(truncate(text, MAX_SUMMARY_LENGTH));
      article.setSourceName(feedItem.getName());

      // Use the url field directly if available, otherwise build it
      String sourceUrl = post.path("url").asText(null);
      if (sourceUrl == null || sourceUrl.isBlank()) {
        sourceUrl = buildSourceUrl(post, username, postId);
      }
      article.setSourceURL(truncate(sourceUrl, MAX_SOURCE_URL_LENGTH));

      article.setPublishedAt(extractPublishedAt(post));
      article.setScrapedAt(Instant.now().toString());
      article.setCategory(feedItem.getCategory());
      article.setTags(serializeTags(extractTags(username)));

      String thumbnailUrl = extractThumbnailUrl(post);
      if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
        article.setTumbnailURL(truncate(thumbnailUrl, MAX_THUMBNAIL_URL_LENGTH));
      }
      article.setRawContent(objectMapper.writeValueAsString(post));
      articles.add(article);
    }

    System.out.println("[threads] parsed articles: " + articles.size());
    return articles;
  }

  private String extractPostId(JsonNode post) {
    String postId = post.path("pk").asText(null);
    if (postId == null || postId.isBlank()) {
      postId = post.path("id").asText(null);
    }
    return postId;
  }

  private String extractPostText(JsonNode post) {
    String captionText = post.path("caption").path("text").asText(null);
    if (captionText != null && !captionText.isBlank()) {
      return captionText.trim();
    }
    return null;
  }

  private String extractPublishedAt(JsonNode post) {
    long timestamp = post.path("taken_at").asLong(0L);
    if (timestamp <= 0L) {
      return Instant.now().toString();
    }
    return Instant.ofEpochSecond(timestamp).toString();
  }

  private List<String> extractTags(String username) {
    Set<String> tags = new LinkedHashSet<>();
    tags.add("threads");
    if (username != null && !username.isBlank()) {
      tags.add(username.toLowerCase(Locale.ROOT));
    }
    return new ArrayList<>(tags);
  }

  private String extractThumbnailUrl(JsonNode post) {
    // Try user profile pic as fallback thumbnail
    String profilePic = post.path("user").path("profile_pic_url").asText(null);

    // Try image_versions2 candidates (if present in response)
    JsonNode imageCandidates = post.path("image_versions2").path("candidates");
    if (imageCandidates.isArray() && imageCandidates.size() > 0) {
      String url = imageCandidates.get(0).path("url").asText(null);
      if (url != null && !url.isBlank()) {
        return url;
      }
    }

    // Try carousel_media (if present)
    JsonNode carouselMedia = post.path("carousel_media");
    if (carouselMedia.isArray()) {
      for (JsonNode item : carouselMedia) {
        JsonNode candidates = item.path("image_versions2").path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
          String url = candidates.get(0).path("url").asText(null);
          if (url != null && !url.isBlank()) {
            return url;
          }
        }
      }
    }

    // Fall back to profile pic
    return profilePic;
  }

  private String serializeTags(List<String> tags) throws JsonProcessingException {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    String json = objectMapper.writeValueAsString(tags);
    return truncate(json, MAX_TAGS_LENGTH);
  }

  private String buildSourceUrl(JsonNode post, String username, String postId) {
    String code = post.path("code").asText(null);
    String suffix = (code == null || code.isBlank()) ? postId : code;
    return "https://www.threads.com/@" + username + "/post/" + suffix;
  }

  private String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    if (value.length() <= maxLength) {
      return value;
    }
    if (maxLength <= 1) {
      return value.substring(0, maxLength);
    }
    return value.substring(0, maxLength - 1) + "…";
  }
}
