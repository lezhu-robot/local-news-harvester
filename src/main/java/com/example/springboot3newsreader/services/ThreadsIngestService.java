package com.example.springboot3newsreader.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.springboot3newsreader.models.FeedItem;
import com.example.springboot3newsreader.models.NewsArticle;
import com.example.springboot3newsreader.repositories.NewsArticleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ThreadsIngestService {

  private static final int MAX_TITLE_LENGTH = 160;
  private static final int MAX_SUMMARY_LENGTH = 255;
  private static final int MAX_TAGS_LENGTH = 255;
  private static final int MAX_SOURCE_URL_LENGTH = 1024;
  private static final int MAX_THUMBNAIL_URL_LENGTH = 255;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ConcurrentMap<String, String> userIdCache = new ConcurrentHashMap<>();

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

    String userId = resolveUserId(username);
    System.out.println("[threads] ingest start: @" + username);
    JsonNode posts = threadsRapidApiClient.fetchUserPosts(userId);
    List<NewsArticle> articles = parsePosts(posts, feedItem, username, userId);

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

  private String resolveUserId(String username) throws Exception {
    String cacheKey = username.toLowerCase(Locale.ROOT);
    String cached = userIdCache.get(cacheKey);
    if (cached != null && !cached.isBlank()) {
      return cached;
    }

    JsonNode userPayload = threadsRapidApiClient.fetchUserByUsername(username);
    String resolved = extractUserId(userPayload, username);
    if (resolved == null || resolved.isBlank()) {
      throw new IllegalStateException("Unable to resolve Threads user id for @" + username);
    }
    userIdCache.put(cacheKey, resolved);
    return resolved;
  }

  private String extractUserId(JsonNode root, String username) {
    JsonNode user = root.path("data").path("user");
    if (!user.isObject()) {
      return null;
    }
    String resolvedUsername = user.path("username").asText("");
    if (!resolvedUsername.isBlank() && !resolvedUsername.equalsIgnoreCase(username)) {
      return null;
    }
    String userId = user.path("pk").asText(null);
    if (userId == null || userId.isBlank()) {
      userId = user.path("id").asText(null);
    }
    return userId;
  }

  private List<NewsArticle> parsePosts(JsonNode root, FeedItem feedItem, String fallbackUsername, String userId)
      throws Exception {
    List<NewsArticle> articles = new ArrayList<>();
    JsonNode edges = root.path("data").path("mediaData").path("edges");
    if (!edges.isArray()) {
      return articles;
    }

    for (JsonNode edge : edges) {
      JsonNode threadItems = edge.path("node").path("thread_items");
      if (!threadItems.isArray()) {
        continue;
      }
      for (JsonNode threadItem : threadItems) {
        JsonNode post = threadItem.path("post");
        if (!post.isObject()) {
          continue;
        }
        if (shouldSkipPost(post, userId)) {
          continue;
        }

        String postId = extractPostId(post);
        String username = extractAuthorUsername(post, fallbackUsername);
        String text = extractPostText(post);
        if (postId == null || postId.isBlank() || text == null || text.isBlank()) {
          continue;
        }

        NewsArticle article = new NewsArticle();
        article.setTitle(truncate(text, MAX_TITLE_LENGTH));
        article.setSummary(truncate(text, MAX_SUMMARY_LENGTH));
        article.setSourceName(feedItem.getName());
        article.setSourceURL(truncate(buildSourceUrl(post, username, postId), MAX_SOURCE_URL_LENGTH));
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
    }

    System.out.println("[threads] parsed articles: " + articles.size());
    return articles;
  }

  private boolean shouldSkipPost(JsonNode post, String expectedUserId) {
    JsonNode user = post.path("user");
    String authorId = user.path("id").asText("");
    if (!authorId.isBlank() && expectedUserId != null && !expectedUserId.isBlank() && !expectedUserId.equals(authorId)) {
      return true;
    }

    JsonNode appInfo = post.path("text_post_app_info");
    if (appInfo.path("is_reply").asBoolean(false)) {
      return true;
    }
    JsonNode pinnedInfo = appInfo.path("pinned_post_info");
    if (pinnedInfo.path("is_pinned_to_profile").asBoolean(false)
        || pinnedInfo.path("is_pinned_to_parent_post").asBoolean(false)) {
      return true;
    }
    return false;
  }

  private String extractPostId(JsonNode post) {
    String postId = post.path("pk").asText(null);
    if (postId == null || postId.isBlank()) {
      postId = post.path("id").asText(null);
    }
    return postId;
  }

  private String extractAuthorUsername(JsonNode post, String fallbackUsername) {
    String username = post.path("user").path("username").asText(null);
    if (username != null && !username.isBlank()) {
      return username;
    }
    return fallbackUsername;
  }

  private String extractPostText(JsonNode post) {
    String captionText = post.path("caption").path("text").asText(null);
    if (captionText != null && !captionText.isBlank()) {
      return captionText.trim();
    }

    JsonNode fragments = post.path("text_post_app_info").path("text_fragments").path("fragments");
    if (!fragments.isArray()) {
      return null;
    }
    StringBuilder text = new StringBuilder();
    for (JsonNode fragment : fragments) {
      String piece = fragment.path("plaintext").asText(null);
      if (piece != null && !piece.isBlank()) {
        text.append(piece);
      }
    }
    String normalized = text.toString().trim();
    return normalized.isBlank() ? null : normalized;
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
    JsonNode imageCandidates = post.path("image_versions2").path("candidates");
    if (imageCandidates.isArray() && imageCandidates.size() > 0) {
      String url = imageCandidates.get(0).path("url").asText(null);
      if (url != null && !url.isBlank()) {
        return url;
      }
    }

    JsonNode linkedInlineMedia = post.path("text_post_app_info").path("linked_inline_media");
    JsonNode linkedCandidates = linkedInlineMedia.path("image_versions2").path("candidates");
    if (linkedCandidates.isArray() && linkedCandidates.size() > 0) {
      String url = linkedCandidates.get(0).path("url").asText(null);
      if (url != null && !url.isBlank()) {
        return url;
      }
    }

    String previewImage = post.path("text_post_app_info").path("link_preview_attachment").path("image_url").asText(null);
    if (previewImage != null && !previewImage.isBlank()) {
      return previewImage;
    }

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
    return null;
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
