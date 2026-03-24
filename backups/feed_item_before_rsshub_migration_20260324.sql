/*M!999999\- enable the sandbox mode */ 
-- MariaDB dump 10.19-12.2.2-MariaDB, for debian-linux-gnu (x86_64)
--
-- Host: localhost    Database: news_reader
-- ------------------------------------------------------
-- Server version	12.2.2-MariaDB-ubu2404

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*M!100616 SET @OLD_NOTE_VERBOSITY=@@NOTE_VERBOSITY, NOTE_VERBOSITY=0 */;

--
-- Table structure for table `feed_item`
--

DROP TABLE IF EXISTS `feed_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8mb4 */;
CREATE TABLE `feed_item` (
  `id` bigint(20) NOT NULL,
  `category` enum('AI','COMPETITORS','GAMES','MUSIC','UNCATEGORIZED') DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `source_type` varchar(255) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `url` varchar(255) DEFAULT NULL,
  `etag` varchar(255) DEFAULT NULL,
  `last_modified` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `feed_item`
--

SET @OLD_AUTOCOMMIT=@@AUTOCOMMIT, @@AUTOCOMMIT=0;
LOCK TABLES `feed_item` WRITE;
/*!40000 ALTER TABLE `feed_item` DISABLE KEYS */;
INSERT INTO `feed_item` VALUES
(1,'AI','2026-02-02 12:52:22.245605',0x01,'量子位 (QbitAI)','RSS','2026-02-02 12:52:22.245614','http://43.134.96.131:1200/qbitai/category/%E8%B5%84%E8%AE%AF','\"3c610-7+AgWhH7P8broNTekRoBJD7GgUE\"','Tue, 24 Mar 2026 09:00:12 GMT'),
(2,'AI','2026-02-02 12:52:22.348574',0x01,'机器之心 (Machine Heart)','RSS','2026-02-02 12:52:22.348579','https://www.jiqizhixin.com/rss','W/\"79c1c96ffa2818a96bc7f85e1084b4db\"','Fri, 27 Feb 2026 03:32:14 GMT'),
(3,'AI','2026-02-02 12:52:22.358258',0x01,'新智元 (Aiera)','RSS','2026-02-02 12:52:22.360204','http://43.134.96.131:1200/aiera','\"9cd51-28/zOOyoUN54z4q/EALa9Q1an9s\"','Tue, 24 Mar 2026 03:30:10 GMT'),
(4,'AI','2026-02-02 12:52:22.372579',0x01,'MIT News (AI)','RSS','2026-02-02 12:52:22.372584','http://150.158.113.98:1200/mit/news/topic/artificial-intelligence2','\"75-XKzi1bYrKkNnEBmyYP0Wa+2Z2dw\"','Tue, 24 Mar 2026 10:00:16 GMT'),
(5,'AI','2026-02-02 12:52:22.383613',0x01,'Google官方博客','RSS','2026-02-02 12:52:22.383618','https://blog.google/products/search/rss',NULL,NULL),
(6,'AI','2026-02-02 12:52:22.395042',0x01,'AI base','RSS','2026-02-02 12:52:22.395047','http://43.134.96.131:1200/aibase/news','\"14082-kT/WDH11Txopi0DrEgzzEaOzDIg\"','Tue, 24 Mar 2026 10:00:05 GMT'),
(7,'AI','2026-02-02 12:52:22.403540',0x01,'AI hot','RSS','2026-02-02 12:52:22.403544','http://43.134.96.131:1200/aihot/today','\"59ec9-XNayQ0qjtdcN7tQGuKFJxtp1obw\"','Tue, 24 Mar 2026 10:00:18 GMT'),
(8,'AI','2026-02-02 12:52:22.415494',0x01,'SE roundtable','RSS','2026-02-02 12:52:22.415499','https://www.seroundtable.com/index-full.rdf',NULL,NULL),
(9,'AI','2026-02-02 12:52:22.430576',0x01,'Search Engine Roundtable','RSS','2026-02-02 12:52:22.430581','http://43.134.96.131:1200/seroundtable','\"15e2c-mXqlIYXgAxU8OfeffmuDSopGXk4\"','Mon, 23 Mar 2026 14:30:12 GMT'),
(10,'AI','2026-02-02 12:52:22.442661',0x01,'TestingCatalog','RSS','2026-02-02 12:52:22.442667','http://43.134.96.131:1200/testingcatalog','\"123c8-fIKc31gl8SCINcMCZDP1unfoc38\"','Mon, 23 Mar 2026 20:00:04 GMT'),
(11,'AI','2026-02-02 12:52:22.456025',0x01,'Telegram官网新闻动态','RSS','2026-02-02 12:52:22.456032','http://43.134.96.131:1200/telegramorg/blog','\"36cb6-SJj3lQIo10HMqx7c7AlMuASeZt0\"','Sun, 01 Mar 2026 10:00:04 GMT'),
(12,'GAMES','2026-02-02 12:52:22.466826',0x01,'Pocket Gamer','RSS','2026-02-02 12:52:22.466831','https://www.pocketgamer.com/index.rss',NULL,'Tue, 24 Mar 2026 10:00:00 GMT'),
(13,'GAMES','2026-02-02 12:52:22.480117',0x01,'GameIndustry.biz','RSS','2026-02-02 12:52:22.480123','https://www.gamesindustry.biz/feed',NULL,'Tue, 24 Mar 2026 09:44:26 GMT'),
(14,'GAMES','2026-02-02 12:52:22.486509',0x01,'eurogamer','RSS','2026-02-02 12:52:22.486514','https://www.eurogamer.net/feed',NULL,'Mon, 23 Mar 2026 17:01:12 GMT'),
(15,'GAMES','2026-02-02 12:52:22.494221',0x01,'jayisgames','RSS','2026-02-02 12:52:22.494225','http://43.134.96.131:1200/jayisgames','\"1e3d9-/bHLH96lqkIKRZCSwCeFsqN7Ozc\"','Thu, 19 Mar 2026 18:00:01 GMT'),
(16,'AI','2026-02-02 12:52:22.505394',0x01,'facebook开发者官网新闻','RSS','2026-02-02 12:52:22.505399','http://43.134.96.131:1200/facebookdevelopers/blog','\"453e-rUHfZfw3ep8fr3aO7dLs0p+xHps\"','Mon, 23 Mar 2026 04:30:08 GMT'),
(17,'AI','2026-02-02 12:52:22.510976',0x01,'kwai官网新闻','RSS','2026-02-02 12:52:22.510980','http://43.134.96.131:1200/kwai/newsroom','\"39797-IM9Th48aYcAYchKpRAem8iYQL0I\"','Tue, 17 Mar 2026 10:00:09 GMT'),
(18,'AI','2026-02-02 12:52:22.516340',0x01,'youtube官网新闻','RSS','2026-02-02 12:52:22.516344','http://43.134.96.131:1200/youtubeblog/news-and-events','\"132a7-pSEVk46ON6t8l6DT3cqwEIAf1FE\"','Mon, 23 Mar 2026 04:30:12 GMT'),
(19,'MUSIC','2026-02-02 12:52:22.525436',0x01,'Music Business World','RSS','2026-02-02 12:52:22.525440','http://43.134.96.131:1200/musicbusinessworldwide','\"4b15a-SVIMafqi5dR2RUm9rVrclxJgrxE\"','Mon, 23 Mar 2026 21:30:09 GMT'),
(20,'MUSIC','2026-02-02 12:52:22.532936',0x01,'Music Ally','RSS','2026-02-02 12:52:22.532940','http://43.134.96.131:1200/musically','\"60b3-pJzHDRv8r0EYNCr5t3kpAc5AMYg\"','Tue, 24 Mar 2026 09:30:03 GMT'),
(21,'AI','2026-02-02 12:52:22.541429',0x01,'TechCrunch','RSS','2026-02-02 12:52:22.541434','https://rsshub.app/techcrunch/news',NULL,NULL),
(22,'AI','2026-02-02 12:52:22.548378',0x01,'Semrush','RSS','2026-02-02 12:52:22.548383','http://43.134.96.131:1200/semrush/news/releases/product-news','\"67-VqF9Y32RkCpsVYJr8OkFGmkTRuk\"','Tue, 03 Feb 2026 02:00:04 GMT'),
(23,'AI','2026-02-02 12:52:22.556080',0x01,'Pinterest','RSS','2026-02-02 12:52:22.556084','https://newsroom.pinterest.com/en-gb/feed/news.xml',NULL,NULL),
(24,'AI','2026-02-02 12:52:22.564039',0x01,'TLDR','RSS','2026-02-02 12:52:22.564044','http://43.134.96.131:1200/tldr/tech','\"2b7d2-Bsacz5jaNwp0ql2pHoSmmrkYXPg\"','Mon, 23 Mar 2026 11:30:06 GMT'),
(25,'AI','2026-02-02 12:52:22.569457',0x01,'Meta Newsroom','RSS','2026-02-02 12:52:22.569462','https://about.fb.com/news/feed/','\"3854c5d2447596539f813019f27d5296\"','Fri, 20 Mar 2026 20:31:10 GMT'),
(26,'UNCATEGORIZED','2026-02-02 12:52:22.575413',0x01,'漫剧自习室','RSS','2026-02-02 12:52:22.575418','http://150.158.113.98:4000/feeds/MP_WXS_3562816099.atom','W/\"f4f-wn+tVa5yQ7vMjbji+D34evMCRfw\"',NULL),
(27,'UNCATEGORIZED','2026-02-02 12:52:22.581822',0x01,'短剧自习室','RSS','2026-02-02 12:52:22.581826','http://150.158.113.98:4000/feeds/MP_WXS_3906677264.atom','W/\"f16-Tuwwd17T1bj4CgSBVM1PBj2Lrd8\"',NULL),
(28,'UNCATEGORIZED','2026-02-02 12:52:22.588410',0x01,'DataEye短剧观察','RSS','2026-02-02 12:52:22.588418','http://150.158.113.98:4000/feeds/MP_WXS_3900619621.atom','W/\"10ef-srGpWImBRy8HxmJiCCIODA1g364\"',NULL),
(29,'UNCATEGORIZED','2026-02-02 12:52:22.594998',0x01,'新腕儿','RSS','2026-02-02 12:52:22.595003','http://150.158.113.98:4000/feeds/MP_WXS_3938379011.atom','W/\"eb5-uD9XpqB8SmxORH9QSHYAIZoEWbA\"',NULL),
(49952,'UNCATEGORIZED','2026-03-06 15:30:56.280698',0x01,'X @realDonaldTrump','TWITTER','2026-03-06 15:30:56.280711','https://x.com/realDonaldTrump',NULL,NULL),
(50002,'MUSIC','2026-03-16 09:20:15.328044',0x01,'Pitchfork','RSS','2026-03-16 09:20:15.328052','https://pitchfork.com/feed/feed-news/rss',NULL,NULL),
(50003,'MUSIC','2026-03-16 09:20:15.348668',0x01,'Stereogum','RSS','2026-03-16 09:20:15.348670','https://www.stereogum.com/feed/',NULL,NULL),
(50004,'MUSIC','2026-03-16 09:20:15.356115',0x01,'BrooklynVegan','RSS','2026-03-16 09:20:15.356117','https://www.brooklynvegan.com/feed',NULL,NULL),
(50005,'MUSIC','2026-03-16 09:20:15.363114',0x01,'Alternative Press','RSS','2026-03-16 09:20:15.363117','https://www.altpress.com/feed',NULL,'Mon, 23 Mar 2026 17:44:22 GMT'),
(50006,'MUSIC','2026-03-16 09:20:15.376257',0x01,'NME News','RSS','2026-03-16 09:20:15.376259','http://www.nme.com/rss/news.xml',NULL,NULL),
(50007,'MUSIC','2026-03-16 09:20:15.388144',0x01,'Billboard','RSS','2026-03-16 09:20:15.388147','https://www.billboard.com/feed/','\"cd993e52cb52391c528283ec3dbf55a6\"','Tue, 24 Mar 2026 07:52:50 GMT'),
(50008,'GAMES','2026-03-16 09:46:29.155885',0x01,'TouchArcade','RSS','2026-03-16 09:46:29.155888','https://toucharcade.com/feed','\"66e9749f3d8e839693230b2fdb8fcf4f\"','Fri, 18 Apr 2025 23:30:02 GMT'),
(50009,'GAMES','2026-03-16 09:46:29.162940',0x01,'Pocket Tactics','RSS','2026-03-16 09:46:29.162949','https://pockettactics.com/mainrss.xml',NULL,NULL),
(50010,'GAMES','2026-03-16 09:46:29.169310',0x01,'Droid Gamers','RSS','2026-03-16 09:46:29.169312','https://www.droidgamers.com/feed',NULL,NULL),
(50011,'GAMES','2026-03-16 09:46:29.176176',0x01,'Mobile Gaming Hub','RSS','2026-03-16 09:46:29.176178','https://mobilegaminghub.com/feed','W/\"3fc1fa8c7f1c3522e236dad2c1c49bdd\"','Thu, 19 Mar 2026 15:19:34 GMT'),
(50012,'GAMES','2026-03-16 09:46:29.214629',0x01,'MobileGamer.biz','RSS','2026-03-16 09:46:29.214634','https://mobilegamer.biz/feed/','\"4518be2bdc0c00a1846d1ac3037dc96d\"','Mon, 23 Mar 2026 23:50:19 GMT'),
(50013,'GAMES','2026-03-16 09:46:29.225831',0x01,'Game Developer','RSS','2026-03-16 09:46:29.225834','https://www.gamedeveloper.com/rss.xml',NULL,'Tue, 24 Mar 2026 10:00:15 GMT'),
(50014,'GAMES','2026-03-16 09:46:29.234857',0x01,'Game From Scratch','RSS','2026-03-16 09:46:29.234860','https://gamefromscratch.com/feed',NULL,'Mon, 23 Mar 2026 14:39:14 GMT'),
(50015,'AI','2026-03-16 10:11:48.746576',0x01,'X @testingcatalog','TWITTER','2026-03-16 10:11:48.746580','https://x.com/testingcatalog',NULL,NULL),
(50052,'COMPETITORS','2026-03-23 13:44:44.798289',0x01,'X (formerly Twitter) - South China Morning Post','RSS','2026-03-23 13:44:44.798299','https://www.scmp.com/rss/32391/feed',NULL,NULL);
/*!40000 ALTER TABLE `feed_item` ENABLE KEYS */;
UNLOCK TABLES;
COMMIT;
SET AUTOCOMMIT=@OLD_AUTOCOMMIT;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*M!100616 SET NOTE_VERBOSITY=@OLD_NOTE_VERBOSITY */;

-- Dump completed on 2026-03-24 10:14:13
