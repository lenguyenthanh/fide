package fide
package broadcast
package test

import com.github.plokhotnyuk.jsoniter_scala.core.*
import fide.broadcast.LichessDtos.*
import weaver.*

import scala.io.Source
import scala.util.Using

object LichessDecodersSuite extends SimpleIOSuite:

  private def loadResource(path: String): String =
    Using.resource(Source.fromResource(path))(_.mkString)

  pureTest("decode BroadcastWithRounds (from /api/broadcast NDJSON line)"):
    val body = loadResource("lichess/broadcast_with_rounds.json")
    val parsed = readFromString[BroadcastWithRounds](body)
    expect(parsed.tour.id == "abc12345") and
      expect(parsed.tour.slug == "sinquefield-cup-2026") and
      expect(parsed.tour.info.flatMap(_.fideTC).contains("standard")) and
      expect(parsed.tour.dates.map(_.size).contains(2)) and
      expect(parsed.rounds.size == 2) and
      expect(parsed.rounds.head.id == "rnd11111") and
      expect(parsed.rounds.head.rated) and
      expect(parsed.rounds.head.finished.contains(true)) and
      expect(parsed.rounds.head.finishedAt.contains(1713993600000L)) and
      expect(parsed.rounds(1).finished.contains(false))

  pureTest("decode BroadcastRoundResponse (from /api/broadcast/{tour}/{round}/{id})"):
    val body = loadResource("lichess/broadcast_round_response.json")
    val parsed = readFromString[BroadcastRoundResponse](body)
    expect(parsed.round.id == "rnd11111") and
      expect(parsed.tour.info.flatMap(_.fideTC).contains("standard")) and
      expect(parsed.games.size == 2) and
      expect(parsed.games.head.id == "game0001") and
      expect(parsed.games.head.status.contains("1-0")) and
      expect(parsed.games.head.players.exists(_.size == 2)) and {
        val white = parsed.games.head.players.get.head
        expect(white.rating.contains(2830)) and
          expect(white.fideId.contains(1503014)) and
          expect(white.title.contains("GM"))
      } and
      expect(parsed.games(1).status.contains("1/2-1/2"))

  pureTest("decode BroadcastTopResponse (from /api/broadcast/top)"):
    val body = loadResource("lichess/broadcast_top.json")
    val parsed = readFromString[BroadcastTopResponse](body)
    expect(parsed.active.size == 1) and
      expect(parsed.active.head.tour.id == "act00001") and
      expect(parsed.past.currentPage == 1) and
      expect(parsed.past.nextPage.contains(2)) and
      expect(parsed.past.previousPage.isEmpty) and
      expect(parsed.past.currentPageResults.size == 1) and
      expect(parsed.past.currentPageResults.head.round.finished.contains(true))

  pureTest("decoder tolerates unexpected fields (withSkipUnexpectedFields)"):
    val body = """{"tour":{"id":"abc12345","slug":"x","unknownField":"ignored"},"rounds":[]}"""
    val parsed = readFromString[BroadcastWithRounds](body)
    expect(parsed.tour.id == "abc12345") and expect(parsed.rounds.isEmpty)
