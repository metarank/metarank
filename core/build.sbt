name := "core"

import Deps._

libraryDependencies ++= Seq(
  "org.typelevel"        %% "cats-effect"     % "2.2.0",
  "io.chrisdavenport"    %% "log4cats-core"   % log4catsVersion,
  "io.chrisdavenport"    %% "log4cats-slf4j"  % log4catsVersion,
  "org.scalatest"        %% "scalatest"       % scalatestVersion % "test",
  "org.scalactic"        %% "scalactic"       % scalatestVersion % "test",
  "ch.qos.logback"        % "logback-classic" % "1.2.3",
  "io.circe"             %% "circe-yaml"      % circeYamlVersion,
  "io.circe"             %% "circe-core"      % circeVersion,
  "io.circe"             %% "circe-generic"   % circeVersion,
  "io.circe"             %% "circe-parser"    % circeVersion,
  "com.github.pathikrit" %% "better-files"    % "3.9.1",
  "com.github.scopt"     %% "scopt"           % "4.0.0-RC2"
)
