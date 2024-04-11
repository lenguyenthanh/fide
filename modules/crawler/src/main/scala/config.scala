package fide
package crawler

case class CrawlerConfig(
    chunkSize: Int,       // number of players to process in a single chunk
    concurrentUpsert: Int // number of concurrent upserts
)
