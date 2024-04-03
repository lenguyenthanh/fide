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
    Compile / run / connectInput := true,
    Docker / dockerExposedPorts  := Seq(9000, 9443),
    Docker / packageName         := "thanh/fide"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(smithy)

lazy val root = project
  .in(file("."))
  .settings(publish := {}, publish / skip := true)
  .aggregate(smithy, backend)

addCommandAlias("prepare", "scalafixAll; scalafmtAll")
addCommandAlias("check", "; scalafixAll --check ; scalafmtCheckAll")
