package fide
package crawler
package test

import fide.domain.OtherTitle
import weaver.*

object CrawlerTest extends FunSuite:

  test("player with other titles"):
    val x = Downloader
      .parse:
        "10001492       Ojok, Patrick                                                UGA M             FI,LSI             1638  0   20 1932  0   20 1926  0   20 1974      "
      .get
      ._1
    expect(x.otherTitles == List(OtherTitle.FI, OtherTitle.LSI))

  test("inactive player"):
    val x = Downloader
      .parse:
        "8605360        A La, Teng Hua                                               CHN F                                1949  0   40                           1993  wi  "
      .get
      ._1
    expect(!x.active)
