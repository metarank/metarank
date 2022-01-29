import Deps._

name := "metarank"

version := "0.2.0"

resolvers ++= Seq(
  ("maven snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")
)

// blocked on xgboost, which is blocked on spark/flink for 2.13

organization             := "me.dfdx"
Test / logBuffered       := false
Test / parallelExecution := false
scalaVersion             := "2.12.15"
scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-Ypartial-unification",
  "-Xfatal-warnings"
)

libraryDependencies ++= Seq(
  "org.typelevel"        %% "cats-effect"                % "3.3.5",
  "org.typelevel"        %% "log4cats-core"              % log4catsVersion,
  "org.typelevel"        %% "log4cats-slf4j"             % log4catsVersion,
  "org.scalatest"        %% "scalatest"                  % scalatestVersion % Test,
  "org.scalactic"        %% "scalactic"                  % scalatestVersion % Test,
  "org.scalatestplus"    %% "scalacheck-1-14"            % "3.2.2.0"        % Test,
  "ch.qos.logback"        % "logback-classic"            % "1.2.10",
  "io.circe"             %% "circe-yaml"                 % circeYamlVersion,
  "io.circe"             %% "circe-core"                 % circeVersion,
  "io.circe"             %% "circe-generic"              % circeVersion,
  "io.circe"             %% "circe-generic-extras"       % circeVersion,
  "io.circe"             %% "circe-parser"               % circeVersion,
  "com.github.pathikrit" %% "better-files"               % "3.9.1",
  "com.github.scopt"     %% "scopt"                      % "4.0.1",
  "redis.clients"         % "jedis"                      % "4.0.1",
  "com.github.blemale"   %% "scaffeine"                  % "5.1.2",
  "com.github.fppt"       % "jedis-mock"                 % "1.0.0"          % Test,
  "org.scala-lang"        % "scala-reflect"              % scalaVersion.value,
  "com.google.guava"      % "guava"                      % "30.1.1-jre",
  "io.findify"           %% "featury-flink"              % featuryVersion,
  "io.findify"           %% "featury-redis"              % featuryVersion,
  "org.apache.flink"     %% "flink-scala"                % flinkVersion,
  "org.apache.flink"     %% "flink-statebackend-rocksdb" % flinkVersion,
  "org.apache.flink"      % "flink-connector-files"      % flinkVersion,
  "org.apache.flink"     %% "flink-runtime-web"          % flinkVersion,
  "org.apache.flink"     %% "flink-streaming-scala"      % flinkVersion,
  "org.apache.flink"     %% "flink-test-utils"           % flinkVersion excludeAll (
    ExclusionRule("org.apache.curator"),
    ExclusionRule("org.apache.logging.log4j", "log4j-slf4j-impl"),
  ),
  "org.http4s"         %% "http4s-dsl"          % http4sVersion,
  "org.http4s"         %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"         %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"         %% "http4s-circe"        % http4sVersion,
  "io.findify"         %% "flink-adt"           % "0.4.5",
  "io.github.metarank" %% "ltrlib"              % "0.1.6.1"
)

enablePlugins(DockerPlugin)
enablePlugins(JavaServerAppPackaging)
dockerExposedPorts ++= Seq(8080)
dockerBaseImage := "openjdk:11"

ThisBuild / assemblyMergeStrategy := {
  case PathList("module-info.class")         => MergeStrategy.discard
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
