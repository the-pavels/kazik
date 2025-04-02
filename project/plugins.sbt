resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.6")
addSbtPlugin("org.typelevel"    % "sbt-tpolecat"        % "0.5.1")
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"        % "2.5.2")
addSbtPlugin("ch.epfl.scala"    % "sbt-scalafix"        % "0.12.1")
addSbtPlugin("nl.gn0s1s"        % "sbt-dotenv"          % "3.0.0")
