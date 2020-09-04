name := "core"

import Deps._

libraryDependencies ++= Seq(
  "org.typelevel"     %% "cats-effect"         % "2.1.4",
  "org.http4s"        %% "http4s-dsl"          % http4sVersion,
  "org.http4s"        %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"        %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"        %% "http4s-circe"        % http4sVersion,
  "io.chrisdavenport" %% "log4cats-core"       % log4catsVersion,
  "io.chrisdavenport" %% "log4cats-slf4j"      % log4catsVersion,
  "org.scalatest"     %% "scalatest"           % scalatestVersion % "test",
  "org.scalactic"     %% "scalactic"           % scalatestVersion % "test",
  "ch.qos.logback"     % "logback-classic"     % "1.2.3",
  "ml.dmlc"           %% "xgboost4j"           % "1.2.0" excludeAll (
    ExclusionRule(organization = "com.esotericsoftware"),
    ExclusionRule(organization = "com.typesafe.akka"),
    ExclusionRule(organization = "org.scala-lang"),
    ExclusionRule(organization = "org.scalatest")
  ),
  "io.circe"             %% "circe-yaml"    % circeYamlVersion,
  "io.circe"             %% "circe-core"    % circeVersion,
  "io.circe"             %% "circe-generic" % circeVersion,
  "io.circe"             %% "circe-parser"  % circeVersion,
  "com.github.pathikrit" %% "better-files"  % "3.9.1",
  "com.github.scopt"     %% "scopt"         % "4.0.0-RC2"
)
