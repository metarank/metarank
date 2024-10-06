import sbt._

object Deps {
  lazy val http4sVersion             = "1.0.0-M41"
  lazy val log4catsVersion           = "2.4.0"
  lazy val scalatestVersion          = "3.2.19"
  lazy val circeVersion              = "0.14.9"
  lazy val circeGenericExtrasVersion = "0.14.3"
  lazy val circeYamlVersion          = "0.15.3"
  lazy val fs2Version                = "3.10.2"
  lazy val pulsarVersion             = "3.3.2"
  lazy val luceneVersion             = "9.11.1"
  lazy val awsVersion                = "2.26.20"
  lazy val prometheusVersion         = "0.16.0"
  lazy val djlVersion                = "0.28.0"

  val httpsDeps = Seq(
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )
}
