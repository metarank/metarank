name := "metarank"

version := "0.1"

// blocked on xgboost, which is blocked on spark/flink for 2.13
scalaVersion := "2.12.12"

resolvers += "XGBoost4J Release Repo" at "https://s3-us-west-2.amazonaws.com/xgboost-maven-repo/release/"

lazy val http4sVersion    = "0.21.7"
lazy val log4catsVersion  = "1.1.1"
lazy val scalatestVersion = "3.2.0"

libraryDependencies ++= Seq(
  "com.github.pureconfig" %% "pureconfig"          % "0.13.0",
  "org.typelevel"         %% "cats-effect"         % "2.1.4",
  "org.http4s"            %% "http4s-dsl"          % http4sVersion,
  "org.http4s"            %% "http4s-blaze-server" % http4sVersion,
  "org.http4s"            %% "http4s-blaze-client" % http4sVersion,
  "org.http4s"            %% "http4s-circe"        % http4sVersion,
  "io.chrisdavenport"     %% "log4cats-core"       % log4catsVersion,
  "io.chrisdavenport"     %% "log4cats-slf4j"      % log4catsVersion,
  "org.scalatest"         %% "scalatest"           % scalatestVersion % "test",
  "org.scalactic"         %% "scalactic"           % scalatestVersion % "test",
  "ch.qos.logback"         % "logback-classic"     % "1.2.3",
  "ml.dmlc"               %% "xgboost4j"           % "1.1.1" exclude ("com.esotericsoftware.kryo", "kryo")
)
