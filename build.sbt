import Dependencies.*
import org.typelevel.scalacoptions.ScalacOptions

scalaVersion                           := "3.8.3"
organization                           := "se.thanh"
organizationName                       := "Thanh Le"
licenses += ("agpl-v3" -> url("https://opensource.org/license/agpl-v3"))
semanticdbEnabled                      := true // for scalafix
Compile / packageDoc / publishArtifact := false

val dockerSettings = Seq(
  dockerBaseImage       := "eclipse-temurin:25-jdk-noble",
  dockerUpdateLatest    := true,
  dockerBuildxPlatforms := Seq("linux/amd64", "linux/arm64")
)

Global / excludeLintKeys ++= Set(
  Debian / executableScriptName,
  Debian / sourceDirectory,
  Rpm / daemonStdoutLogFile,
  Rpm / executableScriptName,
  Rpm / name,
  Rpm / sourceDirectory,
  Universal / executableScriptName,
  UniversalDocs / name,
  UniversalSrc / name,
  rpmScriptsDirectory
)

val commonSettings = Seq(
  tpolecatScalacOptions ++= Set(
    ScalacOptions.other("-rewrite"),
    ScalacOptions.other("-indent"),
    ScalacOptions.explain,
    ScalacOptions.release("25"),
    ScalacOptions.other("-Wsafe-init"),
    ScalacOptions.other("-Wconf:src=target/out/scala[^/]*/src_managed/.*:silent")
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
      smithy4sCore
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
      dumbo,
      ducktape,
      testContainers
    ),
    Compile / dependencyClasspath := {
      val prev      = (Compile / dependencyClasspath).value
      val resDir    = (Compile / resourceDirectory).value
      val converter = fileConverter.value
      prev :+ Attributed.blank(converter.toVirtualFile(resDir.toPath))
    }
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
      logback % Runtime,
      scalacheckFaker
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true,
    Docker / packageName         := "lenguyenthanh/fide",
    Docker / maintainer          := "Thanh Le",
    Docker / dockerRepository    := Some("ghcr.io"),
    dockerSettings
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(api, domain, full(db), crawler)

lazy val cli = (project in file("modules/cli"))
  .settings(
    commonSettings,
    name := "cli",
    libraryDependencies ++= Seq(
      fs2IO,
      fs2DataCsv,
      fs2DataCsvGen,
      declineCore,
      declineCatsEffect,
      smithy4sHttp4s,
      http4sEmberClient,
      cirisCore,
      cirisHtt4s,
      ironCiris,
      logback % Runtime
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true,
    Docker / packageName         := "lenguyenthanh/fide-cli",
    Docker / maintainer          := "Thanh Le",
    Docker / dockerRepository    := Some("ghcr.io"),
    dockerSettings
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(domain, full(db), api)

// lazy val gatling = (project in file("modules/gatling"))
// .settings(name := "gatling")
// .enablePlugins(GatlingPlugin)
// .settings(
// tpolecatExcludeOptions ++= Set(ScalacOptions.warnNonUnitStatement),
// libraryDependencies ++= Seq(
// gatlingTestFramework,
// gatlingHighCharts,
// logback
// )
// )

lazy val root = rootProject.autoAggregate

def full(p: Project) = p % "test->test;compile->compile"

addCommandAlias("prepare", "scalafixAll; scalafmtAll; scalafmtSbt")
addCommandAlias("lintCheck", "; scalafixAll --check ; scalafmtCheckAll; scalafmtSbtCheck")
