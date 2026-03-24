package com.example.springboot3newsreader.models;

import java.util.Arrays;
import java.util.List;

public enum NewsCategory {
  AI("AI", "AI", 1, true),
  MUSIC("MUSIC", "Music", 2, true),
  GAMES("GAMES", "Games", 3, true),
  COMPETITORS("COMPETITORS", "Competitors", 4, true),
  UNCATEGORIZED("UNCATEGORIZED", "Uncategorized", 99, true);

  private final String key;
  private final String label;
  private final int order;
  private final boolean enabled;

  NewsCategory(String key, String label, int order, boolean enabled) {
    this.key = key;
    this.label = label;
    this.order = order;
    this.enabled = enabled;
  }

  public String getKey() {
    return key;
  }

  public String getLabel() {
    return label;
  }

  public int getOrder() {
    return order;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public static NewsCategory fromKey(String key) {
    if (key == null) {
      return null;
    }
    for (NewsCategory c : values()) {
      if (c.key.equalsIgnoreCase(key)) {
        return c;
      }
    }
    return null;
  }

  public static List<NewsCategory> all() {
    return Arrays.asList(values());
  }
}
