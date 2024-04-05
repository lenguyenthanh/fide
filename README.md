# Experimental FIDE API

## Run

```bash
cp .env.example .env
docker-compose up -d
sbt backend/run
open curl "http://localhost:9669/docs" // maybe you need to wait a bit for syncing
```
