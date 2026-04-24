package fide
package broadcast

import cats.effect.kernel.Async
import cats.effect.std.Random
import cats.mtl.Raise
import cats.syntax.all.*
import com.github.plokhotnyuk.jsoniter_scala.core.*
import fide.broadcast.LichessDtos.*
import fs2.Stream
import org.http4s.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

/** HTTP client for the Lichess broadcast API.
  *
  * Authenticated with a personal API token (design #49). Retries 5xx and connection
  * errors with exponential backoff + jitter (design #23 / #33). 429 surfaces as
  * `LichessError.RateLimited` immediately; 4xx non-429 are raised without retry.
  */
trait LichessClient[F[_]]:
  /** Stream official broadcasts.
    *
    * `GET /api/broadcast` — operation `broadcastsOfficial`.
    * Returns an NDJSON stream where each line is a `BroadcastWithRounds` (tour + all of
    * its rounds inline). Used by the backend's 5-min tick for discovery (design #12).
    *
    * Spec: https://lichess.org/api#tag/broadcasts/GET/api/broadcast
    */
  def listBroadcasts: Stream[F, BroadcastWithRounds]

  /** Fetch one broadcast round with its games.
    *
    * `GET /api/broadcast/{broadcastTournamentSlug}/{broadcastRoundSlug}/{broadcastRoundId}`
    * — operation `broadcastRoundGet`. Slugs may be `"-"` per the Lichess spec when the
    * caller only has the round ID. Returns the round metadata, its parent tour (including
    * `tour.info.fideTC` — our time-control signal), and the full games array with
    * per-side `fideId`, `rating`, `title`, plus `status` for the result.
    *
    * Spec:
    * https://lichess.org/api#tag/broadcasts/GET/api/broadcast/~1%7BbroadcastTournamentSlug%7D~1%7BbroadcastRoundSlug%7D~1%7BbroadcastRoundId%7D
    */
  def fetchRound(tourSlug: String, roundSlug: String, roundId: String): F[BroadcastRoundResponse]

  /** Fetch tour detail with all rounds inlined.
    *
    * `GET /api/broadcast/{broadcastTournamentId}` — operation `broadcastTourGet`.
    * Returns `BroadcastWithRounds` (identical shape to one line of `listBroadcasts`).
    * Used by the CLI's Phase 2 after Phase 1 discovery collected tour IDs from
    * `fetchTop` (design CLI §4).
    *
    * Spec: https://lichess.org/api#tag/broadcasts/GET/api/broadcast/~1%7BbroadcastTournamentId%7D
    */
  def fetchTour(tourId: String): F[BroadcastWithRounds]

  /** Paginated top broadcasts (active + past).
    *
    * `GET /api/broadcast/top?page=$page` — operation `broadcastsTop`.
    * Returns `{ active[], upcoming[](deprecated), past{currentPage, maxPerPage,
    * currentPageResults[], previousPage, nextPage} }`. Each item is a
    * `BroadcastWithLastRound` — only the most recent round per tour (Phase 2 re-fetches
    * the tour via `fetchTour` to get all rounds). `page` is 1-20; Lichess caps pagination
    * at page 20 and exposes active broadcasts only on page 1. Used by the CLI's Phase 1
    * for past-broadcast enumeration (design #51).
    *
    * Spec: https://lichess.org/api#tag/broadcasts/GET/api/broadcast/top
    */
  def fetchTop(page: Int): F[BroadcastTopResponse]

object LichessClient:

  def apply[F[_]: {Async, Random, Logger}](
      client: Client[F],
      config: LichessConfig
  )(using Raise[F, LichessError]): LichessClient[F] =
    new LichessClientImpl[F](client, config)

  private final class LichessClientImpl[F[_]: {Async, Random, Logger}](
      client: Client[F],
      config: LichessConfig
  )(using R: Raise[F, LichessError])
      extends LichessClient[F]:

    private val authHeader: Authorization =
      Authorization(Credentials.Token(AuthScheme.Bearer, config.apiToken))

    private def mkRequest(path: String): Request[F] =
      val uri = config.baseUri.withPath(Uri.Path.unsafeFromString(path))
      Request[F](method = Method.GET, uri = uri).putHeaders(authHeader)

    def listBroadcasts: Stream[F, BroadcastWithRounds] =
      // NDJSON: one `BroadcastWithRounds` per line. Retry is not applied to streaming endpoints —
      // the caller (syncer) handles stream-level failures at the tick boundary.
      client
        .stream(mkRequest("/api/broadcast"))
        .flatMap(resp => classifyResponse(resp, "/api/broadcast").fold(Stream.raiseError[F], identity))

    def fetchRound(tourSlug: String, roundSlug: String, roundId: String): F[BroadcastRoundResponse] =
      val path = s"/api/broadcast/$tourSlug/$roundSlug/$roundId"
      getJson[BroadcastRoundResponse](path)

    def fetchTour(tourId: String): F[BroadcastWithRounds] =
      getJson[BroadcastWithRounds](s"/api/broadcast/$tourId")

    def fetchTop(page: Int): F[BroadcastTopResponse] =
      getJson[BroadcastTopResponse](s"/api/broadcast/top?page=$page")

    // ============================================================
    // Helpers
    // ============================================================

    private def getJson[A: JsonValueCodec](path: String): F[A] =
      withRetry:
        client
          .run(mkRequest(path))
          .use: resp =>
            classifyStatus(resp.status, path) match
              case Left(err) =>
                resp.bodyText.compile.string.flatMap: body =>
                  err match
                    case LichessError.Http(s, _) => R.raise(LichessError.Http(s, body.take(500)))
                    case e                       => R.raise(e)
              case Right(_)  =>
                resp.body.compile.to(Array).flatMap: bytes =>
                  Async[F]
                    .delay(readFromArray[A](bytes))
                    .handleErrorWith: e =>
                      R.raise(LichessError.DecodeFailure(path, e.getMessage))

    private def classifyStatus(status: Status, path: String): Either[LichessError, Unit] =
      val code = status.code
      if code >= 200 && code < 300 then Right(())
      else if code == 401 || code == 403 then Left(LichessError.Unauthorized)
      else if code == 404 then Left(LichessError.NotFound(path))
      else if code == 429 then Left(LichessError.RateLimited(60.seconds))
      else Left(LichessError.Http(code, ""))

    /** For the NDJSON streaming endpoint: classify status, and on success decode each line. */
    private def classifyResponse(
        resp: Response[F],
        path: String
    ): Either[Throwable, Stream[F, BroadcastWithRounds]] =
      classifyStatus(resp.status, path) match
        case Left(err) => Left(LichessErrorException(err))
        case Right(_)  =>
          Right:
            resp.body
              .through(fs2.text.utf8.decode)
              .through(fs2.text.lines)
              .filter(_.nonEmpty)
              .evalMap: line =>
                Async[F]
                  .delay(readFromString[BroadcastWithRounds](line))
                  .handleErrorWith: e =>
                    R.raise(LichessError.DecodeFailure(path, e.getMessage))

    /** Wrap `fa` with exponential-backoff retry. Retries only on transient Lichess/network
      * errors: 5xx (mapped to `LichessError.Http` with status >= 500) and connection errors
      * (surfaced as non-`LichessError` Throwables by http4s).
      *
      * Does NOT retry: 429 (caller rescues at tick/CLI boundary), 4xx non-429 (deterministic).
      */
    private def withRetry[A](fa: F[A]): F[A] =
      RetryHelper.retryOn[F, A](
        maxRetries = config.retryMaxAttempts,
        baseDelay = config.retryBaseDelay,
        logRetries = config.retryLoggingEnabled
      )(isRetryable)(fa)

    private def isRetryable(err: Throwable): Boolean = err match
      case LichessErrorException(LichessError.Http(s, _)) if s >= 500 => true
      case LichessErrorException(_)                                   => false
      case _                                                          => true // connection / timeout

  /** Exception adapter used so the hand-written retry helper (which is `Throwable`-based)
    * can see typed Lichess errors and decide whether to retry.
    *
    * `Raise[IO, LichessError]` is wired via `ioRaise` below: `.raise` routes through
    * `IO.raiseError(LichessErrorException(e))`.
    */
  final case class LichessErrorException(err: LichessError)
      extends RuntimeException(s"Lichess error: $err", null, true, false)

  /** `Raise[IO, LichessError]` instance — use by `import LichessClient.given` or
    * `given Raise[IO, LichessError] = LichessClient.ioRaise`.
    */
  given ioRaise: cats.mtl.Raise[cats.effect.IO, LichessError] =
    new cats.mtl.Raise[cats.effect.IO, LichessError]:
      def functor                                        = cats.effect.IO.asyncForIO
      def raise[E2 <: LichessError, A](e: E2): cats.effect.IO[A] =
        cats.effect.IO.raiseError(LichessErrorException(e))
