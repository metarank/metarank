import Deps._

lazy val PLATFORM = Option(System.getenv("PLATFORM")).getOrElse("amd64")

ThisBuild / organization := "ai.metarank"
ThisBuild / scalaVersion := "2.13.9"
ThisBuild / version      := "0.5.5"

lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin)
  .configs(IntegrationTest extend Test)
  .settings(
    Defaults.itSettings,
    name := "metarank",
    resolvers ++= Seq(
      ("maven snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")
    ),
    Test / logBuffered       := false,
    Test / parallelExecution := false,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Xfatal-warnings",
      "-release:11"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel"         %% "cats-effect"              % "3.3.14",
      "org.scalatest"         %% "scalatest"                % scalatestVersion % "test,it",
      "org.scalactic"         %% "scalactic"                % scalatestVersion % "test,it",
      "org.scalatestplus"     %% "scalacheck-1-16"          % "3.2.14.0"       % "test,it",
      "ch.qos.logback"         % "logback-classic"          % "1.4.3",
      "io.circe"              %% "circe-yaml"               % circeYamlVersion,
      "io.circe"              %% "circe-core"               % circeVersion,
      "io.circe"              %% "circe-generic"            % circeVersion,
      "io.circe"              %% "circe-generic-extras"     % circeGenericExtrasVersion,
      "io.circe"              %% "circe-parser"             % circeVersion,
      "com.github.pathikrit"  %% "better-files"             % "3.9.1",
      "org.rogach"            %% "scallop"                  % "4.1.0",
      "com.github.blemale"    %% "scaffeine"                % "5.2.1",
      "org.apache.kafka"       % "kafka-clients"            % "3.3.1",
      "org.apache.pulsar"      % "pulsar-client"            % pulsarVersion,
      "org.apache.pulsar"      % "pulsar-client-admin"      % pulsarVersion    % "test",
      "org.http4s"            %% "http4s-dsl"               % http4sVersion,
      "org.http4s"            %% "http4s-blaze-server"      % http4sVersion,
      "org.http4s"            %% "http4s-blaze-client"      % http4sVersion,
      "io.github.metarank"    %% "ltrlib"                   % "0.1.15",
      "com.github.ua-parser"   % "uap-java"                 % "1.5.3",
      "com.snowplowanalytics" %% "scala-referer-parser"     % "2.0.0",
      "org.apache.lucene"      % "lucene-core"              % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-common"   % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-icu"      % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-smartcn"  % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-kuromoji" % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-stempel"  % luceneVersion,
      "software.amazon.awssdk" % "kinesis"                  % "2.17.286",
      "io.lettuce"             % "lettuce-core"             % "6.2.0.RELEASE",
      "commons-io"             % "commons-io"               % "2.11.0",
      "com.google.guava"       % "guava"                    % "31.1-jre",
      "io.sentry"              % "sentry-logback"           % "6.4.2",
      "com.fasterxml.util"     % "java-merge-sort"          % "1.0.2"
    ),
    Compile / mainClass             := Some("ai.metarank.main.Main"),
    Compile / discoveredMainClasses := Seq(),
    docker / dockerfile := {
      val artifact: File     = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from(s"--platform=$PLATFORM ubuntu:jammy-20221003")
        runRaw(
          "apt-get update && apt-get -y install openjdk-17-jdk-headless openjdk-17-dbg htop procps curl inetutils-ping libgomp1"
        )
        add(new File("deploy/metarank.sh"), "/metarank.sh")
        add(artifact, artifactTargetPath)
        entryPoint("/metarank.sh")
        cmd("--help")
      }
    },
    docker / imageNames := Seq(
      ImageName(s"metarank/metarank:${version.value}-$PLATFORM")
    ),
    docker / buildOptions := BuildOptions(
      removeIntermediateContainers = BuildOptions.Remove.Always,
      pullBaseImage = BuildOptions.Pull.Always
    ),
    ThisBuild / assemblyMergeStrategy := {
      case PathList("module-info.class")           => MergeStrategy.discard
      case "META-INF/io.netty.versions.properties" => MergeStrategy.first
      case "findbugsExclude.xml"                   => MergeStrategy.discard
      case "log4j2-test.properties"                => MergeStrategy.discard
      case x if x.endsWith("/module-info.class")   => MergeStrategy.discard
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / assemblyJarName := "metarank.jar"
  )
