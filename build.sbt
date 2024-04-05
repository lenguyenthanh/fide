import Dependencies.*

inThisBuild(
  Seq(
    scalaVersion           := "3.4.1",
    version                := "0.1.0-SNAPSHOT",
    organization           := "se.thanh",
    organizationName       := "Thanh Le",
    licenses += ("agpl-v3" -> url("https://opensource.org/license/agpl-v3")),
    publishTo              := Option(Resolver.file("file", new File(sys.props.getOrElse("publishTo", "")))),
    semanticdbEnabled      := true, // for scalafix
    Compile / packageDoc / publishArtifact := false
  )
)

val commonSettings = Seq(
  scalacOptions -= "-Xfatal-warnings",
  scalacOptions ++= Seq(
    "-source:future",
    "-rewrite",
    "-indent",
    "-Yexplicit-nulls",
    "-explain",
    "-Wunused:all"
  ),
  libraryDependencies ++= Seq(
    catsCore,
    catsEffect,
    log4Cats,
    log4CatsNoop,
    weaver,
    weaverScalaCheck
  )
)

lazy val smithy = (project in file("modules/smithy"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "smithy",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
    )
  )

lazy val domain = (project in file("modules/domain"))
  .settings(
    commonSettings
  )

lazy val db = (project in file("modules/db"))
  .settings(
    commonSettings,
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

lazy val backend = (project in file("modules/backend"))
  .settings(
    commonSettings,
    name := "backend",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s"         % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      http4sServer
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true,
    Docker / dockerExposedPorts  := Seq(9000, 9443),
    Docker / packageName         := "thanh/fide"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(smithy)

lazy val root = project
  .in(file("."))
  .settings(publish := {}, publish / skip := true)
  .aggregate(smithy, domain, db, backend)

addCommandAlias("prepare", "scalafixAll; scalafmtAll")
addCommandAlias("check", "; scalafixAll --check ; scalafmtCheckAll")
