import Dependencies.*
import org.typelevel.scalacoptions.ScalacOptions
import sbtcrossproject.CrossPlugin.autoImport.{ CrossType, crossProject }

inThisBuild(
  Seq(
    scalaVersion                           := "3.8.4",
    organization                           := "se.thanh",
    organizationName                       := "Thanh Le",
    licenses += ("agpl-v3" -> url("https://opensource.org/license/agpl-v3")),
    semanticdbEnabled                      := true, // for scalafix
    Compile / packageDoc / publishArtifact := false,
    dockerBaseImage                        := "eclipse-temurin:25-jdk-noble",
    dockerUpdateLatest                     := true,
    dockerBuildxPlatforms                  := Seq("linux/amd64", "linux/arm64")
  )
)

val commonSettings = Seq(
  tpolecatScalacOptions ++= Set(
    ScalacOptions.other("-rewrite"),
    ScalacOptions.other("-indent"),
    ScalacOptions.explain,
    ScalacOptions.other("-Wsafe-init")
  ),
  libraryDependencies ++= Seq(
    catsCore.value,
    catsEffect.value,
    fs2.value,
    log4CatsCore.value,
    weaver.value,
    weaverScalaCheck.value
  )
)

// `-release:25` targets the JVM bytecode level and is rejected by the JS/Native
// back-ends, so it is only added on the JVM platform.
val jvmReleaseSettings = Seq(
  tpolecatScalacOptions += ScalacOptions.release("25")
)

lazy val types = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/types"))
  .settings(
    name := "types",
    libraryDependencies ++= Seq(
      catsCore.value,
      iron.value,
      scalaJavaTime.value
    )
  )
  .jvmSettings(jvmReleaseSettings)

lazy val api = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/api"))
  .enablePlugins(Smithy4sCodegenPlugin)
  .settings(
    name                     := "api",
    smithy4sWildcardArgument := "?",
    // Pure crossProject keeps the smithy specs in the shared source tree; each
    // platform project's base is modules/api/.<platform>, so point codegen back
    // at the shared dir explicitly.
    Compile / smithy4sInputDirs := Seq(baseDirectory.value.getParentFile / "src" / "main" / "smithy"),
    libraryDependencies += smithy4sCore.value,
    Compile / scalacOptions += "-Wconf:src=target/scala[^/]*/src_managed/.*:silent"
  )
  .jvmSettings(jvmReleaseSettings)
  .dependsOn(types)

lazy val domain = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/domain"))
  .settings(
    name := "domain",
    commonSettings,
    libraryDependencies += scalaJavaTime.value
  )
  .jvmSettings(jvmReleaseSettings)
  .dependsOn(types)

lazy val db = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("modules/db"))
  .settings(
    commonSettings,
    name := "db",
    libraryDependencies ++= Seq(
      skunk.value,
      dumbo.value,
      ducktape.value
    ),
    // dumbo's `withResourcesIn` macro inspects the *copied* migration resources at
    // compile time, so resources must be staged before compilation on every platform.
    Compile / compile := (Compile / compile).dependsOn(Compile / copyResources).value
  )
  .jvmSettings(
    jvmReleaseSettings,
    libraryDependencies += testContainers
  )
  .nativeSettings(
    // dumbo lists migration resources at compile time; the binary must embed them.
    nativeConfig ~= (_.withEmbedResources(true))
  )
  .dependsOn(domain)

lazy val crawler = (project in file("modules/crawler"))
  .settings(
    commonSettings,
    jvmReleaseSettings,
    name := "crawler",
    libraryDependencies ++= Seq(
      fs2Compress,
      http4sClient.value,
      http4sEmberClient.value % Test
    )
  )
  .dependsOn(domain.jvm, db.jvm % "test->test;compile->compile")

lazy val backend = (project in file("modules/backend"))
  .settings(
    commonSettings,
    jvmReleaseSettings,
    name := "backend",
    libraryDependencies ++= Seq(
      smithy4sHttp4s.value,
      smithy4sHttp4sSwagger,
      http4sServer.value,
      http4sEmberClient.value,
      cirisCore.value,
      cirisHtt4s.value,
      ironCiris.value,
      log4Cats,
      logback % Runtime,
      scalacheckFaker
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true,
    Docker / packageName         := "lenguyenthanh/fide",
    Docker / maintainer          := "Thanh Le",
    Docker / dockerRepository    := Some("ghcr.io")
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(api.jvm, domain.jvm, db.jvm % "test->test;compile->compile", crawler)

lazy val cli = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("modules/cli"))
  .settings(
    commonSettings,
    name := "cli",
    libraryDependencies ++= Seq(
      fs2IO.value,
      fs2DataCsv.value,
      fs2DataCsvGen.value,
      declineCore.value,
      declineCatsEffect.value,
      smithy4sHttp4s.value,
      http4sEmberClient.value,
      cirisCore.value,
      cirisHtt4s.value
    ),
    Compile / run / fork         := true,
    Compile / run / connectInput := true
  )
  .jvmSettings(
    jvmReleaseSettings,
    libraryDependencies ++= Seq(log4Cats, logback % Runtime, log4CatsNoop.value),
    Docker / packageName      := "lenguyenthanh/fide-cli",
    Docker / maintainer       := "Thanh Le",
    Docker / dockerRepository := Some("ghcr.io")
  )
  .jvmConfigure(_.enablePlugins(JavaAppPackaging, DockerPlugin))
  .dependsOn(domain, db % "test->test;compile->compile", api)

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
  .aggregate(
    types.jvm,
    types.js,
    types.native,
    api.jvm,
    api.js,
    api.native,
    domain.jvm,
    domain.js,
    domain.native,
    db.jvm,
    db.native,
    cli.jvm,
    cli.native,
    crawler,
    backend,
    gatling
  )

addCommandAlias("prepare", "scalafixAll; scalafmtAll; scalafmtSbt")
addCommandAlias("lintCheck", "; scalafixAll --check ; scalafmtCheckAll; scalafmtSbtCheck")
