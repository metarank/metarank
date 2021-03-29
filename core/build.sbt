name := "core"

import Deps._

libraryDependencies ++= Seq(
  "org.typelevel"        %% "cats-effect"          % "3.0.0",
  "io.chrisdavenport"    %% "log4cats-core"        % log4catsVersion,
  "io.chrisdavenport"    %% "log4cats-slf4j"       % log4catsVersion,
  "org.scalatest"        %% "scalatest"            % scalatestVersion % Test,
  "org.scalactic"        %% "scalactic"            % scalatestVersion % Test,
  "org.scalatestplus"    %% "scalacheck-1-14"      % "3.2.2.0"        % Test,
  "ch.qos.logback"        % "logback-classic"      % "1.2.3",
  "io.circe"             %% "circe-yaml"           % circeYamlVersion,
  "io.circe"             %% "circe-core"           % circeVersion,
  "io.circe"             %% "circe-generic"        % circeVersion,
  "io.circe"             %% "circe-generic-extras" % circeVersion,
  "io.circe"             %% "circe-parser"         % circeVersion,
  "com.github.pathikrit" %% "better-files"         % "3.9.1",
  "com.github.scopt"     %% "scopt"                % "4.0.1",
  "com.github.blemale"   %% "scaffeine"            % "4.0.2",
  "com.github.fppt"       % "jedis-mock"           % "0.1.16"         % Test,
  "redis.clients"         % "jedis"                % "3.5.2",
  "com.propensive"       %% "magnolia"             % "0.17.0",
  "org.scala-lang"        % "scala-reflect"        % scalaVersion.value,
  "com.google.guava"      % "guava"                % "30.1-jre",
  "org.apache.lucene"     % "lucene-core"          % luceneVersion,
  "org.apache.lucene"     % "lucene-analyzers-icu" % luceneVersion
)
