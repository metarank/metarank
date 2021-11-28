import Deps._

name := "metarank"

version := "0.2-M1"

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
  "org.typelevel"                    %% "cats-effect"                % "3.3.0",
  "org.typelevel"                    %% "log4cats-core"              % log4catsVersion,
  "org.typelevel"                    %% "log4cats-slf4j"             % log4catsVersion,
  "org.scalatest"                    %% "scalatest"                  % scalatestVersion % Test,
  "org.scalactic"                    %% "scalactic"                  % scalatestVersion % Test,
  "org.scalatestplus"                %% "scalacheck-1-14"            % "3.2.2.0"        % Test,
  "ch.qos.logback"                    % "logback-classic"            % "1.2.6",
  "io.circe"                         %% "circe-yaml"                 % circeYamlVersion,
  "io.circe"                         %% "circe-core"                 % circeVersion,
  "io.circe"                         %% "circe-generic"              % circeVersion,
  "io.circe"                         %% "circe-generic-extras"       % circeVersion,
  "io.circe"                         %% "circe-parser"               % circeVersion,
  "com.github.pathikrit"             %% "better-files"               % "3.9.1",
  "com.github.scopt"                 %% "scopt"                      % "4.0.1",
  "com.github.blemale"               %% "scaffeine"                  % "5.1.1",
  "com.github.fppt"                   % "jedis-mock"                 % "0.1.23"         % Test,
  "redis.clients"                     % "jedis"                      % "3.7.0",
  "com.propensive"                   %% "magnolia"                   % "0.17.0",
  "org.scala-lang"                    % "scala-reflect"              % scalaVersion.value,
  "com.google.guava"                  % "guava"                      % "30.1.1-jre",
  "org.apache.lucene"                 % "lucene-core"                % luceneVersion,
  "org.apache.lucene"                 % "lucene-analyzers-icu"       % luceneVersion,
  "io.findify"                       %% "featury-flink"              % featuryVersion,
  "io.findify"                       %% "featury-redis"              % featuryVersion,
  "org.apache.flink"                 %% "flink-scala"                % flinkVersion,
  "org.apache.flink"                 %% "flink-statebackend-rocksdb" % flinkVersion,
  "org.apache.flink"                  % "flink-connector-files"      % flinkVersion,
  "org.apache.flink"                 %% "flink-runtime-web"          % flinkVersion,
  "org.apache.flink"                 %% "flink-streaming-scala"      % flinkVersion,
  "org.http4s"                       %% "http4s-dsl"                 % http4sVersion,
  "org.http4s"                       %% "http4s-blaze-server"        % http4sVersion,
  "org.http4s"                       %% "http4s-blaze-client"        % http4sVersion,
  "org.http4s"                       %% "http4s-circe"               % http4sVersion,
  "org.apache.httpcomponents.core5"   % "httpcore5"                  % "5.1.2",
  "org.apache.httpcomponents.client5" % "httpclient5"                % "5.1.2",
  "io.findify"                       %% "flink-adt"                  % "0.4.4",
  "io.github.metarank"               %% "ltrlib"                     % "0.1.5"
)
