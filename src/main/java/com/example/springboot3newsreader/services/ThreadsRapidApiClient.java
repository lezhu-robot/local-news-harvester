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

@Service
public class ThreadsRapidApiClient {

  private static final int MAX_ATTEMPTS = 3;
  private static final long INITIAL_BACKOFF_MS = 500L;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Value("${app.threads.rapidapi.base-url:https://threads-api4.p.rapidapi.com}")
  private String baseUrl;

  @Value("${app.threads.rapidapi.host:threads-api4.p.rapidapi.com}")
  private String rapidApiHost;

  @Value("${app.threads.rapidapi.key:${app.twitter.rapidapi.key:}}")
  private String rapidApiKey;

  public JsonNode fetchUserByUsername(String username) throws Exception {
    String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);
    return executeGet("/api/user/info?username=" + encodedUsername);
  }

  public JsonNode fetchUserPosts(String userId) throws Exception {
    String encodedUserId = URLEncoder.encode(userId, StandardCharsets.UTF_8);
    return executeGet("/api/user/posts?user_id=" + encodedUserId);
  }

  private JsonNode executeGet(String path) throws Exception {
    if (rapidApiKey == null || rapidApiKey.isBlank()) {
      throw new IllegalStateException("Threads RapidAPI key is not configured");
    }

    String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(normalizedBaseUrl + path))
        .timeout(Duration.ofSeconds(20))
        .header("Accept", "application/json")
        .header("Content-Type", "application/json")
        .header("User-Agent", "curl/8.5.0")
        .header("x-rapidapi-key", rapidApiKey)
        .header("x-rapidapi-host", rapidApiHost)
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
          lastError = new IOException("Threads RapidAPI request failed with status " + statusCode + ": " + responseBody);
        } else {
          throw new IllegalStateException("Threads RapidAPI request failed with status " + statusCode + ": " + responseBody);
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
        ? new IllegalStateException("Threads RapidAPI request failed")
        : lastError;
  }
}
