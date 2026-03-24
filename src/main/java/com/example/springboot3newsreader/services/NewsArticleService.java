package com.example.springboot3newsreader.services;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.springboot3newsreader.models.FeedItem;
import com.example.springboot3newsreader.models.NewsArticle;
import com.example.springboot3newsreader.repositories.FeedItemRepository;
import com.example.springboot3newsreader.models.NewsCategory;
import com.example.springboot3newsreader.models.dto.NewsArticleSearchRequest;

import com.example.springboot3newsreader.repositories.NewsArticleRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
public class NewsArticleService {

  @Autowired
  NewsArticleRepository newsArticleRepository;
  @Autowired
  FeedItemRepository feedItemRepository;
  @Autowired
  IngestPipelineService ingestPipelineService;

  public List<NewsArticle> getAll() {
    return newsArticleRepository.findAll();
  }

  public Optional<NewsArticle> getById(Long id) {
    return newsArticleRepository.findById(id);
  }

  public NewsArticle save(NewsArticle newsArticle) {
    return newsArticleRepository.save(newsArticle);
  }

  public List<NewsArticle> saveAll(List<NewsArticle> newsArticles) {
    return newsArticleRepository.saveAll(newsArticles);
  }

  @Transactional
  public void deleteBySourceNamePrefix(String prefix) {
    newsArticleRepository.deleteBySourceNameStartingWith(prefix);
  }

  public List<NewsArticle> refreshFromRssFeeds() {
    // 1) 取所有已启用的 RSS/WEB/TWITTER 源
    List<FeedItem> allFeeds = feedItemRepository.findAll();
    List<FeedItem> feeds = new ArrayList<>();
    for (FeedItem feed : allFeeds) {
      if (!Boolean.TRUE.equals(feed.getEnabled())) {
        continue;
      }
      if ("RSS".equals(feed.getSourceType())
          || "WEB".equals(feed.getSourceType())
          || "TWITTER".equals(feed.getSourceType())) {
        feeds.add(feed);
      }
    }
    // 2) 走统一 ingest pipeline（与 feeds/new 一致）
    // 2) 走统一 ingest pipeline（与 feeds/new 一致）
    List<NewsArticle> results = ingestPipelineService.ingestAll(feeds);
    // 默认不返回大内容，节省流量
    results.forEach(a -> a.setRawContent(null));
    return results;
  }

  public List<NewsArticle> search(NewsArticleSearchRequest request) {
    Instant startDateTime = parseUtcDateTimeOrNull(request.getStartDateTime(), "startDateTime");
    Instant endDateTime = parseUtcDateTimeOrNull(request.getEndDateTime(), "endDateTime");
    List<List<String>> preciseKeywordGroups = normalizeKeywordGroups(request.getKeywordGroups());
    String preciseKeyword = normalizeKeyword(request.getKeyword());
    List<List<String>> coarseKeywordGroups = buildCoarseKeywordGroups(preciseKeywordGroups, request.getGroupMode());
    String coarseKeyword = buildCoarseKeyword(preciseKeyword);

    Specification<NewsArticle> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // 1. Category
      if (request.getCategory() != null && !request.getCategory().isBlank()) {
        try {
          NewsCategory cat = NewsCategory.valueOf(request.getCategory().toUpperCase());
          predicates.add(cb.equal(root.get("category"), cat));
        } catch (IllegalArgumentException e) {
          // ignore invalid category
        }
      }

      // 2. Keyword / Keyword Groups (Title or Summary)
      if (!coarseKeywordGroups.isEmpty()) {
        predicates.add(buildKeywordGroupsPredicate(root, cb, coarseKeywordGroups, request.getGroupMode()));
      } else {
        if (coarseKeyword != null) {
          predicates.add(buildSingleKeywordPredicate(root, cb, coarseKeyword));
        }
      }

      // 3. Sources
      if (request.getSources() != null && !request.getSources().isEmpty()) {
        predicates.add(root.get("sourceName").in(request.getSources()));
      }

      // 4. Tags (JSON String like check)
      if (request.getTags() != null && !request.getTags().isEmpty()) {
        List<Predicate> tagPredicates = new ArrayList<>();
        for (String tag : request.getTags()) {
          tagPredicates.add(cb.like(root.get("tags"), "%\"" + tag + "\"%"));
        }
        predicates.add(cb.or(tagPredicates.toArray(new Predicate[0])));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };

    Sort sort = Sort.by(Sort.Direction.DESC, "publishedAt");
    if ("oldest".equalsIgnoreCase(request.getSortOrder())) {
      sort = Sort.by(Sort.Direction.ASC, "publishedAt");
    }

    List<NewsArticle> results = newsArticleRepository.findAll(spec, sort).stream()
        .filter(a -> matchesDateTimeRange(a.getPublishedAt(), startDateTime, endDateTime))
        .filter(a -> matchesKeywordRequestPrecisely(a, preciseKeywordGroups, preciseKeyword, request.getGroupMode()))
        .collect(Collectors.toList());

    // Optimize payload: set rawContent to null if not requested
    if (!request.isIncludeContent()) {
      results.forEach(a -> a.setRawContent(null));
    }

    return results;
  }

  private String normalizeKeyword(String keyword) {
    if (keyword == null) {
      return null;
    }
    String trimmed = keyword.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed;
  }

  private List<List<String>> normalizeKeywordGroups(List<List<String>> keywordGroups) {
    if (keywordGroups == null || keywordGroups.isEmpty()) {
      return List.of();
    }

    List<List<String>> normalizedGroups = new ArrayList<>();
    for (List<String> keywordGroup : keywordGroups) {
      if (keywordGroup == null || keywordGroup.isEmpty()) {
        continue;
      }

      List<String> normalizedGroup = new ArrayList<>();
      for (String keyword : keywordGroup) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword != null) {
          normalizedGroup.add(normalizedKeyword);
        }
      }

      if (!normalizedGroup.isEmpty()) {
        normalizedGroups.add(normalizedGroup);
      }
    }
    return normalizedGroups;
  }

  private String buildCoarseKeyword(String keyword) {
    if (keyword == null || isRiskyShortAsciiKeyword(keyword)) {
      return null;
    }
    return keyword;
  }

  private List<List<String>> buildCoarseKeywordGroups(List<List<String>> keywordGroups, String groupMode) {
    if (keywordGroups.isEmpty()) {
      return List.of();
    }

    for (List<String> keywordGroup : keywordGroups) {
      if (containsRiskyShortAsciiKeyword(keywordGroup)) {
        if ("OR".equalsIgnoreCase(groupMode)) {
          return List.of();
        }
        return buildAndModeCoarseKeywordGroups(keywordGroups);
      }
    }
    return keywordGroups;
  }

  private List<List<String>> buildAndModeCoarseKeywordGroups(List<List<String>> keywordGroups) {
    List<List<String>> coarseGroups = new ArrayList<>();
    for (List<String> keywordGroup : keywordGroups) {
      if (!containsRiskyShortAsciiKeyword(keywordGroup)) {
        coarseGroups.add(keywordGroup);
      }
    }
    return coarseGroups;
  }

  private boolean containsRiskyShortAsciiKeyword(List<String> keywordGroup) {
    for (String keyword : keywordGroup) {
      if (isRiskyShortAsciiKeyword(keyword)) {
        return true;
      }
    }
    return false;
  }

  private boolean isRiskyShortAsciiKeyword(String keyword) {
    return keyword != null && keyword.matches("^[A-Za-z]{1,2}$");
  }

  private Predicate buildSingleKeywordPredicate(
      Root<NewsArticle> root,
      CriteriaBuilder cb,
      String keywordTerm) {
    String likePattern = "%" + keywordTerm.toLowerCase() + "%";
    Predicate titleMatch = cb.like(cb.lower(root.get("title")), likePattern);
    Predicate summaryMatch = cb.like(cb.lower(root.get("summary")), likePattern);
    return cb.or(titleMatch, summaryMatch);
  }

  private Predicate buildKeywordGroupPredicate(
      Root<NewsArticle> root,
      CriteriaBuilder cb,
      List<String> keywordGroup) {
    List<Predicate> groupPredicates = new ArrayList<>();
    for (String keywordTerm : keywordGroup) {
      groupPredicates.add(buildSingleKeywordPredicate(root, cb, keywordTerm));
    }
    return cb.or(groupPredicates.toArray(new Predicate[0]));
  }

  private Predicate buildKeywordGroupsPredicate(
      Root<NewsArticle> root,
      CriteriaBuilder cb,
      List<List<String>> keywordGroups,
      String groupMode) {
    List<Predicate> groupPredicates = new ArrayList<>();
    for (List<String> keywordGroup : keywordGroups) {
      groupPredicates.add(buildKeywordGroupPredicate(root, cb, keywordGroup));
    }

    if ("OR".equalsIgnoreCase(groupMode)) {
      return cb.or(groupPredicates.toArray(new Predicate[0]));
    }
    return cb.and(groupPredicates.toArray(new Predicate[0]));
  }

  private boolean matchesKeywordRequestPrecisely(
      NewsArticle article,
      List<List<String>> keywordGroups,
      String keyword,
      String groupMode) {
    if (!keywordGroups.isEmpty()) {
      return matchesKeywordGroupsPrecisely(buildSearchableText(article), keywordGroups, groupMode);
    }
    if (keyword != null) {
      return matchesKeywordTermPrecisely(buildSearchableText(article), keyword);
    }
    return true;
  }

  private String buildSearchableText(NewsArticle article) {
    String title = article.getTitle() == null ? "" : article.getTitle();
    String summary = article.getSummary() == null ? "" : article.getSummary();
    return title + "\n" + summary;
  }

  private boolean matchesKeywordGroupsPrecisely(
      String searchableText,
      List<List<String>> keywordGroups,
      String groupMode) {
    if ("OR".equalsIgnoreCase(groupMode)) {
      for (List<String> keywordGroup : keywordGroups) {
        if (matchesKeywordGroupPrecisely(searchableText, keywordGroup)) {
          return true;
        }
      }
      return false;
    }

    for (List<String> keywordGroup : keywordGroups) {
      if (!matchesKeywordGroupPrecisely(searchableText, keywordGroup)) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesKeywordGroupPrecisely(String searchableText, List<String> keywordGroup) {
    for (String keyword : keywordGroup) {
      if (matchesKeywordTermPrecisely(searchableText, keyword)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesKeywordTermPrecisely(String searchableText, String keyword) {
    String loweredText = searchableText.toLowerCase(Locale.ROOT);
    String loweredKeyword = keyword.toLowerCase(Locale.ROOT);
    if (containsCjk(keyword)) {
      return loweredText.contains(loweredKeyword);
    }

    String pattern = "(?iu)(?<![\\p{L}\\p{N}_])"
        + Pattern.quote(keyword)
        + "(?![\\p{L}\\p{N}_])";
    return Pattern.compile(pattern).matcher(searchableText).find();
  }

  private boolean containsCjk(String value) {
    for (int i = 0; i < value.length(); i++) {
      Character.UnicodeScript script = Character.UnicodeScript.of(value.charAt(i));
      if (script == Character.UnicodeScript.HAN) {
        return true;
      }
    }
    return false;
  }

  private Instant parseUtcDateTimeOrNull(String rawValue, String fieldName) {
    if (rawValue == null || rawValue.isBlank()) {
      return null;
    }
    if (!rawValue.endsWith("Z")) {
      throw new IllegalArgumentException(fieldName
          + " must be an ISO 8601 UTC datetime with 'Z', e.g. 2026-02-13T02:35:00Z");
    }
    try {
      return Instant.parse(rawValue);
    } catch (DateTimeParseException ex) {
      throw new IllegalArgumentException(fieldName
          + " must be a valid ISO 8601 UTC datetime with 'Z', e.g. 2026-02-13T02:35:00Z");
    }
  }

  private boolean matchesDateTimeRange(String publishedAt, Instant startDateTime, Instant endDateTime) {
    if (startDateTime == null && endDateTime == null) {
      return true;
    }
    if (publishedAt == null || publishedAt.isBlank()) {
      return false;
    }
    final Instant publishedInstant;
    try {
      publishedInstant = Instant.parse(publishedAt);
    } catch (DateTimeParseException ex) {
      return false;
    }

    if (startDateTime != null && publishedInstant.isBefore(startDateTime)) {
      return false;
    }
    if (endDateTime != null && !publishedInstant.isBefore(endDateTime)) {
      return false;
    }
    return true;
  }
}
