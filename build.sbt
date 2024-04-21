import Dependencies.*

inThisBuild(
  Seq(
    scalaVersion                           := "3.4.1",
    version                                := "0.1.0-SNAPSHOT",
    organization                           := "se.thanh",
    organizationName                       := "Thanh Le",
    licenses += ("agpl-v3"                 -> url("https://opensource.org/license/agpl-v3")),
    semanticdbEnabled                      := true, // for scalafix
    Compile / packageDoc / publishArtifact := false,
    dockerBaseImage := "openjdk:21"
  )
)

val commonSettings = Seq(
  scalacOptions -= "-Xfatal-warnings",
  scalacOptions ++= Seq(
    "-source:future",
    "-rewrite",
    "-indent",
    "-explain",
    "-Wunused:all"
  ),
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
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
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
    Docker / dockerExposedPorts  := Seq(9000, 9443),
    Docker / packageName         := "thanh/fide"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(smithy, domain, db, crawler)

lazy val root = project
  .in(file("."))
  .settings(publish := {}, publish / skip := true)
  .aggregate(smithy, domain, db, crawler, backend)

addCommandAlias("lint", "scalafixAll; scalafmtAll")
addCommandAlias("lintCheck", "; scalafixAll --check ; scalafmtCheckAll")
