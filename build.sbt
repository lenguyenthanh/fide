import Dependencies.*

inThisBuild(
  Seq(
    scalaVersion                           := "3.4.2",
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
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
    )
  ).dependsOn(types)

lazy val domain = (project in file("modules/domain"))
  .settings(
    name := "domain",
    commonSettings
  ).dependsOn(types)

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
  .dependsOn(api, domain, db, crawler)

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
  .aggregate(types, api, domain, db, crawler, backend, gatling)

addCommandAlias("lint", "scalafixAll; scalafmtAll")
addCommandAlias("lintCheck", "; scalafixAll --check ; scalafmtCheckAll")
