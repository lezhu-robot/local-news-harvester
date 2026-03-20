package com.example.springboot3newsreader.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ScheduledTasks {

    @Autowired
    private NewsArticleService newsArticleService;

    // 每1小时执行一次 (cron: 秒 分 时 日 月 周)
    // 0 0 * * * ? -> 每小时的第0分0秒执行
    @Scheduled(cron = "0 0,30 * * * ?")
    public void scheduleRefresh() {
        System.out.println("[Scheduled] Starting hourly feed refresh at " + LocalDateTime.now());
        try {
            newsArticleService.refreshFromRssFeeds();
            System.out.println("[Scheduled] Hourly feed refresh completed at " + LocalDateTime.now());
        } catch (Exception e) {
            System.err.println("[Scheduled] Hourly feed refresh failed: " + e.getMessage());
        }
    }
}
