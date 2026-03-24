package com.example.springboot3newsreader.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.example.springboot3newsreader.models.FeedItem;
import com.example.springboot3newsreader.models.NewsArticle;
import com.example.springboot3newsreader.models.NewsCategory;
import com.example.springboot3newsreader.repositories.FeedItemRepository;
import com.example.springboot3newsreader.repositories.NewsArticleRepository;
import com.example.springboot3newsreader.repositories.ThumbnailTaskRepository;
import com.example.springboot3newsreader.services.FeedItemService;
import com.example.springboot3newsreader.services.IngestPipelineService;
import com.example.springboot3newsreader.services.WebIngestService;
import com.example.springboot3newsreader.ApiResponse;
import org.springframework.beans.factory.annotation.Value;

@RestController
public class FormController {

  @Autowired
  private FeedItemService feedItemService;
  @Autowired
  private FeedItemRepository feedItemRepository;
  @Autowired
  private NewsArticleRepository newsArticleRepository;
  @Autowired
  private ThumbnailTaskRepository thumbnailTaskRepository;
  @Autowired
  private IngestPipelineService ingestPipelineService;
  @Autowired
  private WebIngestService webIngestService;

  @Value("${app.feature.web-ingest.enabled:true}")
  private boolean webIngestEnabled;
  @Value("${app.feature.twitter-ingest.enabled:false}")
  private boolean twitterIngestEnabled;

  @PostMapping("/feeds/new")
  public ResponseEntity<?> createFeedItem(FeedItem feedItem) {
    System.out.println("[feeds/new] request received");
    System.out.println("[feeds/new] name=" + feedItem.getName()
        + ", url=" + feedItem.getUrl()
        + ", sourceType=" + feedItem.getSourceType()
        + ", enabled=" + feedItem.getEnabled());
    if (feedItem.getName() == null || feedItem.getName().trim().isEmpty()) {
      System.out.println("[feeds/new] validation failed: name is blank");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "name should not be null!", null));
    }
    if (feedItem.getUrl() == null || feedItem.getUrl().trim().isEmpty()) {
      System.out.println("[feeds/new] validation failed: url is blank");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "url should not be null!", null));
    }
    if (!feedItem.getUrl().startsWith("http://") && !feedItem.getUrl().startsWith("https://")) {
      System.out.println("[feeds/new] validation failed: url must start with http/https");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "url should begin with http or https!", null));
    }
    if (feedItem.getSourceType() == null || feedItem.getSourceType().trim().isEmpty()) {
      System.out.println("[feeds/new] validation failed: sourceType is blank");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "sourceType should not be null!", null));
    }
    if (!feedItem.getSourceType().equals("RSS")
        && !feedItem.getSourceType().equals("WEB")
        && !feedItem.getSourceType().equals("TWITTER")) {
      System.out.println("[feeds/new] validation failed: sourceType must be RSS, WEB or TWITTER");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "sourceType must be RSS, WEB or TWITTER!", null));
    }
    if ("WEB".equals(feedItem.getSourceType()) && !webIngestEnabled) {
      System.out.println("[feeds/new] validation failed: WEB source disabled by feature flag");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "WEB source type is currently disabled by configuration!", null));
    }
    if ("TWITTER".equals(feedItem.getSourceType()) && !twitterIngestEnabled) {
      System.out.println("[feeds/new] validation failed: TWITTER source disabled by feature flag");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "TWITTER source type is currently disabled by configuration!", null));
    }
    if (feedItem.getEnabled() == null) {
      System.out.println("[feeds/new] validation failed: enabled is null");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "enabled should not be null!", null));
    }
    if (feedItem.getCategory() == null) {
      System.out.println("[feeds/new] category missing, default to UNCATEGORIZED");
      feedItem.setCategory(NewsCategory.UNCATEGORIZED);
    }

    String name = feedItem.getName().trim();
    String url = feedItem.getUrl().trim();
    if ("TWITTER".equals(feedItem.getSourceType())
        && !isValidTwitterProfileUrl(url)) {
      System.out.println("[feeds/new] validation failed: twitter url is invalid");
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "twitter url should look like https://x.com/{username}!", null));
    }
    System.out.println("[feeds/new] normalized name=" + name + ", url=" + url);
    if (feedItemRepository.existsByNameAndUrl(name, url)) {
      System.out.println("[feeds/new] duplicate feed found, abort");
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new ApiResponse<>(409, "feed already exists", null));
    }

    feedItem.setName(name);
    feedItem.setUrl(url);

    System.out.println("[feeds/new] saving feed item");
    FeedItem saved = feedItemService.save(feedItem);
    System.out.println("[feeds/new] saved id=" + saved.getId());

    System.out.println("[feeds/new] trigger ingest pipeline async");
    ingestPipelineService.ingestFeedAsync(saved);
    System.out.println("[feeds/new] response 201");
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new ApiResponse<>(200, "ok", saved));
  }

  @PostMapping("/feeds/preview")
  public ResponseEntity<?> previewFeed(FeedItem feedItem) {
    if ("WEB".equals(feedItem.getSourceType()) && !webIngestEnabled) {
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "WEB preview is currently disabled by configuration!", null));
    }
    if (feedItem.getUrl() == null || feedItem.getUrl().trim().isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "url should not be null!", null));
    }
    if (!feedItem.getUrl().startsWith("http://") && !feedItem.getUrl().startsWith("https://")) {
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "url should begin with http or https!", null));
    }
    if (feedItem.getSourceType() == null || feedItem.getSourceType().trim().isEmpty()) {
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "sourceType should not be null!", null));
    }
    if (!feedItem.getSourceType().equals("WEB")) {
      return ResponseEntity.badRequest()
          .body(new ApiResponse<>(400, "preview only supports WEB sourceType!", null));
    }

    String name = feedItem.getName() == null ? "PREVIEW" : feedItem.getName().trim();
    String url = feedItem.getUrl().trim();
    try {
      List<NewsArticle> articles = webIngestService.previewOnly(url, name);
      return ResponseEntity.ok(new ApiResponse<>(200, "ok", articles));
    } catch (Exception e) {
      e.printStackTrace();
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(new ApiResponse<>(500, "preview failed", null));
    }
  }

  private boolean isValidTwitterProfileUrl(String url) {
    String normalized = url == null ? "" : url.trim().toLowerCase();
    return normalized.matches("^https?://(www\\.)?(x|twitter)\\.com/[^/?#]+/?$");
  }

  @PostMapping("/admin/clear")
  public ResponseEntity<?> clearBusinessTables() {
    // 按依赖顺序清空，避免外键约束问题
    thumbnailTaskRepository.deleteAll();
    newsArticleRepository.deleteAll();
    feedItemRepository.deleteAll();
    return ResponseEntity.ok(new ApiResponse<>(200, "cleared", null));
  }

  @PostMapping("/admin/seed-rss")
  public ResponseEntity<?> seedRssFeeds() {
    // Category, Name, Url
    Object[][] seeds = {
        { NewsCategory.AI, "量子位 (QbitAI)", "http://43.165.175.44:1200/qbitai/category/%E8%B5%84%E8%AE%AF" },
        { NewsCategory.AI, "机器之心 (Machine Heart)", "https://www.jiqizhixin.com/rss" },
        { NewsCategory.AI, "新智元 (Aiera)", "http://43.165.175.44:1200/aiera" },
        { NewsCategory.AI, "MIT News (AI)", "http://43.165.175.44:1200/mit/news/topic/artificial-intelligence2" },
        { NewsCategory.AI, "Google官方博客", "https://blog.google/products/search/rss" },
        { NewsCategory.AI, "AI base", "http://43.165.175.44:1200/aibase/news" },
        { NewsCategory.AI, "AI hot", "http://43.165.175.44:1200/aihot/today" },
        { NewsCategory.AI, "SE roundtable", "https://www.seroundtable.com/index-full.rdf" },
        { NewsCategory.AI, "Search Engine Roundtable", "http://43.165.175.44:1200/seroundtable" },
        { NewsCategory.AI, "TestingCatalog", "http://43.165.175.44:1200/testingcatalog" },
        { NewsCategory.AI, "Telegram官网新闻动态", "http://43.165.175.44:1200/telegramorg/blog" },
        { NewsCategory.GAMES, "Pocket Gamer", "https://www.pocketgamer.com/index.rss" },
        { NewsCategory.GAMES, "GameIndustry.biz", "https://www.gamesindustry.biz/feed" },
        { NewsCategory.GAMES, "eurogamer", "https://www.eurogamer.net/feed" },
        { NewsCategory.GAMES, "jayisgames", "http://43.165.175.44:1200/jayisgames" },
        { NewsCategory.AI, "facebook开发者官网新闻", "http://43.165.175.44:1200/facebookdevelopers/blog" },
        { NewsCategory.AI, "kwai官网新闻", "http://43.165.175.44:1200/kwai/newsroom" },
        { NewsCategory.AI, "youtube官网新闻", "http://43.165.175.44:1200/youtubeblog/news-and-events" },
        { NewsCategory.MUSIC, "Music Business World", "http://43.165.175.44:1200/musicbusinessworldwide" },
        { NewsCategory.MUSIC, "Music Ally", "http://43.165.175.44:1200/musically" },
        { NewsCategory.AI, "TechCrunch", "https://rsshub.app/techcrunch/news" },
        { NewsCategory.AI, "Semrush", "http://43.165.175.44:1200/semrush/news/releases/product-news" },
        { NewsCategory.AI, "Pinterest", "https://newsroom.pinterest.com/en-gb/feed/news.xml" },
        { NewsCategory.AI, "TLDR", "http://43.165.175.44:1200/tldr/tech" },
        { NewsCategory.AI, "Meta Newsroom", "https://about.fb.com/news/feed/" },
        { NewsCategory.UNCATEGORIZED, "漫剧自习室", "http://150.158.113.98:4000/feeds/MP_WXS_3562816099.atom" },
        { NewsCategory.UNCATEGORIZED, "短剧自习室", "http://150.158.113.98:4000/feeds/MP_WXS_3906677264.atom" },
        { NewsCategory.UNCATEGORIZED, "DataEye短剧观察", "http://150.158.113.98:4000/feeds/MP_WXS_3900619621.atom" },
        { NewsCategory.UNCATEGORIZED, "新腕儿", "http://150.158.113.98:4000/feeds/MP_WXS_3938379011.atom" }
    };

    int added = 0;
    for (Object[] row : seeds) {
      NewsCategory cat = (NewsCategory) row[0];
      String name = (String) row[1];
      String url = (String) row[2];

      if (feedItemRepository.existsByNameAndUrl(name, url)) {
        continue;
      }

      FeedItem item = new FeedItem();
      item.setName(name);
      item.setUrl(url);
      item.setCategory(cat);
      item.setSourceType("RSS");
      item.setEnabled(true);

      feedItemService.save(item);
      ingestPipelineService.ingestFeedAsync(item);
      added++;
    }
    return ResponseEntity.ok(new ApiResponse<>(200, "seeded " + added + " feeds", null));
  }

}
