name := "metarank"

version := "0.1"

// blocked on xgboost, which is blocked on spark/flink for 2.13
scalaVersion := "2.12.15"

lazy val sharedSettings = Seq(
  organization := "me.dfdx",
  Test / logBuffered := false,
  //resolvers += "XGBoost4J Release Repo" at "https://s3-us-west-2.amazonaws.com/xgboost-maven-repo/release/",
  scalaVersion := "2.12.15",
  scalacOptions ++= Seq("-feature", "-deprecation", "-Ypartial-unification")
)

lazy val core = (project in file("core"))
  .settings(sharedSettings)

lazy val ingest = (project in file("ingest"))
  .settings(sharedSettings)
  .dependsOn(core % "test->test;compile->compile")

lazy val api = (project in file("api"))
  .settings(sharedSettings)
  .dependsOn(core % "test->test;compile->compile")
  .dependsOn(ingest % "test->test;compile->compile")

lazy val root = (project in file("."))
  .aggregate(core, ingest, api)
  .settings(
    name := "Metarank"
  )
