package com.example.springboot3newsreader.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.springboot3newsreader.models.FeedItem;
import com.example.springboot3newsreader.models.NewsArticle;
import com.example.springboot3newsreader.models.NewsCategory;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

@Service
public class IngestPipelineService {

  @Autowired
  private RssIngestService rssIngestService;
  @Autowired
  private WebIngestService webIngestService;
  @Autowired
  private TwitterIngestService twitterIngestService;
  @Autowired
  private ThreadsIngestService threadsIngestService;

  @Value("${app.feature.web-ingest.enabled:true}")
  private boolean webIngestEnabled;
  @Value("${app.feature.twitter-ingest.enabled:false}")
  private boolean twitterIngestEnabled;
  @Value("${app.feature.threads-ingest.enabled:false}")
  private boolean threadsIngestEnabled;

  public List<NewsArticle> ingestFeed(FeedItem feed) throws Exception {
    if (feed == null || feed.getSourceType() == null) {
      return new ArrayList<>();
    }
    String type = feed.getSourceType();
    NewsCategory category = feed.getCategory();
    if (category == null) {
      category = NewsCategory.UNCATEGORIZED;
    }
    if ("RSS".equals(type)) {
      // Use logic with Etag/Last-Modified support
      return rssIngestService.ingest(feed);
    }
    if ("WEB".equals(type)) {
      if (!webIngestEnabled) {
        System.out.println("Skipping WEB feed ingestion (disabled by config): " + feed.getName());
        return new ArrayList<>();
      }
      return webIngestService.ingest(feed.getUrl(), feed.getName(), category);
    }
    if ("TWITTER".equals(type)) {
      if (!twitterIngestEnabled) {
        System.out.println("Skipping TWITTER feed ingestion (disabled by config): " + feed.getName());
        return new ArrayList<>();
      }
      return twitterIngestService.ingest(feed);
    }
    if ("THREADS".equals(type)) {
      if (!threadsIngestEnabled) {
        System.out.println("Skipping THREADS feed ingestion (disabled by config): " + feed.getName());
        return new ArrayList<>();
      }
      return threadsIngestService.ingest(feed);
    }
    return new ArrayList<>();
  }

  public List<NewsArticle> ingestAll(List<FeedItem> feeds) {
    if (feeds == null || feeds.isEmpty()) {
      return new ArrayList<>();
    }
    return feeds.parallelStream()
        .flatMap(feed -> {
          try {
            return ingestFeed(feed).stream();
          } catch (Exception e) {
            String feedName = feed == null ? "<null>" : String.valueOf(feed.getName());
            String feedType = feed == null ? "<null>" : String.valueOf(feed.getSourceType());
            String feedUrl = feed == null ? "<null>" : String.valueOf(feed.getUrl());
            System.err.println("[ingest] feed failed: type=" + feedType
                + ", name=" + feedName
                + ", url=" + feedUrl
                + ", error=" + e.getMessage());
            e.printStackTrace();
            return java.util.stream.Stream.empty();
          }
        })
        .collect(Collectors.toList());
  }

  @Async
  public void ingestFeedAsync(FeedItem feed) {
    try {
      ingestFeed(feed);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
