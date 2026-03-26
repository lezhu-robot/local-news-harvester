import requests

# 这些是我们精挑细选或全新手写的高质量 RSSHub 路由
RSSHUB_FEEDS = [
    {"name": "FIFA Official News", "url": "http://localhost:1200/fifa/news", "sourceType": "RSS", "category": "COMPETITORS", "enabled": "true"},
    {"name": "SocialMediaToday", "url": "http://localhost:1200/socialmediatoday/news", "sourceType": "RSS", "category": "COMPETITORS", "enabled": "true"},
    {"name": "Mashable Social Media", "url": "http://localhost:1200/mashable/category/tech/social-media", "sourceType": "RSS", "category": "COMPETITORS", "enabled": "true"},
    {"name": "The Verge", "url": "http://localhost:1200/theverge", "sourceType": "RSS", "category": "AI", "enabled": "true"},
    {"name": "Engadget", "url": "http://localhost:1200/engadget/home", "sourceType": "RSS", "category": "AI", "enabled": "true"},
    {"name": "Bloomberg Technology", "url": "http://localhost:1200/bloomberg", "sourceType": "RSS", "category": "AI", "enabled": "true"},
    {"name": "Reuters Technology", "url": "http://localhost:1200/reuters/technology", "sourceType": "RSS", "category": "AI", "enabled": "true"},
    {"name": "AP News Soccer", "url": "http://localhost:1200/apnews/topics/soccer", "sourceType": "RSS", "category": "GAMES", "enabled": "true"},
    {"name": "SCMP Twitter-X Topics", "url": "http://localhost:1200/scmp/topics/twitter-x", "sourceType": "RSS", "category": "COMPETITORS", "enabled": "true"}
]

def update_feed(feed):
    url = "http://localhost:9090/feeds/new"
    try:
        # 重复调用 new 接口在 Spring Boot 后端大多会更新或报错重复，为了确保我们可以直接暴力重新插入/更新
        response = requests.post(url, data=feed, timeout=5)
        if response.status_code in [200, 201]:
            print(f"✅ Upgrade to RSSHub route: {feed['name']} ({feed['url']})")
        else:
            print(f"⚠️ Failed or already handled {feed['name']} - status {response.status_code}")
    except Exception as e:
        print(f"❌ Error linking {feed['name']}: {e}")

if __name__ == "__main__":
    print("Switching data sources to native high-quality RSSHub Routes...")
    for feed in RSSHUB_FEEDS:
        update_feed(feed)
