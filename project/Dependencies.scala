import sbt.*

object Dependencies {

  object V {
    val http4s     = "0.23.26"
    val ciris      = "3.5.0"
    val iron       = "2.5.0"
    val catsEffect = "3.5.4"
    val fs2        = "3.10.2"
  }

  def http4s(artifact: String) = "org.http4s" %% s"http4s-$artifact" % V.http4s

  val catsCore   = "org.typelevel" %% "cats-core"   % "2.10.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % V.catsEffect

  val fs2   = "co.fs2" %% "fs2-core" % V.fs2
  val fs2IO = "co.fs2" %% "fs2-io"   % V.fs2

  val cirisCore  = "is.cir"             %% "ciris"        % V.ciris
  val cirisHtt4s = "is.cir"             %% "ciris-http4s" % V.ciris
  val iron       = "io.github.iltotore" %% "iron"         % V.iron
  val ironCiris  = "io.github.iltotore" %% "iron-ciris"   % V.iron

  val http4sServer = http4s("ember-server")
  val http4sClient = http4s("ember-client") % Test

  val skunk          = "org.tpolecat"       %% "skunk-core"                 % "0.6.3"
  val flyway4s       = "com.github.geirolz" %% "fly4s"                      % "1.0.1"
  val flyway         = "org.flywaydb"        % "flyway-core"                % "10.11.0"
  val flywayPostgres = "org.flywaydb"        % "flyway-database-postgresql" % "10.11.0"
  val postgres       = "org.postgresql"      % "postgresql"                 % "42.7.3"

  val otel     = "org.typelevel" %% "otel4s-oteljava" % "0.5.0"
  val log4Cats = "org.typelevel" %% "log4cats-slf4j"  % "2.6.0"
  val logback  = "ch.qos.logback" % "logback-classic" % "1.5.3"

  val log4CatsNoop = "org.typelevel" %% "log4cats-noop" % "2.6.0" % Test

  val testContainers    = "com.dimafeng"        %% "testcontainers-scala-postgresql" % "0.41.3"     % Test
  val weaver            = "com.disneystreaming" %% "weaver-cats"                     % "0.8.4"      % Test
  val weaverScalaCheck  = "com.disneystreaming" %% "weaver-scalacheck"               % "0.8.4"      % Test
  val catsEffectTestKit = "org.typelevel"       %% "cats-effect-testkit"             % V.catsEffect % Test
  val munit             = "org.scalameta"       %% "munit"                           % "1.0.0-M11"  % Test
  val munitScalaCheck   = "org.scalameta"       %% "munit-scalacheck"                % "1.0.0-M11"  % Test
  val scalacheck        = "org.scalacheck"      %% "scalacheck"                      % "1.17.0"     % Test
}
