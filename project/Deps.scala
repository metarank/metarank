import sbt._

object Deps {
  lazy val http4sVersion    = "0.21.8"
  lazy val log4catsVersion  = "1.1.1"
  lazy val scalatestVersion = "3.2.2"
  lazy val circeVersion     = "0.13.0"
  lazy val circeYamlVersion = "0.13.1"
  lazy val fs2Version       = "2.4.4"

  val httpsDeps = Seq(
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )
}
