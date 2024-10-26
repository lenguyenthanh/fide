import sbt.*

object Dependencies {

  object V {
    val catsEffect = "3.5.4"
    val ciris      = "3.6.0"
    val flyway     = "10.18.0"
    val fs2        = "3.11.0"
    val http4s     = "0.23.29"
    val iron       = "2.6.0"
    val gatling    = "3.12.0"
  }

  def http4s(artifact: String) = "org.http4s" %% s"http4s-$artifact" % V.http4s

  val catsCore   = "org.typelevel" %% "cats-core"   % "2.11.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect

  val fs2         = "co.fs2"  %% "fs2-core"           % V.fs2
  val fs2IO       = "co.fs2"  %% "fs2-io"             % V.fs2
  val fs2Compress = "de.lhns" %% "fs2-compress-zip4j" % "2.0.0"

  val cirisCore  = "is.cir"             %% "ciris"        % V.ciris
  val cirisHtt4s = "is.cir"             %% "ciris-http4s" % V.ciris
  val iron       = "io.github.iltotore" %% "iron"         % V.iron
  val ironCiris  = "io.github.iltotore" %% "iron-ciris"   % V.iron

  val http4sServer      = http4s("ember-server")
  val http4sClient      = http4s("client")
  val http4sEmberClient = http4s("ember-client")

  val skunk          = "org.tpolecat"       %% "skunk-core"                 % "1.0.0-M7"
  val flyway4s       = "com.github.geirolz" %% "fly4s"                      % "1.0.8"
  val flyway         = "org.flywaydb"        % "flyway-core"                % V.flyway
  val flywayPostgres = "org.flywaydb"        % "flyway-database-postgresql" % V.flyway
  val postgres       = "org.postgresql"      % "postgresql"                 % "42.7.4"

  val log4Cats = "org.typelevel" %% "log4cats-slf4j"  % "2.7.0"
  val logback  = "ch.qos.logback" % "logback-classic" % "1.5.12"

  val ducktape = "io.github.arainko" %% "ducktape" % "0.2.6"

  val gatlingTestFramework = "io.gatling"            % "gatling-test-framework"    % V.gatling % Test
  val gatlingHighCharts    = "io.gatling.highcharts" % "gatling-charts-highcharts" % V.gatling % Test

  val testContainers    = "com.dimafeng"        %% "testcontainers-scala-postgresql" % "0.41.4"     % Test
  val weaver            = "com.disneystreaming" %% "weaver-cats"                     % "0.8.4"      % Test
  val weaverScalaCheck  = "com.disneystreaming" %% "weaver-scalacheck"               % "0.8.4"      % Test
  val catsEffectTestKit = "org.typelevel"       %% "cats-effect-testkit"             % V.catsEffect % Test
  val munit             = "org.scalameta"       %% "munit"                           % "1.0.0-M11"  % Test
  val munitScalaCheck   = "org.scalameta"       %% "munit-scalacheck"                % "1.0.0-M11"  % Test
  val scalacheck        = "org.scalacheck"      %% "scalacheck"                      % "1.17.0"     % Test
}
