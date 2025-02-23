import Dependencies.*
import org.typelevel.scalacoptions.ScalacOptions

inThisBuild(
  Seq(
    scalaVersion                           := "3.6.3",
    organization                           := "se.thanh",
    organizationName                       := "Thanh Le",
    licenses += ("agpl-v3" -> url("https://opensource.org/license/agpl-v3")),
    semanticdbEnabled                      := true, // for scalafix
    Compile / packageDoc / publishArtifact := false,
    dockerBaseImage                        := "openjdk:21",
    dockerUpdateLatest                     := true
  )
)

val commonSettings = Seq(
  tpolecatScalacOptions ++= Set(
    ScalacOptions.sourceFuture,
    ScalacOptions.other("-rewrite"),
    ScalacOptions.other("-indent"),
    ScalacOptions.explain,
    ScalacOptions.release("21"),
    ScalacOptions.other("-Wsafe-init")
  ),
  libraryDependencies ++= Seq(
    catsCore,
    catsEffect,
    fs2,
    log4Cats,
    weaver,
    weaverScalaCheck
  )
)

lazy val types = (project in file("modules/types"))
  .settings(
    name := "types",
    libraryDependencies ++= Seq(
      catsCore,
      iron
    )
  )

lazy val api = (project in file("modules/api"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name                     := "api",
    smithy4sWildcardArgument := "?",
    libraryDependencies ++= Seq(
      smithy4sCore,
    )
  )
  .dependsOn(types)

lazy val domain = (project in file("modules/domain"))
  .settings(
    name := "domain",
    commonSettings
  )
  .dependsOn(types)

lazy val db = (project in file("modules/db"))
  .settings(
    commonSettings,
    name := "db",
    libraryDependencies ++= Seq(
      skunk,
      postgres,
      flyway,
      flywayPostgres,
      flyway4s,
      ducktape,
      testContainers
    )
  )
  .dependsOn(domain)

lazy val crawler = (project in file("modules/crawler"))
  .settings(
    commonSettings,
    name := "crawler",
    libraryDependencies ++= Seq(
      fs2Compress,
      http4sClient,
      http4sEmberClient % Test
    )
  )
  .dependsOn(domain, db)

lazy val backend = (project in file("modules/backend"))
  .settings(
    commonSettings,
    name := "backend",
    libraryDependencies ++= Seq(
      smithy4sHttp4s,
      smithy4sHttp4sSwagger,
      http4sServer,
      http4sEmberClient,
      cirisCore,
      cirisHtt4s,
      ironCiris,
      logback,
      scalacheckFaker
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true,
    Docker / packageName         := "lenguyenthanh/fide",
    Docker / maintainer          := "Thanh Le",
    Docker / dockerRepository    := Some("ghcr.io")
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(api, domain, full(db), crawler)

lazy val gatling = (project in file("modules/gatling"))
  .settings(name := "gatling")
  .enablePlugins(GatlingPlugin)
  .settings(
    tpolecatExcludeOptions ++= Set(ScalacOptions.warnNonUnitStatement),
    libraryDependencies ++= Seq(
      gatlingTestFramework,
      gatlingHighCharts,
      logback
    )
  )

lazy val root = project
  .in(file("."))
  .settings(publish := {}, publish / skip := true)
  .aggregate(types, api, domain, db, crawler, backend, gatling)

def full(p: Project) = p % "test->test;compile->compile"

addCommandAlias("lint", "scalafixAll; scalafmtAll")
addCommandAlias("lintCheck", "; scalafixAll --check ; scalafmtCheckAll")
