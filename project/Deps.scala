import sbt._

object Deps {
  lazy val http4sVersion             = "1.0.0-M47"
  lazy val log4catsVersion           = "2.8.0"
  lazy val scalatestVersion          = "3.2.20"
  lazy val circeVersion              = "0.14.16"
  lazy val circeGenericExtrasVersion = "0.14.4"
  lazy val circeYamlVersion          = "0.16.1"
  lazy val fs2Version                = "3.13.0"
  lazy val luceneVersion             = "10.5.1"
  lazy val pulsarVersion             = "4.0.13"
  lazy val awsVersion                = "2.54.2"
  lazy val prometheusVersion         = "0.16.0"
  lazy val djlVersion                = "0.36.0"

  val httpsDeps = Seq(
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )
}
