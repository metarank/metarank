name := "core"

import Deps._

libraryDependencies ++= Seq(
  "org.typelevel"        %% "cats-effect"          % "3.2.9",
  "org.typelevel"        %% "log4cats-core"        % log4catsVersion,
  "org.typelevel"        %% "log4cats-slf4j"       % log4catsVersion,
  "org.scalatest"        %% "scalatest"            % scalatestVersion % Test,
  "org.scalactic"        %% "scalactic"            % scalatestVersion % Test,
  "org.scalatestplus"    %% "scalacheck-1-14"      % "3.2.2.0"        % Test,
  "ch.qos.logback"        % "logback-classic"      % "1.2.6",
  "io.circe"             %% "circe-yaml"           % circeYamlVersion,
  "io.circe"             %% "circe-core"           % circeVersion,
  "io.circe"             %% "circe-generic"        % circeVersion,
  "io.circe"             %% "circe-generic-extras" % circeVersion,
  "io.circe"             %% "circe-parser"         % circeVersion,
  "com.github.pathikrit" %% "better-files"         % "3.9.1",
  "com.github.scopt"     %% "scopt"                % "4.0.1",
  "com.github.blemale"   %% "scaffeine"            % "5.1.1",
  "com.github.fppt"       % "jedis-mock"           % "0.1.23"         % Test,
  "redis.clients"         % "jedis"                % "3.7.0",
  "com.propensive"       %% "magnolia"             % "0.17.0",
  "org.scala-lang"        % "scala-reflect"        % scalaVersion.value,
  "com.google.guava"      % "guava"                % "30.1.1-jre",
  "org.apache.lucene"     % "lucene-core"          % luceneVersion,
  "org.apache.lucene"     % "lucene-analyzers-icu" % luceneVersion
)
