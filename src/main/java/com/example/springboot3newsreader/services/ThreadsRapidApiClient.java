package com.example.springboot3newsreader.services;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client for fetching Threads posts via the Scrape Creators API.
 * 
 * Endpoint: GET /v1/threads/user/posts?handle={username}
 * Auth: x-api-key header
 * 
 * Replaces the previous threads-api4.p.rapidapi.com RapidAPI client.
 */
@Service
public class ThreadsRapidApiClient {

  private static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_BACKOFF_MS = 500L;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${app.threads.scrapecreators.base-url:https://api.scrapecreators.com}")
  private String baseUrl;

  @Value("${app.threads.scrapecreators.api-key:}")
  private String apiKey;

  /**
   * Fetch posts for a Threads user by username.
   * Scrape Creators accepts username directly — no need to resolve user_id first.
   */
  public JsonNode fetchPostsByUsername(String username) throws Exception {
    String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
    return executeGet("/v1/threads/user/posts?handle=" + encodedUsername);
  }

  private JsonNode executeGet(String path) throws Exception {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("Threads Scrape Creators API key is not configured");
    }

    String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(normalizedBaseUrl + path))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/json")
        .header("User-Agent", "curl/8.5.0")
        .header("x-api-key", apiKey)
        .GET()
        .build();

    long backoffMs = INITIAL_BACKOFF_MS;
    Exception lastError = null;
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
          return objectMapper.readTree(response.body());
        }
        String responseBody = response.body() == null ? "" : response.body();
        if (statusCode == HttpStatus.TOO_MANY_REQUESTS.value() || statusCode >= 500) {
          lastError = new IOException("Threads Scrape Creators request failed with status " + statusCode + ": " + responseBody);
        } else {
          throw new IllegalStateException("Threads Scrape Creators request failed with status " + statusCode + ": " + responseBody);
        }
      } catch (IOException | InterruptedException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        lastError = e;
      }

      if (attempt < MAX_ATTEMPTS) {
        Thread.sleep(backoffMs);
        backoffMs *= 2;
      }
    }

    throw lastError == null
        ? new IllegalStateException("Threads Scrape Creators request failed")
        : lastError;
  }
}
