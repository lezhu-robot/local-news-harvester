# Migration Report

Date: 2026-03-24
Target server: 43.165.175.44
Target path: /home/ubuntu/workspace/local-news-harvester

## Summary
This project was migrated to the new server on 2026-03-24.
The application stack was deployed with Docker Compose and verified online after migration.

## Scope
- Cloned project code into /home/ubuntu/workspace/local-news-harvester
- Migrated MariaDB application data from the original server
- Rebuilt and started Docker services on the new server
- Moved Twitter RapidAPI key loading from hardcoded config to .env
- Verified public API availability on the new server

## Configuration Changes
The following configuration adjustments were applied before migration:
- src/main/resources/application.properties
  - app.twitter.rapidapi.key now reads from environment variable APP_TWITTER_RAPIDAPI_KEY
- compose.yaml
  - passes APP_TWITTER_RAPIDAPI_KEY into the app container
- .env.example
  - added APP_TWITTER_RAPIDAPI_KEY= placeholder
- .env
  - stores the real APP_TWITTER_RAPIDAPI_KEY value on the server

Related Git commit pushed before deployment:
- 444a767 - chore: load twitter rapidapi key from env

## Data Migration
- Source database exported from the original MariaDB container
- Export file used: news_reader.sql
- Export file copied to the new server
- Database imported into the new MariaDB instance on the new server

## Services Deployed
Docker Compose services deployed on the new server:
- database -> MariaDB
- app -> Spring Boot backend
- phpmyadmin -> database admin UI

Published ports:
- 9090 -> application API
- 8081 -> phpMyAdmin
- 3306 -> MariaDB

## Verification Performed
The following checks were completed on 2026-03-24:
- Confirmed docker, docker compose, and git were already installed on the new server
- Confirmed Docker containers started successfully with docker compose ps
- Confirmed Spring Boot app started successfully from container logs
- Confirmed API endpoint GET /api/categories returned HTTP 200
- Confirmed API endpoint GET /api/newsarticles returned HTTP 200 and existing article data

## Verification Results
Public endpoints after migration:
- API: http://43.165.175.44:9090
- phpMyAdmin: http://43.165.175.44:8081

Observed successful API responses:
- GET http://43.165.175.44:9090/api/categories
- GET http://43.165.175.44:9090/api/newsarticles

## Files Left In Place
The SQL backup file currently remains on the new server for rollback or inspection:
- /home/ubuntu/workspace/local-news-harvester/news_reader.sql

## Notes
- Flutter client configuration was not changed during this migration.
- The server currently exposes ports 3306 and 8081 publicly; firewall or network restrictions are recommended if this is a production environment.
- The project is running successfully on the new server as of 2026-03-24.
