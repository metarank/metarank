import Deps._
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}

name := "metarank"

version := "0.2.6"

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
  "org.typelevel"        %% "cats-effect"                % "3.3.9",
  "org.typelevel"        %% "log4cats-core"              % log4catsVersion,
  "org.typelevel"        %% "log4cats-slf4j"             % log4catsVersion,
  "org.scalatest"        %% "scalatest"                  % scalatestVersion % Test,
  "org.scalactic"        %% "scalactic"                  % scalatestVersion % Test,
  "org.scalatestplus"    %% "scalacheck-1-14"            % "3.2.2.0"        % Test,
  "ch.qos.logback"        % "logback-classic"            % "1.2.11",
  "io.circe"             %% "circe-yaml"                 % circeYamlVersion,
  "io.circe"             %% "circe-core"                 % circeVersion,
  "io.circe"             %% "circe-generic"              % circeVersion,
  "io.circe"             %% "circe-generic-extras"       % circeVersion,
  "io.circe"             %% "circe-parser"               % circeVersion,
  "com.github.pathikrit" %% "better-files"               % "3.9.1",
  "com.github.scopt"     %% "scopt"                      % "4.0.1",
  "redis.clients"         % "jedis"                      % "4.2.0",
  "com.github.blemale"   %% "scaffeine"                  % "5.1.2",
  "com.github.fppt"       % "jedis-mock"                 % "1.0.1"          % Test,
  "org.scala-lang"        % "scala-reflect"              % scalaVersion.value,
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
  "org.apache.flink"     % "flink-s3-fs-hadoop"  % flinkVersion,
  "org.http4s"          %% "http4s-dsl"          % http4sVersion,
  "org.http4s"          %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"          %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"          %% "http4s-circe"        % http4sVersion,
  "io.findify"          %% "flink-adt"           % "0.4.5",
  "io.github.metarank"  %% "ltrlib"              % "0.1.6.1",
  "com.github.ua-parser" % "uap-java"            % "1.5.2",
  "com.github.microwww"  % "redis-server"        % "0.3.0"
)

enablePlugins(DockerPlugin)
enablePlugins(JavaServerAppPackaging)

Compile / mainClass             := Some("ai.metarank.Main")
Compile / discoveredMainClasses := Seq()

maintainer := "Metarank team"
dockerExposedPorts ++= Seq(8080, 6123)
dockerPermissionStrategy := DockerPermissionStrategy.Run
dockerBaseImage          := "openjdk:11.0.14.1-jdk"
dockerExposedVolumes     := List("/data")
dockerAliases := List(
  DockerAlias(None, Some("metarank"), "metarank", Some("latest")),
  DockerAlias(None, Some("metarank"), "metarank", Some(version.value))
)

dockerCommands := dockerCommands.value.flatMap {
  case Cmd("USER", args @ _*) if args.contains("1001:0") =>
    Seq(
      Cmd("RUN", "apt-get update && apt-get -y install libgomp1"),
      Cmd("USER", args: _*)
    )
  case cmd => Seq(cmd)
}

/** A hack for flink-s3-fs-hadoop jar bundling a set of ancient dependencies causing classpath conflicts on fat-jar
  * building. With this approach we have a custom MergeStrategy, which drops all files from flink-s3-fs-hadoop jar if
  * there is a conflict (so probably there is a newer version of dependency in fat-jar classpath)
  */
lazy val flinkS3Conflicts = List(
  "com/google/common",
  "com/google/errorprone",
  "com/google/j2objc",
  "com/google/thirdparty",
  "org/checkerframework",
  "org/apache/commons/beanutils",
  "org/apache/commons/collections",
  "org/apache/commons/io",
  "org/apache/commons/lang3",
  "org/apache/commons/text",
  "org/apache/commons/logging"
)

val flinkMergeStrategy = FlinkMergeStrategy("flink-s3-fs-hadoop-.*".r, flinkS3Conflicts)

ThisBuild / assemblyMergeStrategy := {
  case x if flinkS3Conflicts.exists(prefix => x.startsWith(prefix)) => flinkMergeStrategy
  case PathList("module-info.class")                                => MergeStrategy.discard
  case x if x.endsWith("/module-info.class")                        => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}
assembly / assemblyJarName := "metarank.jar"
