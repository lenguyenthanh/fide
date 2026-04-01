package fide
package crawler
package test

import cats.effect.IO
import fide.crawler.Syncer.Status
import weaver.*

object SyncerTest extends SimpleIOSuite:

  test("Status: same local and remote is UpToDate"):
    IO(expect(Status(Some("a"), Some("a")) == Status.UpToDate))

  test("Status: different local and remote is OutDated"):
    IO:
      val status = Status(Some("a"), Some("b"))
      expect(status == Status.OutDated(Some("b")))

  test("Status: no local, some remote is OutDated"):
    IO:
      val status = Status(None, Some("b"))
      expect(status == Status.OutDated(Some("b")))

  test("Status: no local, no remote is OutDated"):
    IO:
      val status = Status(None, None)
      expect(status == Status.OutDated(None))
