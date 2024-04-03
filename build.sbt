ThisBuild / scalaVersion     := "3.4.1"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "se.thanh"
ThisBuild / organizationName := "Thanh Le"

lazy val smithy = (project in file("modules/smithy"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "smithy",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-core" % smithy4sVersion.value
    )
  )

lazy val backend = (project in file("modules/backend"))
  .settings(
    name := "backend",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s"         % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      "org.http4s"                   %% "http4s-ember-server"     % "0.23.26"
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true
  )
  .dependsOn(smithy)

lazy val root = project
  .in(file("."))
  .settings(publish := {}, publish / skip := true)
  .aggregate(smithy, backend)
