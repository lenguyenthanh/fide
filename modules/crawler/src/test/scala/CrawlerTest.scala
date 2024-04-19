package fide
package crawler
package test

import cats.syntax.all.*
import fide.domain.{ NewPlayer, OtherTitle }
import weaver.*

object CrawlerTest extends FunSuite:

  test("player with other titles"):
    val x =
      parse:
        "10001492       Ojok, Patrick                                                UGA M             FI,LSI             1638  0   20 1932  0   20 1926  0   20 1974      "
      .get
    expect(x.otherTitles == List(OtherTitle.FI, OtherTitle.LSI))

  test("active flag"):
    val lines = List(
      "8605360        A La, Teng Hua                                               CHN F                                1949  0   40                           1993  wi  ",
      "25021044       Aagney L., Narasimhan                                        IND M                                1606  0   20 1565  0   20 1567  0   20 2000  i   ",
      "29976634       Aafrin, S F Aja                                              SRI F                                                                       2012  w   ",
      "1478800        Aagaard, Christian                                           DEN M                                                                       1999      "
    )
    val actives = lines.traverse(parse).get.map(_.active)
    expect(actives == List(false, false, false, true))

  private def parse(s: String): Option[NewPlayer] = Downloader.parse(s).map(_._1)
