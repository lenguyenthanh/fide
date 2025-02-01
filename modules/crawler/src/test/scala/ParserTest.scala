package fide
package crawler
package test

import cats.effect.IO
import cats.syntax.all.*
import fide.domain.OtherTitle
import org.typelevel.log4cats.Logger
import weaver.*

object ParserTest extends SimpleIOSuite:

  given Logger[IO] = org.typelevel.log4cats.noop.NoOpLogger[IO]

  test("player with other titles"):
    parse(
      "10001492       Ojok, Patrick                                                UGA M             FI,LSI             1638  0   20 1932  0   20 1926  0   20 1974      "
    ).map(_.get.otherTitles)
      .map(x => expect(x == List(OtherTitle.FI, OtherTitle.LSI)))

  test("active flag"):
    val lines = List(
      "8605360        A La, Teng Hua                                               CHN F                                1949  0   40                           1993  wi  ",
      "25021044       Aagney L., Narasimhan                                        IND M                                1606  0   20 1565  0   20 1567  0   20 2000  i   ",
      "                                                                       ", // whitespaces only line
      "29976634       Aafrin, S F Aja                                              SRI F                                                                       2012  w   ",
      "1478800        Aagaard, Christian                                           DEN M                                                                       1999      "
    )
    lines
      .traverse(parse)
      .map(_.flatten.map(_.active))
      .map(x => expect(x == List(false, false, true, true)))

  private def parse(s: String) = Downloader.parse(s).map(_.map(_._1))
