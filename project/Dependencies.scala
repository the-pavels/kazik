import sbt.*

object Dependencies {

  object V {
    val BetterMonadicFor = "0.3.1"
    val Cats             = "2.12.0"
    val CatsEffect       = "3.5.4"
    val Circe            = "0.14.4"
    val Ciris            = "3.6.0"
    val Doobie           = "1.0.0-RC5"
    val FS2              = "3.10.2"
    val Fuuid            = "0.8.0-M2"
    val Http4s           = "0.23.27"
    val Http4sClient     = "0.23.16"
    val Http4sPrometheus = "0.24.7"
    val Http4sWS         = "1.0.0-M9"
    val KindProjector    = "0.13.3"
    val Log4Cats         = "2.7.0"
    val Logback          = "1.5.6"
    val Neutron          = "0.0.10+43-d2352253-SNAPSHOT"
    val Newtype          = "0.4.4"
    val Redis4Cats       = "1.7.2"
    val WartRemover      = "3.3.1"
    val Munit            = "1.0.0"
    val SemanticDB       = "4.9.9"
    val Sttp             = "3.9.7"
  }

  object Libs {

    val Cats         = "org.typelevel" %% "cats-core"            % V.Cats
    val CatsEffect   = "org.typelevel" %% "cats-effect"          % V.CatsEffect
    val CirceCore    = "io.circe"      %% "circe-core"           % V.Circe
    val CirceGeneric = "io.circe"      %% "circe-generic"        % V.Circe
    val CirceParser  = "io.circe"      %% "circe-parser"         % V.Circe
    val CirceExtras  = "io.circe"      %% "circe-generic-extras" % V.Circe
    val CirisCore    = "is.cir"        %% "ciris"                % V.Ciris

    val Doobie              = "org.tpolecat" %% "doobie-core"           % V.Doobie
    val DoobieH2            = "org.tpolecat" %% "doobie-h2"             % V.Doobie
    val DoobieHikari        = "org.tpolecat" %% "doobie-hikari"         % V.Doobie
    val DoobiePostgres      = "org.tpolecat" %% "doobie-postgres"       % V.Doobie
    val DoobiePostgresCirce = "org.tpolecat" %% "doobie-postgres-circe" % V.Doobie

    val FS2   = "co.fs2" %% "fs2-core" % V.FS2
    val FS2IO = "co.fs2" %% "fs2-io"   % V.FS2

    val FUUID    = "io.chrisdavenport" %% "fuuid"          % V.Fuuid
    val Log4Cats = "org.typelevel"     %% "log4cats-slf4j" % V.Log4Cats
    val Newtype  = "io.estatico"       %% "newtype"        % V.Newtype

    val Http4sCirce       = "org.http4s" %% "http4s-circe"              % V.Http4s
    val Http4sDsl         = "org.http4s" %% "http4s-dsl"                % V.Http4s
    val Http4sEmberServer = "org.http4s" %% "http4s-ember-server"       % V.Http4s
    val Http4sClient      = "org.http4s" %% "http4s-client"             % V.Http4s
    val Http4sBlazeClient = "org.http4s" %% "http4s-blaze-client"       % V.Http4sClient
    val Http4sPrometheus  = "org.http4s" %% "http4s-prometheus-metrics" % V.Http4sPrometheus

    // Redis
    val Redis4CatsEffects  = "dev.profunktor" %% "redis4cats-effects"  % V.Redis4Cats
    val Redis4CatsLog4cats = "dev.profunktor" %% "redis4cats-log4cats" % V.Redis4Cats

    // Apache Pulsar client
    val NeutronCore  = "com.chatroulette" %% "neutron-core"  % V.Neutron
    val NeutronCirce = "com.chatroulette" %% "neutron-circe" % V.Neutron

    // Other
    val Logback = "ch.qos.logback" % "logback-classic" % V.Logback
  }

  object Testing {
    val CatsEffectTestkit = "org.typelevel"                 %% "cats-effect-testkit"            % V.CatsEffect
    val MunitCore         = "org.scalameta"                 %% "munit"                          % V.Munit
    val MunitScalaCheck   = "org.scalameta"                 %% "munit-scalacheck"               % V.Munit
    val SttpCore          = "com.softwaremill.sttp.client3" %% "core"                           % V.Sttp
    val SttpFs2           = "com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2"  % V.Sttp
    val SttpCats          = "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % V.Sttp
  }

  object CompilerPlugins {
    val BetterMonadicFor = compilerPlugin("com.olegpy" %% "better-monadic-for" % V.BetterMonadicFor)
    val KindProjector = compilerPlugin(
      "org.typelevel" % "kind-projector" % V.KindProjector cross CrossVersion.full
    )
    val SemanticDB = compilerPlugin(
      "org.scalameta" % "semanticdb-scalac" % V.SemanticDB cross CrossVersion.full
    )
    val WartRemover = compilerPlugin(
      "org.wartremover" % "wartremover" % V.WartRemover cross CrossVersion.full
    )
  }
}
