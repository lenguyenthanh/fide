package fide
package broadcast
package test

import fide.domain.Models.GameResult
import fide.domain.Models.TimeControl
import weaver.*

object EloServiceSuite extends SimpleIOSuite:

  // ============================================================
  // Golden cases: equal-rated players.
  // ============================================================

  pureTest("equal-rated draw with K=10 → zero diff for both sides"):
    val (w, b) = EloService.computeDiffs(TimeControl.Standard, 2700, 10, 2700, 10, GameResult.Draw)
    expect(w == 0) and expect(b == 0)

  pureTest("equal-rated white wins with K=10 → +5/-5"):
    val (w, b) = EloService.computeDiffs(TimeControl.Standard, 2700, 10, 2700, 10, GameResult.WhiteWin)
    // FIDE: K=10 * (1.0 - 0.5) = 5, round to 5.
    expect(w == 5) and expect(b == -5)

  pureTest("equal-rated black wins with K=10 → -5/+5"):
    val (w, b) = EloService.computeDiffs(TimeControl.Standard, 2700, 10, 2700, 10, GameResult.BlackWin)
    expect(w == -5) and expect(b == 5)

  // ============================================================
  // Symmetry: swapping sides + result flips signs.
  // ============================================================

  pureTest("swapping sides with same result flips the signs"):
    val (aW, aB) = EloService.computeDiffs(TimeControl.Standard, 2600, 20, 2700, 10, GameResult.WhiteWin)
    val (bW, bB) = EloService.computeDiffs(TimeControl.Standard, 2700, 10, 2600, 20, GameResult.BlackWin)
    // Same game from each colour's POV: the previously-white (2600) winning
    // vs the previously-black (2600) winning produces matching diffs.
    expect(aW == bB) and expect(aB == bW)

  pureTest("bigger rating gap → smaller diff for the higher-rated winner"):
    val (hiWins, _)   = EloService.computeDiffs(TimeControl.Standard, 2800, 10, 2200, 20, GameResult.WhiteWin)
    val (equalWins, _) = EloService.computeDiffs(TimeControl.Standard, 2500, 10, 2500, 20, GameResult.WhiteWin)
    expect(hiWins < equalWins)

  // ============================================================
  // K-factor scaling.
  // ============================================================

  pureTest("doubling K doubles the magnitude of the diff"):
    val (w1, b1) = EloService.computeDiffs(TimeControl.Standard, 2400, 10, 2400, 10, GameResult.WhiteWin)
    val (w2, b2) = EloService.computeDiffs(TimeControl.Standard, 2400, 20, 2400, 20, GameResult.WhiteWin)
    expect(math.abs(w2) == math.abs(w1) * 2) and expect(math.abs(b2) == math.abs(b1) * 2)

  pureTest("DefaultKFactor is 40 (matches scalachess KFactor.default)"):
    expect(EloService.DefaultKFactor == 40)

  // ============================================================
  // Time control passthrough — rapid/blitz use the same K=40 rule set.
  // ============================================================

  pureTest("rapid time control: K=40 standard-draw diff is zero"):
    val (w, b) = EloService.computeDiffs(TimeControl.Rapid, 2400, 40, 2400, 40, GameResult.Draw)
    expect(w == 0) and expect(b == 0)

  pureTest("blitz time control: K=40 equal-rated white wins"):
    val (w, b) = EloService.computeDiffs(TimeControl.Blitz, 2400, 40, 2400, 40, GameResult.WhiteWin)
    expect(w == 20) and expect(b == -20)
