import sbt.*
import smithy4s.codegen.BuildInfo.version as smithy4sVersion

object Dependencies {

  object V {
    val catsEffect = "3.7.0"
    val ciris      = "3.14.1"
    val decline    = "2.6.2"
    val fs2        = "3.13.0"
    val fs2Data    = "1.13.0"
    val http4s     = "0.23.34"
    val iron       = "3.3.0"
    val gatling    = "3.15.0"
  }

  def http4s(artifact: String) = "org.http4s" %% s"http4s-$artifact" % V.http4s

  def smithy4s(artifact: String) = "com.disneystreaming.smithy4s" %% s"smithy4s-$artifact" % smithy4sVersion

  val catsCore   = "org.typelevel" %% "cats-core"   % "2.13.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect
  val catsMtl    = "org.typelevel" %% "cats-mtl"    % "1.6.0"

  val fs2           = "co.fs2"    %% "fs2-core"             % V.fs2
  val fs2IO         = "co.fs2"    %% "fs2-io"               % V.fs2
  val fs2Compress   = "de.lhns"   %% "fs2-compress-zip4j"   % "2.3.2"
  val fs2DataCsv    = "org.gnieh" %% "fs2-data-csv"         % V.fs2Data
  val fs2DataCsvGen = "org.gnieh" %% "fs2-data-csv-generic" % V.fs2Data

  val declineCore       = "com.monovore" %% "decline"        % V.decline
  val declineCatsEffect = "com.monovore" %% "decline-effect" % V.decline

  val cirisCore  = "is.cir" %% "ciris"        % V.ciris
  val cirisHtt4s = "is.cir" %% "ciris-http4s" % V.ciris

  val iron      = "io.github.iltotore" %% "iron"       % V.iron
  val ironCiris = "io.github.iltotore" %% "iron-ciris" % V.iron

  val http4sServer      = http4s("ember-server")
  val http4sClient      = http4s("client")
  val http4sEmberClient = http4s("ember-client")

  val smithy4sCore          = smithy4s("core")
  val smithy4sHttp4s        = smithy4s("http4s")
  val smithy4sHttp4sSwagger = smithy4s("http4s-swagger")

  val skunk = "org.tpolecat" %% "skunk-core" % "1.1.0-RC1"
  val dumbo = "dev.rolang"   %% "dumbo"      % "0.9.0"

  val log4Cats = "org.typelevel" %% "log4cats-slf4j"  % "2.8.0"
  val logback  = "ch.qos.logback" % "logback-classic" % "1.5.32"

  val ducktape = "io.github.arainko" %% "ducktape" % "0.2.12"

  object chess {
    val version = "17.15.5"
    val org     = "com.github.lichess-org.scalachess"
    val core    = org %% "scalachess"        % version
    val rating  = org %% "scalachess-rating" % version
  }

  val gatlingTestFramework = "io.gatling"            % "gatling-test-framework"    % V.gatling % Test
  val gatlingHighCharts    = "io.gatling.highcharts" % "gatling-charts-highcharts" % V.gatling % Test

  val testContainers    = "com.dimafeng"         %% "testcontainers-scala-postgresql" % "0.44.1"     % Test
  val weaver            = "org.typelevel"        %% "weaver-cats"                     % "0.12.0"     % Test
  val weaverScalaCheck  = "org.typelevel"        %% "weaver-scalacheck"               % "0.12.0"     % Test
  val catsEffectTestKit = "org.typelevel"        %% "cats-effect-testkit"             % V.catsEffect % Test
  val scalacheck        = "org.scalacheck"       %% "scalacheck"                      % "1.17.0"     % Test
  val scalacheckFaker   = "io.github.etspaceman" %% "scalacheck-faker"                % "9.0.2"      % Test
}
