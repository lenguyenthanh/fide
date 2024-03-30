ThisBuild / scalaVersion := "3.4.1"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "se.thanh"
ThisBuild / organizationName := "Thanh Le"

lazy val root = (project in file("."))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name := "fide",
    libraryDependencies ++= Seq(
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s" % smithy4sVersion.value,
      "com.disneystreaming.smithy4s" %% "smithy4s-http4s-swagger" % smithy4sVersion.value,
      "org.http4s" %% "http4s-ember-server" % "0.23.26"
    ),
    Compile / run / fork := true,
    Compile / run / connectInput := true
  )
