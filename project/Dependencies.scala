import sbt.*
import smithy4s.codegen.BuildInfo.version as smithy4sVersion

object Dependencies {

  object V {
    val catsEffect = "3.6.3"
    val ciris      = "3.10.0"
    val flyway     = "11.10.5"
    val fs2        = "3.12.2"
    val http4s     = "0.23.30"
    val iron       = "3.2.0"
    val gatling    = "3.14.3"
  }

  def http4s(artifact: String) = "org.http4s" %% s"http4s-$artifact" % V.http4s

  def smithy4s(artifact: String) = "com.disneystreaming.smithy4s" %% s"smithy4s-$artifact" % smithy4sVersion

  val catsCore   = "org.typelevel" %% "cats-core"   % "2.13.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect

  val fs2         = "co.fs2"  %% "fs2-core"           % V.fs2
  val fs2IO       = "co.fs2"  %% "fs2-io"             % V.fs2
  val fs2Compress = "de.lhns" %% "fs2-compress-zip4j" % "2.3.2"

  val cirisCore  = "is.cir" %% "ciris"        % V.ciris
  val cirisHtt4s = "is.cir" %% "ciris-http4s" % V.ciris

  val iron      = "io.github.iltotore" %% "iron"       % V.iron
  val ironCiris = "io.github.iltotore" %% "iron-ciris" % V.iron
  val ironSkunk = "io.github.iltotore" %% "iron-skunk" % V.iron

  val http4sServer      = http4s("ember-server")
  val http4sClient      = http4s("client")
  val http4sEmberClient = http4s("ember-client")

  val smithy4sCore          = smithy4s("core")
  val smithy4sHttp4s        = smithy4s("http4s")
  val smithy4sHttp4sSwagger = smithy4s("http4s-swagger")

  val skunk          = "org.tpolecat"       %% "skunk-core"                 % "1.0.0-M10"
  val flyway4s       = "com.github.geirolz" %% "fly4s"                      % "1.0.8"
  val flyway         = "org.flywaydb"        % "flyway-core"                % V.flyway
  val flywayPostgres = "org.flywaydb"        % "flyway-database-postgresql" % V.flyway
  val postgres       = "org.postgresql"      % "postgresql"                 % "42.7.7"

  val log4Cats = "org.typelevel" %% "log4cats-slf4j"  % "2.7.1"
  val logback  = "ch.qos.logback" % "logback-classic" % "1.5.18"

  val ducktape = "io.github.arainko" %% "ducktape" % "0.2.10"

  val gatlingTestFramework = "io.gatling"            % "gatling-test-framework"    % V.gatling % Test
  val gatlingHighCharts    = "io.gatling.highcharts" % "gatling-charts-highcharts" % V.gatling % Test

  val testContainers    = "com.dimafeng"         %% "testcontainers-scala-postgresql" % "0.43.0"     % Test
  val weaver            = "org.typelevel"        %% "weaver-cats"                     % "0.9.3"      % Test
  val weaverScalaCheck  = "org.typelevel"        %% "weaver-scalacheck"               % "0.9.3"      % Test
  val catsEffectTestKit = "org.typelevel"        %% "cats-effect-testkit"             % V.catsEffect % Test
  val scalacheck        = "org.scalacheck"       %% "scalacheck"                      % "1.17.0"     % Test
  val scalacheckFaker   = "io.github.etspaceman" %% "scalacheck-faker"                % "9.0.0"      % Test
}
