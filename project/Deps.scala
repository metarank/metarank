import sbt._

object Deps {
  lazy val http4sVersion    = "1.0.0-M30"
  lazy val log4catsVersion  = "2.3.0"
  lazy val scalatestVersion = "3.2.12"
  lazy val circeVersion     = "0.14.1"
  lazy val circeYamlVersion = "0.14.1"
  lazy val fs2Version       = "3.2.2"
  lazy val flinkVersion     = "1.14.4"
  lazy val featuryVersion   = "0.3.0-M9-SNAPSHOT"
  lazy val luceneVersion    = "9.1.0"

  val httpsDeps = Seq(
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )
}
