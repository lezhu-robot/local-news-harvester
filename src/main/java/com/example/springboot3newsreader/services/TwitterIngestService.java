package com.example.springboot3newsreader.services;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashSet;
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

@Service
public class TwitterIngestService {

  private static final int DEFAULT_TWEET_FETCH_COUNT = 40;
  private static final int MAX_TITLE_LENGTH = 160;
  private static final int MAX_SUMMARY_LENGTH = 255;
  private static final int MAX_TAGS_LENGTH = 255;
  private static final int MAX_SOURCE_URL_LENGTH = 1024;
  private static final int MAX_THUMBNAIL_URL_LENGTH = 255;

  private static final DateTimeFormatter TWITTER_DATE_FORMATTER = new DateTimeFormatterBuilder()
      .parseCaseInsensitive()
      .appendPattern("EEE MMM dd HH:mm:ss Z yyyy")
      .toFormatter(Locale.ENGLISH);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private TwitterRapidApiClient twitterRapidApiClient;
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
      throw new IllegalArgumentException("Twitter feed url must look like https://x.com/{username}");
    }

    System.out.println("[twitter] ingest start: @" + username);
    JsonNode response = twitterRapidApiClient.fetchUserTweets(username);
    List<NewsArticle> articles = parseTimeline(response, feedItem, username);

    int before = articles.size();
    articles = newsArticleDedupeService.filterNewArticles(articles);
    System.out.println("[twitter] after dedupe: " + articles.size()
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
    if (!normalized.startsWith("x.com/") && !normalized.startsWith("twitter.com/")) {
      return null;
    }
    String path = normalized.substring(normalized.indexOf('/') + 1);
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
   * Parse the flat tweets[] array from the Scrape Creators response.
   * The internal tweet structure (rest_id, legacy, core) is identical to twitter241.
   */
  private List<NewsArticle> parseTimeline(JsonNode root, FeedItem feedItem, String fallbackUsername)
      throws JsonProcessingException {
    List<NewsArticle> articles = new ArrayList<>();

    // Scrape Creators returns { tweets: [...] }
    JsonNode tweetsArray = root.path("tweets");
    if (!tweetsArray.isArray()) {
      // Fallback: try the old nested traversal if tweets[] not present
      List<JsonNode> candidates = new ArrayList<>();
      collectTweetCandidates(root, candidates);
      for (JsonNode candidate : candidates) {
        JsonNode tweet = unwrapTweetResult(candidate);
        if (tweet != null) {
          addTweetArticle(tweet, feedItem, fallbackUsername, articles, new HashSet<>());
        }
      }
      System.out.println("[twitter] parsed articles (fallback): " + articles.size());
      return articles;
    }

    Set<String> seenTweetIds = new HashSet<>();
    for (JsonNode tweet : tweetsArray) {
      addTweetArticle(tweet, feedItem, fallbackUsername, articles, seenTweetIds);
    }
    System.out.println("[twitter] parsed articles: " + articles.size());
    return articles;
  }

  private void addTweetArticle(JsonNode tweet, FeedItem feedItem, String fallbackUsername,
      List<NewsArticle> articles, Set<String> seenTweetIds) throws JsonProcessingException {
    if (tweet == null || tweet.isMissingNode() || tweet.isNull()) {
      return;
    }

    String tweetId = tweet.path("rest_id").asText(null);
    if (tweetId == null || tweetId.isBlank() || !seenTweetIds.add(tweetId)) {
      return;
    }
    if (shouldSkipTweet(tweet)) {
      return;
    }

    String username = extractAuthorUsername(tweet, fallbackUsername);
    String text = extractTweetText(tweet);
    if (text == null || text.isBlank()) {
      return;
    }

    // Use the url field from Scrape Creators if available
    String sourceUrl = tweet.path("url").asText(null);
    if (sourceUrl == null || sourceUrl.isBlank()) {
      sourceUrl = "https://x.com/" + username + "/status/" + tweetId;
    }

    NewsArticle article = new NewsArticle();
    article.setTitle(truncate(text, MAX_TITLE_LENGTH));
    article.setSummary(truncate(text, MAX_SUMMARY_LENGTH));
    article.setSourceName(feedItem.getName());
    article.setSourceURL(truncate(sourceUrl, MAX_SOURCE_URL_LENGTH));
    article.setPublishedAt(extractPublishedAt(tweet));
    article.setScrapedAt(Instant.now().toString());
    article.setCategory(feedItem.getCategory());
    article.setTags(serializeTags(extractTags(tweet)));

    String thumbnailUrl = extractThumbnailUrl(tweet);
    if (thumbnailUrl != null && !thumbnailUrl.isBlank()) {
      article.setTumbnailURL(truncate(thumbnailUrl, MAX_THUMBNAIL_URL_LENGTH));
    }
    article.setRawContent(objectMapper.writeValueAsString(tweet));
    articles.add(article);
  }

  private void collectTweetCandidates(JsonNode node, List<JsonNode> results) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return;
    }
    if (node.isObject()) {
      if (node.has("tweet_results") && node.path("tweet_results").has("result")) {
        results.add(node.path("tweet_results").path("result"));
      }
      for (var fields = node.fields(); fields.hasNext();) {
        var field = fields.next();
        if (shouldSkipNestedTweetBranch(field.getKey())) {
          continue;
        }
        collectTweetCandidates(field.getValue(), results);
      }
      return;
    }
    if (node.isArray()) {
      for (JsonNode child : node) {
        collectTweetCandidates(child, results);
      }
    }
  }

  private boolean shouldSkipNestedTweetBranch(String fieldName) {
    return "retweeted_status_result".equals(fieldName)
        || "quoted_status_result".equals(fieldName)
        || "source_status_result".equals(fieldName)
        || "quoted_ref_result".equals(fieldName);
  }

  private JsonNode unwrapTweetResult(JsonNode result) {
    if (result == null || result.isNull() || result.isMissingNode()) {
      return null;
    }
    if (result.has("tweet")) {
      return unwrapTweetResult(result.path("tweet"));
    }
    if (result.has("result") && result.size() == 1) {
      return unwrapTweetResult(result.path("result"));
    }
    if (result.hasNonNull("rest_id") && result.has("legacy")) {
      return result;
    }
    if (result.has("core") && result.has("legacy")) {
      return result;
    }
    return result;
  }

  private boolean shouldSkipTweet(JsonNode tweet) {
    JsonNode legacy = tweet.path("legacy");
    if (!legacy.isObject()) {
      return true;
    }

    if (!legacy.path("in_reply_to_status_id_str").isMissingNode()
        && !legacy.path("in_reply_to_status_id_str").asText("").isBlank()) {
      return true;
    }
    if (!legacy.path("quoted_status_id_str").isMissingNode()
        && !legacy.path("quoted_status_id_str").asText("").isBlank()) {
      return true;
    }
    if (tweet.has("quoted_status_result")) {
      return true;
    }
    if (tweet.has("retweeted_status_result")) {
      return true;
    }
    String fullText = extractTweetText(tweet);
    if (fullText != null && fullText.startsWith("RT @")) {
      return true;
    }
    if (isPinnedTweet(tweet)) {
      return true;
    }
    return false;
  }

  private boolean isPinnedTweet(JsonNode tweet) {
    String tweetId = tweet.path("rest_id").asText("");
    if (tweetId.isBlank()) {
      return false;
    }
    JsonNode pinnedIds = tweet.path("core").path("user_results").path("result").path("legacy").path("pinned_tweet_ids_str");
    if (pinnedIds.isArray()) {
      for (JsonNode pinnedId : pinnedIds) {
        if (tweetId.equals(pinnedId.asText())) {
          return true;
        }
      }
    }
    return false;
  }

  private String extractAuthorUsername(JsonNode tweet, String fallbackUsername) {
    JsonNode userResult = tweet.path("core").path("user_results").path("result");
    String screenName = userResult.path("legacy").path("screen_name").asText(null);
    if (screenName == null || screenName.isBlank()) {
      screenName = userResult.path("core").path("screen_name").asText(null);
    }
    if (screenName != null && !screenName.isBlank()) {
      return screenName;
    }
    return fallbackUsername;
  }

  private String extractTweetText(JsonNode tweet) {
    String noteText = tweet.path("note_tweet").path("note_tweet_results").path("result")
        .path("text").asText(null);
    if (noteText != null && !noteText.isBlank()) {
      return noteText;
    }
    String noteResultsText = tweet.path("note_tweet_results").path("result").path("text").asText(null);
    if (noteResultsText != null && !noteResultsText.isBlank()) {
      return noteResultsText;
    }
    String fullText = tweet.path("legacy").path("full_text").asText(null);
    if (fullText != null && !fullText.isBlank()) {
      return fullText;
    }
    return null;
  }

  private String extractPublishedAt(JsonNode tweet) {
    String createdAt = tweet.path("legacy").path("created_at").asText(null);
    if (createdAt == null || createdAt.isBlank()) {
      return Instant.now().toString();
    }
    try {
      return OffsetDateTime.parse(createdAt, TWITTER_DATE_FORMATTER).toInstant().toString();
    } catch (Exception e) {
      return Instant.now().toString();
    }
  }

  private List<String> extractTags(JsonNode tweet) {
    Set<String> tags = new LinkedHashSet<>();
    JsonNode hashtags = tweet.path("legacy").path("entities").path("hashtags");
    if (hashtags.isArray()) {
      for (JsonNode hashtag : hashtags) {
        String text = hashtag.path("text").asText(null);
        if (text != null && !text.isBlank()) {
          tags.add(text.toLowerCase(Locale.ROOT));
        }
      }
    }
    tags.add("twitter");
    return new ArrayList<>(tags);
  }

  private String extractThumbnailUrl(JsonNode tweet) {
    JsonNode mediaItems = tweet.path("legacy").path("extended_entities").path("media");
    if (!mediaItems.isArray()) {
      mediaItems = tweet.path("legacy").path("entities").path("media");
    }
    if (mediaItems.isArray()) {
      for (JsonNode media : mediaItems) {
        String url = media.path("media_url_https").asText(null);
        if (url == null || url.isBlank()) {
          url = media.path("media_url").asText(null);
        }
        if (url != null && !url.isBlank()) {
          return url;
        }
      }
    }
    return null;
  }

  private String serializeTags(List<String> tags) throws JsonProcessingException {
    List<String> limitedTags = new ArrayList<>();
    for (String tag : tags) {
      limitedTags.add(tag);
      String candidate = objectMapper.writeValueAsString(limitedTags);
      if (candidate.length() > MAX_TAGS_LENGTH) {
        limitedTags.remove(limitedTags.size() - 1);
        break;
      }
    }
    return objectMapper.writeValueAsString(limitedTags);
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength - 3) + "...";
  }
}
