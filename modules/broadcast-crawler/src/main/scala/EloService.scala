package fide
package broadcast

import chess.FideTC
import chess.Outcome
import chess.rating.Elo
import chess.rating.KFactor
import fide.domain.Models.GameResult
import fide.domain.Models.TimeControl

/** Pure wrapper around scalachess's Elo math.
  *
  * Delegates to `chess.rating.Elo.computeRatingDiff` — bit-identical to Lichess/FIDE's
  * computation (see design decision #14). No local Elo implementation.
  */
object EloService:

  /** Fallback K-factor for unresolved players. Matches scalachess's `KFactor.default`. */
  val DefaultKFactor: Int = KFactor.default.value

  /** Compute `(whiteDiff, blackDiff)` for a single game.
    *
    * @param tc           Time control of the enclosing tour (see decision #48).
    * @param whiteRating  White player's pre-game rating (from our `players.{tc}` column).
    * @param whiteK       White K-factor (from our `players.{tc}_kfactor` column, or `DefaultKFactor`).
    * @param blackRating  Black's pre-game rating.
    * @param blackK       Black's K-factor.
    * @param result       Decisive result (unfinished filtered at ingest per decision #37).
    */
  def computeDiffs(
      tc: TimeControl,
      whiteRating: Int,
      whiteK: Int,
      blackRating: Int,
      blackK: Int,
      result: GameResult
  ): (Int, Int) =
    val fideTc       = toFideTc(tc)
    val whitePts     = pointsFor(result, white = true)
    val blackPts     = pointsFor(result, white = false)
    val whitePlayer  = Elo.Player(Elo(whiteRating), KFactor(whiteK))
    val blackPlayer  = Elo.Player(Elo(blackRating), KFactor(blackK))
    val whiteDiff    = Elo.computeRatingDiff(fideTc)(whitePlayer, Seq(Elo.Game(whitePts, Elo(blackRating))))
    val blackDiff    = Elo.computeRatingDiff(fideTc)(blackPlayer, Seq(Elo.Game(blackPts, Elo(whiteRating))))
    (whiteDiff.value, blackDiff.value)

  private def toFideTc(tc: TimeControl): FideTC = tc match
    case TimeControl.Standard => FideTC.standard
    case TimeControl.Rapid    => FideTC.rapid
    case TimeControl.Blitz    => FideTC.blitz

  private def pointsFor(result: GameResult, white: Boolean): Outcome.Points = result match
    case GameResult.WhiteWin => if white then Outcome.Points.One else Outcome.Points.Zero
    case GameResult.BlackWin => if white then Outcome.Points.Zero else Outcome.Points.One
    case GameResult.Draw     => Outcome.Points.Half
