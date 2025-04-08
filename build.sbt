import Dependencies.*

import scala.collection.Seq

ThisBuild / scalaVersion := "2.13.16"

ThisBuild / evictionErrorLevel := Level.Warn

Global / concurrentRestrictions += Tags.limit(Tags.Test, 8)
Global / onChangedBuildSource := ReloadOnSourceChanges
cancelable in Global := true

val commonSettings = Seq(
  organizationName := "FlowRoulette",
  testFrameworks += new TestFramework("munit.Framework"),
  semanticdbEnabled := true,
  doc / aggregate := false,
  scalacOptions ++= List("-Ymacro-annotations", "-Yrangepos", "-Wconf:cat=unused:info"),
  ThisBuild / envFileName := "./.env",
  libraryDependencies ++= Seq(
    CompilerPlugins.BetterMonadicFor,
    CompilerPlugins.KindProjector,
    CompilerPlugins.SemanticDB,
    CompilerPlugins.WartRemover,
    Libs.CirisCore,
    Libs.NeutronCore,
    Libs.NeutronCirce,
    Libs.CirceCore            % Test,
    Libs.FUUID                % Test,
    Testing.CatsEffectTestkit % Test,
    Testing.MunitCore         % Test,
    Testing.MunitScalaCheck   % Test
  ),
  resolvers ++= Resolver
    .sonatypeOssRepos("snapshots")
    .toList :+ ("Apache public" at "https://repository.apache.org/content/groups/public/"),
  scalafmtOnCompile := false,
  Compile / run / fork := true
)

val packageSettings = Seq(
  Compile / doc / sources := Seq(),
  Compile / packageDoc / mappings := Seq()
)

// mill-like simple layout
val simpleLayout = Seq(
  Compile / scalaSource := baseDirectory.value / "src",
  Compile / resourceDirectory := baseDirectory.value / "resources",
  Test / scalaSource := baseDirectory.value / "test" / "src",
  Test / resourceDirectory := baseDirectory.value / "test" / "resources"
)

val itTestsLayout = Seq(
  Test / scalaSource := baseDirectory.value / "src",
  Test / resourceDirectory := baseDirectory.value / "resources"
)

lazy val root = project
  .in(file("."))
  .aggregate(
    core,
    sticky,
    user,
    table,
    simulator,
    devapp
  )

lazy val core = project
  .in(file("module/core"))
  .settings(commonSettings: _*)
  .settings(simpleLayout: _*)
  .settings(
    libraryDependencies ++= List(
      Libs.Cats,
      Libs.CatsEffect,
      Libs.CirceParser,
      Libs.CirceGeneric,
      Libs.Doobie,
      Libs.DoobieHikari,
      Libs.DoobiePostgres,
      Libs.DoobiePostgresCirce,
      Libs.Http4sEmberServer,
      Libs.FS2,
      Libs.FUUID,
      Libs.Logback,
      Libs.Log4Cats,
      Libs.Newtype,
      Libs.Redis4CatsEffects
    )
  )

lazy val sticky = project
  .in(file("module/sticky"))
  .settings(packageSettings: _*)
  .settings(commonSettings: _*)
  .settings(simpleLayout: _*)
  .settings(
    libraryDependencies ++= List(
      Libs.FUUID,
      Libs.Http4sCirce,
      Libs.Http4sDsl,
      Libs.Logback % "runtime"
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val user = project
  .in(file("module/user-handler"))
  .settings(packageSettings: _*)
  .settings(commonSettings: _*)
  .settings(simpleLayout: _*)
  .settings(
    libraryDependencies ++= List(
      Libs.FUUID,
      Libs.Logback % "runtime",
      Libs.Redis4CatsEffects,
      Libs.Redis4CatsLog4cats
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val table = project
  .in(file("module/table-handler"))
  .settings(packageSettings: _*)
  .settings(commonSettings: _*)
  .settings(simpleLayout: _*)
  .settings(
    libraryDependencies ++= List(
      Libs.FUUID,
      Libs.Logback % "runtime",
      Libs.Redis4CatsEffects,
      Libs.Redis4CatsLog4cats,
      Libs.Http4sDsl,
      Libs.Http4sCirce
    )
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val devapp = project
  .in(file("module/devapp"))
  .settings(packageSettings: _*)
  .settings(commonSettings: _*)
  .settings(simpleLayout: _*)
  .dependsOn(sticky % "compile->compile;test->test")
  .dependsOn(user % "compile->compile;test->test")
  .dependsOn(table % "compile->compile;test->test")

lazy val simulator = project
  .in(file("module/simulator"))
  .settings(packageSettings: _*)
  .settings(commonSettings: _*)
  .settings(simpleLayout: _*)
  .settings(
    libraryDependencies ++= List(
      Libs.FUUID,
      Libs.Logback % "runtime",
      Testing.SttpCats,
      Testing.SttpCore,
      Testing.SttpFs2
    )
  )
  .dependsOn(core % "compile->compile;test->test")
  .dependsOn(table % "compile->compile;test->test")
