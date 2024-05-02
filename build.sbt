import Dependencies.*

inThisBuild(
  Seq(
    scalaVersion                           := "3.4.1",
    organization                           := "se.thanh",
    organizationName                       := "Thanh Le",
    licenses += ("agpl-v3"                 -> url("https://opensource.org/license/agpl-v3")),
    semanticdbEnabled                      := true, // for scalafix
    Compile / packageDoc / publishArtifact := false,
    dockerBaseImage                        := "openjdk:21",
    dockerUpdateLatest                     := true
  )
)

val commonSettings = Seq(
  scalacOptions -= "-Xfatal-warnings",
  scalacOptions ++= Seq(
    "-source:future",
    "-rewrite",
    "-indent",
    "-explain",
    "-Wunused:all",
    "-release:21"
  ),
  javacOptions ++= Seq("--release", "21"),
  libraryDependencies ++= Seq(
    catsCore,
    catsEffect,
    fs2,
    log4Cats,
    log4CatsNoop,
    weaver,
    weaverScalaCheck
  )
)

lazy val smithy = (project in file("modules/smithy"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name                     := "smithy",
    smithy4sWildcardArgument := "?",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value,
      catsCore,
      iron
    )
  )

lazy val domain = (project in file("modules/domain"))
  .settings(
    name := "domain",
    commonSettings
  )

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
      otel,
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
      http4sClient
    )
  )
  .dependsOn(domain, db)

lazy val backend = (project in file("modules/backend"))
  .settings(
    commonSettings,
    name := "backend",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s"         % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      http4sServer,
      http4sEmberClient,
      cirisCore,
      cirisHtt4s,
      logback
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true,
    Docker / packageName         := "lenguyenthanh/fide",
    Docker / maintainer          := "Thanh Le",
    Docker / dockerRepository    := Some("ghcr.io")
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(smithy, domain, db, crawler)

lazy val gatling = (project in file("modules/gatling"))
  .settings(name := "gatling")
  .enablePlugins(GatlingPlugin)
  .settings(
    scalacOptions -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      gatlingTestFramework,
      gatlingHighCharts,
      logback
    )
  )

lazy val root = project
  .in(file("."))
  .settings(publish := {}, publish / skip := true)
  .aggregate(smithy, domain, db, crawler, backend, gatling)

addCommandAlias("lint", "scalafixAll; scalafmtAll")
addCommandAlias("lintCheck", "; scalafixAll --check ; scalafmtCheckAll")
