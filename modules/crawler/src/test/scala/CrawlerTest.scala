package fide
package crawler
package test

import fide.domain.OtherTitle
import weaver.*

object CrawlerTest extends FunSuite:

  test("create player success"):
    val x = Downloader
      .parse:
        "10001492       Ojok, Patrick                                                UGA M             FI,LSI             1638  0   20 1932  0   20 1926  0   20 1974      "
      .get
      ._1
    expect(x.otherTitles == List(OtherTitle.FI, OtherTitle.LSI))
