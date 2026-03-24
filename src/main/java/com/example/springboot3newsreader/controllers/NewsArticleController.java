package com.example.springboot3newsreader.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springboot3newsreader.ApiResponse;
import com.example.springboot3newsreader.models.NewsArticle;
import com.example.springboot3newsreader.models.NewsCategory;
import com.example.springboot3newsreader.services.NewsArticleService;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.example.springboot3newsreader.models.dto.NewsArticleSearchRequest;

@RestController
@RequestMapping("/api/newsarticles")
public class NewsArticleController {

  @Autowired
  NewsArticleService newsArticleService;

  @GetMapping
  public ResponseEntity<?> getAllNewsArticles() {
    List<NewsArticle> articleList = newsArticleService.getAll();
    return ResponseEntity.ok(new ApiResponse<>(200, "ok", articleList));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> getNewsArticle(@PathVariable Long id) {
    Optional<NewsArticle> theItem = newsArticleService.getById(id);
    if (theItem.isPresent()) {
      return ResponseEntity.ok(new ApiResponse<>(200, "ok", theItem.get()));
    }
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ApiResponse<>(404, "not found", null));
  }

  @GetMapping("/refresh")
  public ResponseEntity<?> getRefreshedContent() {
    List<NewsArticle> updatedArticles = newsArticleService.refreshFromRssFeeds();
    return ResponseEntity.ok(new ApiResponse<>(200, "ok", updatedArticles));
  }

  @PostMapping("/search")
  public ResponseEntity<?> searchArticles(@RequestBody NewsArticleSearchRequest request) {
    List<NewsArticle> results = newsArticleService.search(request);
    return ResponseEntity.ok(new ApiResponse<>(200, "ok", results, results.size()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiResponse<>(400, ex.getMessage(), null));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<?> handleNotReadable(HttpMessageNotReadableException ex) {
    String message = "Invalid request body. Only these fields are supported: "
        + "category, keyword, keywordGroups, groupMode, sources, tags, startDateTime, endDateTime, "
        + "sortOrder, includeContent. "
        + "Datetime fields must be ISO 8601 UTC with 'Z', e.g. 2026-02-13T02:35:00Z.";
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiResponse<>(400, message, null));
  }

  @PostMapping("/seed")
  public ResponseEntity<?> seedToyArticles() {
    String[] sourceUrls = {
        "https://en.wikipedia.org/wiki/Artificial_intelligence",
        "https://en.wikipedia.org/wiki/Machine_learning",
        "https://en.wikipedia.org/wiki/Natural_language_processing",
        "https://en.wikipedia.org/wiki/Computer_vision",
        "https://en.wikipedia.org/wiki/Deep_learning",
        "https://en.wikipedia.org/wiki/Neural_network",
        "https://en.wikipedia.org/wiki/Data_science",
        "https://en.wikipedia.org/wiki/Big_data",
        "https://en.wikipedia.org/wiki/Information_retrieval",
        "https://en.wikipedia.org/wiki/Web_scraping"
    };
    String thumbnailUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/63/Wikipedia-logo.png/320px-Wikipedia-logo.png";
    List<NewsArticle> articles = new ArrayList<>();
    for (int i = 0; i < sourceUrls.length; i++) {
      NewsArticle article = new NewsArticle();
      int index = i + 1;
      article.setTitle("Toy Article " + index);
      article.setSourceURL(sourceUrls[i]);
      article.setSourceName("TOY_SOURCE");
      article.setPublishedAt(String.format("2026-01-%02dT10:00:00Z", index));
      article.setScrapedAt(String.format("2026-01-%02dT10:05:00Z", index));
      article.setSummary("Toy summary " + index);
      article.setTags("[\"toy\",\"seed\"]");
      article.setTumbnailURL(thumbnailUrl);
      article.setCategory(NewsCategory.UNCATEGORIZED);
      article.setRawContent("Toy content " + index);
      articles.add(article);
    }
    List<NewsArticle> saved = newsArticleService.saveAll(articles);
    return ResponseEntity.ok(new ApiResponse<>(200, "seeded", saved));
  }

  @DeleteMapping("/seed")
  public ResponseEntity<?> deleteToyArticles() {
    newsArticleService.deleteBySourceNamePrefix("TOY_");
    return ResponseEntity.ok(new ApiResponse<>(200, "deleted", null));
  }

}
