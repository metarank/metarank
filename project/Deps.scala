import sbt._

object Deps {
  lazy val http4sVersion             = "1.0.0-M44"
  lazy val log4catsVersion           = "2.4.0"
  lazy val scalatestVersion          = "3.2.19"
  lazy val circeVersion              = "0.14.14"
  lazy val circeGenericExtrasVersion = "0.14.4"
  lazy val circeYamlVersion          = "0.16.1"
  lazy val fs2Version                = "3.12.0"
  lazy val luceneVersion             = "10.2.2"
  lazy val pulsarVersion             = "4.0.5"
  lazy val awsVersion                = "2.31.70"
  lazy val prometheusVersion         = "0.16.0"
  lazy val djlVersion                = "0.28.0"

  val httpsDeps = Seq(
    "org.http4s" %% "http4s-dsl"          % http4sVersion,
    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % http4sVersion,
    "org.http4s" %% "http4s-circe"        % http4sVersion
  )
}
