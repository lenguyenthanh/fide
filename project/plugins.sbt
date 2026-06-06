addSbtPlugin("ch.epfl.scala"                % "sbt-scalafix"         % "0.14.6")
addSbtPlugin("com.disneystreaming.smithy4s" % "smithy4s-sbt-codegen" % "0.19.7")
addSbtPlugin("com.github.sbt"               % "sbt-native-packager"  % "1.11.7")
addSbtPlugin("org.scalameta"                % "sbt-scalafmt"         % "2.6.0")
addSbtPlugin("org.typelevel"                % "sbt-tpolecat"         % "0.5.5")
addSbtPlugin("io.gatling"                   % "gatling-sbt"          % "4.18.3")
addSbtPlugin("com.github.sbt"               % "sbt-release"          % "1.4.0")

// Cross-compilation to Scala.js and Scala Native
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.21.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.12")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
