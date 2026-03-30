# Experimental FIDE API

[![Continuous Integration](https://github.com/lenguyenthanh/fide/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/lenguyenthanh/fide/actions/workflows/ci.yml)

## How to use

Check [Open API docs](https://fide.serginho.dev/docs/index.html) thanks [@SergioGlorias](https://github.com/SergioGlorias)

## Development

### Prerequisites:

- Docker

### Run

Also requires JDK 25 with [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)

```bash
cp .env.example .env
docker compose up -d
```

then

```bash
export $(cat .env | xargs) && sbt backend/run
```

or

```bash
sbt backend/stage
export $(cat .env | xargs) && ./modules/backend/target/universal/stage/bin/backend
```

### Usage

```bash
open http://localhost:9669/docs
curl http://localhost:9669/api/players
```

### Database viewer

```bash
COMPOSE_PROFILES=adminer docker compose up -d
```

http://localhost:8180/?pgsql=db&username=admin&db=fide&ns=fide (password: dummy)


### Stress test with Gatling

```bash
docker compose -f docker-compose.gatling.yml up -d
sbt gatling/gatling:test
```

### Before submitting PR

```bash
sbt test
sbt lint
```

### release

```bash
sbt release with-defaults
```

## Run without building

You can use a pre-built Docker image from [GitHub Container Registry](https://github.com/lenguyenthanh/fide/pkgs/container/fide). Here is an example of how to run it with docker-compose:

```yaml
name: fide

services:

  api:
    image: ghcr.io/lenguyenthanh/fide:latest
    environment:
      - HTTP_SHUTDOWN_TIMEOUT=1
      - POSTGRES_HOST=db
      - POSTGRES_PORT=5432
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=dummy
      - POSTGRES_DATABASE=fide
    ports:
      - 9669:9669
    networks:
      - fide_api
    restart: unless-stopped

  db:
    image: postgres:17.4-alpine
    environment:
      POSTGRES_DB: fide
      POSTGRES_USER: admin
      POSTGRES_PASSWORD: dummy
    ports:
      - 5432:5432
    networks:
      - fide_api
    restart: unless-stopped

networks:
  fide_api:
    driver: bridge
```

Then run:

```bash
docker compose up -d
```

## Run CLI without building

You can use the pre-built CLI Docker image from [GitHub Container Registry](https://github.com/lenguyenthanh/fide/pkgs/container/fide-cli) to ingest historical CSV files into the database.

Assuming CSV files are in `./csv-data` and the docker-compose Postgres is running:

```bash
docker run --rm \
  --network fide_fide \
  -v ./csv-data:/data \
  -e POSTGRES_HOST=fide_postgres \
  -e POSTGRES_PORT=5432 \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=dummy \
  -e POSTGRES_DATABASE=fide \
  ghcr.io/lenguyenthanh/fide-cli:latest \
  ingest /data --start 2024-01 --end 2024-12
```
