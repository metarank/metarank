name := "core"

import Deps._

libraryDependencies ++= Seq(
  "org.typelevel"        %% "cats-effect"          % "2.2.0",
  "io.chrisdavenport"    %% "log4cats-core"        % log4catsVersion,
  "io.chrisdavenport"    %% "log4cats-slf4j"       % log4catsVersion,
  "org.scalatest"        %% "scalatest"            % scalatestVersion % "test",
  "org.scalactic"        %% "scalactic"            % scalatestVersion % "test",
  "org.scalatestplus"    %% "scalacheck-1-14"      % "3.2.2.0"        % "test",
  "ch.qos.logback"        % "logback-classic"      % "1.2.3",
  "io.circe"             %% "circe-yaml"           % circeYamlVersion,
  "io.circe"             %% "circe-core"           % circeVersion,
  "io.circe"             %% "circe-generic"        % circeVersion,
  "io.circe"             %% "circe-generic-extras" % circeVersion,
  "io.circe"             %% "circe-parser"         % circeVersion,
  "com.github.pathikrit" %% "better-files"         % "3.9.1",
  "com.github.scopt"     %% "scopt"                % "4.0.0-RC2",
  "com.github.blemale"   %% "scaffeine"            % "4.0.1",
  "com.github.fppt"       % "jedis-mock"           % "0.1.16"         % "test",
  "redis.clients"         % "jedis"                % "2.9.0"
)
