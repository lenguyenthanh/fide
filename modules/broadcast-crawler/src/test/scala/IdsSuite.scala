package fide
package broadcast
package test

import fide.broadcast.Ids.*
import weaver.*

object IdsSuite extends SimpleIOSuite:

  // ============================================================
  // BroadcastTourId
  // ============================================================

  pureTest("BroadcastTourId accepts 8-char alphanumeric"):
    expect(BroadcastTourId.option("abc12345").isDefined) and
      expect(BroadcastTourId.option("ABCDEFGH").isDefined) and
      expect(BroadcastTourId.option("12345678").isDefined)

  pureTest("BroadcastTourId rejects wrong length"):
    expect(BroadcastTourId.option("").isEmpty) and
      expect(BroadcastTourId.option("short").isEmpty) and
      expect(BroadcastTourId.option("abcdefghi").isEmpty) // 9 chars

  pureTest("BroadcastTourId rejects non-alphanumeric"):
    expect(BroadcastTourId.option("abc-1234").isEmpty) and
      expect(BroadcastTourId.option("abc 1234").isEmpty) and
      expect(BroadcastTourId.option("abc.1234").isEmpty)

  // ============================================================
  // BroadcastRoundId / BroadcastGameId — same constraint.
  // ============================================================

  pureTest("BroadcastRoundId accepts 8-char alphanumeric"):
    expect(BroadcastRoundId.option("xyz99999").isDefined)

  pureTest("BroadcastGameId accepts 8-char alphanumeric"):
    expect(BroadcastGameId.option("aBc12345").isDefined)

  pureTest("All three ID types share the same constraint"):
    // Same input should yield the same accept/reject decision for all three.
    val good = "abc12345"
    val bad  = "not valid"
    expect(BroadcastTourId.option(good).isDefined == BroadcastRoundId.option(good).isDefined) and
      expect(BroadcastTourId.option(good).isDefined == BroadcastGameId.option(good).isDefined) and
      expect(BroadcastTourId.option(bad).isEmpty == BroadcastRoundId.option(bad).isEmpty) and
      expect(BroadcastTourId.option(bad).isEmpty == BroadcastGameId.option(bad).isEmpty)
