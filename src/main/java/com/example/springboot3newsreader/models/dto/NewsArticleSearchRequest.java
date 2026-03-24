package com.example.springboot3newsreader.models.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = false)
public class NewsArticleSearchRequest {
    private String category;
    private String keyword;
    private List<List<String>> keywordGroups;
    private String groupMode; // AND, OR
    private List<String> sources;
    private List<String> tags;
    private String startDateTime; // ISO 8601 UTC, e.g. 2026-02-13T02:35:00Z
    private String endDateTime; // ISO 8601 UTC, exclusive upper bound
    private String sortOrder; // latest, oldest
    private boolean includeContent = false; // default false
}
