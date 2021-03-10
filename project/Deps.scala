import sbt._

object Deps {
  lazy val http4sVersion    = "0.21.20"
  lazy val log4catsVersion  = "1.1.1"
  lazy val scalatestVersion = "3.2.6"
  lazy val circeVersion     = "0.13.0"
  lazy val circeYamlVersion = "0.13.1"
  lazy val fs2Version       = "2.5.3"
  lazy val luceneVersion    = "8.8.1"

  val httpsDeps = Seq(
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )
}
