package fide
package crawler

import org.http4s.Uri

case class CrawlerConfig(
    chunkSize: Int,        // number of players to process in a single chunk
    concurrentUpsert: Int, // number of concurrent upserts
    fidePlayerDownloadUri: Uri
)
