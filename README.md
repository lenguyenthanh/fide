# Experimental FIDE API

## How to use

Check [Open API docs](https://fide.thanh.se/docs/index.html)

## Examples

Get all players

```bash
curl 'fide.thanh.se/api/players'
```

Get top 10 players by standard rating with descending order

```bash
curl 'fide.thanh.se/api/players?sort_by=standard&order=desc&size=10'
```

Get all players sort by blizt rating and is active on page 5

```bash
curl 'fide.thanh.se/api/players?sort_by=blitz&order=desc&page=5'
```

## Development

### Prerequisites:

- Docker
- JDK 21 with [sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)

### Run

```bash
cp .env.example .env
docker-compose up -d
sbt backend/run
open curl "http://localhost:9669/docs" // maybe you need to wait a bit for syncing
```

### Test

```bash
sbt test
```

### Before summiting PR

```bash
sbt prepare
```

## TODO

[TODO](/TODO.md)
