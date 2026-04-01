package fide
package cli
package test

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.comcast.ip4s.*
import fide.db.test.Containers
import fide.db.{ Db, HistoryDb, PostgresConfig }
import fide.domain.Models.*
import fide.types.*
import io.github.iltotore.iron.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger
import weaver.*

import java.nio.file.{ Files as JFiles, Path }

object HistoryIngestorSuite extends SimpleIOSuite:

  given Logger[IO] = NoOpLogger[IO]

  private val defaultPage = Pagination(PageNumber(1), PageSize(100))

  // Dummy postgres config — never used since HistoryIngestor receives db/historyDb directly
  private val dummyPg = PostgresConfig(ip"0.0.0.0", port"5432", "", "", "", 1, "fide", false, false)

  private def mkConfig(
      dir: Path,
      startMonth: Option[YearMonth] = none,
      endMonth: Option[YearMonth] = none
  ): IngestConfig =
    IngestConfig(dir, startMonth, endMonth, dummyPg, 100)

  private def resourceWithFeds: Resource[IO, (HistoryDb, Db)] =
    Containers.createResource.map(x => (HistoryDb(x.postgres, 100), Db(x.postgres)))

  private def writeCsv(dir: java.nio.file.Path, filename: String, content: String): IO[Unit] =
    IO:
      JFiles.writeString(dir.resolve(filename), content)
      ()

  private val csvHeader =
    "id,name,title,womenTitle,otherTitles,standard,standardK,rapid,rapidK,blitz,blitzK,gender,birthYear,active,federationId"

  private def playerRow(
      id: Int,
      name: String,
      fedId: String = "USA",
      standard: Int = 2700,
      active: Boolean = true
  ) =
    s"$id,$name,GM,,,${standard},40,2600,40,2500,40,M,1990,$active,$fedId"

  test("discovers only yyyy-MM.csv files, ignores others"):
    resourceWithFeds.use: (historyDb, db) =>
      IO(JFiles.createTempDirectory("fide-test")).flatMap: tmpDir =>
        for
          _ <- writeCsv(tmpDir, "2024-01.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          _ <- writeCsv(tmpDir, "notes.csv", s"$csvHeader\n${playerRow(2, "Bob")}")
          _ <- writeCsv(tmpDir, "backup.csv", s"$csvHeader\n${playerRow(3, "Charlie")}")
          config   = mkConfig(tmpDir)
          ingestor = HistoryIngestor(historyDb, db, config)
          _      <- ingestor.ingest
          months <- historyDb.availableMonths
        yield expect(months.size == 1) and
          expect(months.head == YearMonth(2024, 1))

  test("startMonth filter only ingests files >= startMonth"):
    resourceWithFeds.use: (historyDb, db) =>
      IO(JFiles.createTempDirectory("fide-test")).flatMap: tmpDir =>
        for
          _ <- writeCsv(tmpDir, "2024-01.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          _ <- writeCsv(tmpDir, "2024-02.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          _ <- writeCsv(tmpDir, "2024-03.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          config   = mkConfig(tmpDir, startMonth = YearMonth(2024, 2).some)
          ingestor = HistoryIngestor(historyDb, db, config)
          _      <- ingestor.ingest
          months <- historyDb.availableMonths
        yield expect(months.size == 2) and
          expect(!months.contains(YearMonth(2024, 1)))

  test("endMonth filter only ingests files <= endMonth"):
    resourceWithFeds.use: (historyDb, db) =>
      IO(JFiles.createTempDirectory("fide-test")).flatMap: tmpDir =>
        for
          _ <- writeCsv(tmpDir, "2024-01.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          _ <- writeCsv(tmpDir, "2024-02.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          _ <- writeCsv(tmpDir, "2024-03.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          config   = mkConfig(tmpDir, endMonth = YearMonth(2024, 2).some)
          ingestor = HistoryIngestor(historyDb, db, config)
          _      <- ingestor.ingest
          months <- historyDb.availableMonths
        yield expect(months.size == 2) and
          expect(!months.contains(YearMonth(2024, 3)))

  test("full pipeline: CSV to queryable history"):
    resourceWithFeds.use: (historyDb, db) =>
      IO(JFiles.createTempDirectory("fide-test")).flatMap: tmpDir =>
        val jan2024 = YearMonth(2024, 1)
        val feb2024 = YearMonth(2024, 2)
        for
          _ <- writeCsv(
            tmpDir,
            "2024-01.csv",
            s"$csvHeader\n${playerRow(1, "Alice")}\n${playerRow(2, "Bob")}"
          )
          _ <- writeCsv(tmpDir, "2024-02.csv", s"$csvHeader\n${playerRow(1, "Alice", standard = 2750)}")
          config   = mkConfig(tmpDir)
          ingestor = HistoryIngestor(historyDb, db, config)
          _ <- ingestor.ingest
          // Verify available months
          months <- historyDb.availableMonths
          // Verify player_info populated
          janPlayers <- historyDb.allPlayers(
            jan2024,
            Sorting(SortBy.Name, Order.Asc),
            defaultPage,
            PlayerFilter.default
          )
          // Verify per-month snapshots
          febPlayers <- historyDb.allPlayers(
            feb2024,
            Sorting(SortBy.Name, Order.Asc),
            defaultPage,
            PlayerFilter.default
          )
          // Verify playerById
          alice <- historyDb.playerById(PlayerId(1), feb2024)
          // Verify federation populated
          feds <- db.allFederations
        yield expect(months.size == 2) and
          expect(janPlayers.size == 2) and
          expect(janPlayers.head.name == "Alice") and
          expect(febPlayers.size == 1) and
          expect(alice.isDefined) and
          expect(alice.get.standard.contains(Rating(2750))) and
          expect(feds.exists(_.id == FederationId("USA")))

  test("re-running ingest is idempotent"):
    resourceWithFeds.use: (historyDb, db) =>
      IO(JFiles.createTempDirectory("fide-test")).flatMap: tmpDir =>
        val jan2024 = YearMonth(2024, 1)
        for
          _ <- writeCsv(tmpDir, "2024-01.csv", s"$csvHeader\n${playerRow(1, "Alice")}")
          config   = mkConfig(tmpDir)
          ingestor = HistoryIngestor(historyDb, db, config)
          _       <- ingestor.ingest
          _       <- ingestor.ingest // second run
          players <- historyDb.allPlayers(
            jan2024,
            Sorting(SortBy.Name, Order.Asc),
            defaultPage,
            PlayerFilter.default
          )
        yield expect(players.size == 1) // no duplicates
