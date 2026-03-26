import requests

FEEDS = [
    {
        "name": "YouTube Blog",
        "url": "https://blog.youtube/rss/",
        "sourceType": "RSS",
        "category": "COMPETITORS",
        "enabled": "true"
    },
    {
        "name": "Meta Instagram News",
        "url": "https://about.fb.com/news/category/technologies/instagram/feed/",
        "sourceType": "RSS",
        "category": "COMPETITORS",
        "enabled": "true"
    },
    {
        "name": "SCMP Twitter-X Topics",
        "url": "https://news.google.com/rss/search?q=site:scmp.com/topics/twitter-x&hl=en-US&gl=US&ceid=US:en",
        "sourceType": "RSS",
        "category": "COMPETITORS",
        "enabled": "true"
    },
    {
        "name": "FIFA Official News",
        "url": "https://news.google.com/rss/search?q=site:fifa.com/en/news&hl=en-US&gl=US&ceid=US:en",
        "sourceType": "RSS",
        "category": "GAMES",
        "enabled": "true"
    },
    {
        "name": "FIFATV YouTube",
        "url": "https://www.youtube.com/feeds/videos.xml?user=FIFATV", 
        "sourceType": "RSS",
        "category": "GAMES",
        "enabled": "true"
    },
    {
        "name": "AP News Soccer",
        "url": "https://news.google.com/rss/search?q=site:apnews.com/hub/soccer&hl=en-US&gl=US&ceid=US:en",
        "sourceType": "RSS",
        "category": "GAMES",
        "enabled": "true"
    },
    {
        "name": "The Verge",
        "url": "https://www.theverge.com/rss/index.xml",
        "sourceType": "RSS",
        "category": "AI",
        "enabled": "true"
    },
    {
        "name": "SocialMediaToday",
        "url": "https://www.socialmediatoday.com/feeds/news/",
        "sourceType": "RSS",
        "category": "COMPETITORS",
        "enabled": "true"
    },
    {
        "name": "Mashable Social Media",
        "url": "https://news.google.com/rss/search?q=site:mashable.com/tech/social-media/&hl=en-US&gl=US&ceid=US:en",
        "sourceType": "RSS",
        "category": "COMPETITORS",
        "enabled": "true"
    },
    {
        "name": "Engadget",
        "url": "https://www.engadget.com/rss.xml",
        "sourceType": "RSS",
        "category": "AI",
        "enabled": "true"
    },
    {
        "name": "Bloomberg Technology",
        "url": "https://news.google.com/rss/search?q=site:bloomberg.com/technology&hl=en-US&gl=US&ceid=US:en",
        "sourceType": "RSS",
        "category": "AI",
        "enabled": "true"
    },
    {
        "name": "Reuters Technology",
        "url": "https://news.google.com/rss/search?q=site:reuters.com/technology/&hl=en-US&gl=US&ceid=US:en",
        "sourceType": "RSS",
        "category": "AI",
        "enabled": "true"
    }
]

def add_feed(feed):
    try:
        url = "http://localhost:9090/feeds/new"
        response = requests.post(url, data=feed, timeout=5)
        if response.status_code == 201 or response.status_code == 200:
            print(f"✅ Added: {feed['name']}")
        elif response.status_code == 409:
            print(f"⚠️ Already exists: {feed['name']}")
        else:
            print(f"❌ Failed to add {feed['name']}: {response.text}")
    except Exception as e:
        print(f"❌ Error for {feed['name']}: {e}")

if __name__ == "__main__":
    for feed in FEEDS:
        add_feed(feed)
