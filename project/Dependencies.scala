import sbt.*
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import smithy4s.codegen.BuildInfo.version as smithy4sVersion

object Dependencies {

  object V {
    val catsEffect    = "3.7.0"
    val ciris         = "3.15.0"
    val decline       = "2.6.2"
    val fs2           = "3.13.0"
    val fs2Data       = "1.14.0"
    val http4s        = "0.23.34"
    val iron          = "3.3.0"
    val gatling       = "3.15.1"
    val scalaJavaTime = "2.7.0"
  }

  // Cross-platform helpers (%%% picks the right JVM/JS/Native artifact). These are
  // `Def.setting`s so they must be used as `dep.value` inside (cross)project settings.
  def http4s(artifact: String) =
    Def.setting("org.http4s" %%% s"http4s-$artifact" % V.http4s)

  def smithy4s(artifact: String) =
    Def.setting("com.disneystreaming.smithy4s" %%% s"smithy4s-$artifact" % smithy4sVersion)

  val catsCore   = Def.setting("org.typelevel" %%% "cats-core" % "2.13.0")
  val catsEffect = Def.setting("org.typelevel" %%% "cats-effect" % V.catsEffect)

  val fs2           = Def.setting("co.fs2" %%% "fs2-core" % V.fs2)
  val fs2IO         = Def.setting("co.fs2" %%% "fs2-io" % V.fs2)
  val fs2DataCsv    = Def.setting("org.gnieh" %%% "fs2-data-csv" % V.fs2Data)
  val fs2DataCsvGen = Def.setting("org.gnieh" %%% "fs2-data-csv-generic" % V.fs2Data)

  // zip4j is JVM-only; crawler stays on the JVM
  val fs2Compress = "de.lhns" %% "fs2-compress-zip4j" % "2.3.2"

  val declineCore       = Def.setting("com.monovore" %%% "decline" % V.decline)
  val declineCatsEffect = Def.setting("com.monovore" %%% "decline-effect" % V.decline)

  val cirisCore  = Def.setting("is.cir" %%% "ciris" % V.ciris)
  val cirisHtt4s = Def.setting("is.cir" %%% "ciris-http4s" % V.ciris)

  val iron      = Def.setting("io.github.iltotore" %%% "iron" % V.iron)
  val ironCiris = Def.setting("io.github.iltotore" %%% "iron-ciris" % V.iron)

  val http4sServer      = http4s("ember-server")
  val http4sClient      = http4s("client")
  val http4sEmberClient = http4s("ember-client")

  val smithy4sCore   = smithy4s("core")
  val smithy4sHttp4s = smithy4s("http4s")
  // Swagger UI integration is JVM-only (backend)
  val smithy4sHttp4sSwagger = "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion

  val skunk    = Def.setting("org.tpolecat" %%% "skunk-core" % "1.1.0-RC1")
  val dumbo    = Def.setting("dev.rolang" %%% "dumbo" % "0.10.1")
  val ducktape = Def.setting("io.github.arainko" %%% "ducktape" % "0.2.13")

  // java.time implementation for Scala.js / Scala Native (provided by the JDK on the JVM)
  val scalaJavaTime = Def.setting("io.github.cquiroz" %%% "scala-java-time" % V.scalaJavaTime)

  // Logging: the cross-platform `log4cats-core` API is used everywhere; the slf4j
  // binding + logback are JVM-only. The native CLI ships a minimal console logger.
  val log4Cats     = "org.typelevel" %% "log4cats-slf4j"  % "2.8.0"
  val log4CatsCore = Def.setting("org.typelevel" %%% "log4cats-core" % "2.8.0")
  val log4CatsNoop = Def.setting("org.typelevel" %%% "log4cats-noop" % "2.8.0" % Test)
  val logback      = "ch.qos.logback" % "logback-classic" % "1.5.34"

  val gatlingTestFramework = "io.gatling"            % "gatling-test-framework"    % V.gatling % Test
  val gatlingHighCharts    = "io.gatling.highcharts" % "gatling-charts-highcharts" % V.gatling % Test

  // testcontainers drives Docker via the JVM — JVM test scope only
  val testContainers    = "com.dimafeng"         %% "testcontainers-scala-postgresql" % "0.44.1" % Test
  val weaver            = Def.setting("org.typelevel" %%% "weaver-cats" % "0.13.0" % Test)
  val weaverScalaCheck  = Def.setting("org.typelevel" %%% "weaver-scalacheck" % "0.13.0" % Test)
  val catsEffectTestKit = Def.setting("org.typelevel" %%% "cats-effect-testkit" % V.catsEffect % Test)
  val scalacheck        = Def.setting("org.scalacheck" %%% "scalacheck" % "1.17.0" % Test)
  val scalacheckFaker   = "io.github.etspaceman" %% "scalacheck-faker"                % "9.0.2"  % Test
}
