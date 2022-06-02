import Deps._

ThisBuild / organization := "ai.metarank"
ThisBuild / scalaVersion := "2.13.8"
ThisBuild / version      := "0.3.1-k8s"

/** A hack for flink-s3-fs-hadoop jar bundling a set of ancient dependencies causing classpath conflicts on fat-jar
  * building. With this approach we have a custom MergeStrategy, which drops all files from flink-s3-fs-hadoop jar if
  * there is a conflict (so probably there is a newer version of dependency in fat-jar classpath)
  */
lazy val flinkS3Conflicts = List(
  "com/google/common",
  "com/google/errorprone",
  "com/google/j2objc",
  "com/google/thirdparty",
  "org/checkerframework",
  "org/apache/commons/beanutils",
  "org/apache/commons/codec",
  "org/apache/commons/collections",
  "org/apache/commons/io",
  "org/apache/commons/lang3",
  "org/apache/commons/text",
  "org/apache/commons/logging",
  "org/apache/http",
  "com/fasterxml/jackson/"
)

val flinkMergeStrategy = FlinkMergeStrategy("flink-s3-fs-hadoop-.*".r, flinkS3Conflicts)

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
      "-Xfatal-warnings"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel"        %% "cats-effect"                % "3.3.12",
      "org.typelevel"        %% "log4cats-core"              % log4catsVersion,
      "org.typelevel"        %% "log4cats-slf4j"             % log4catsVersion,
      "org.scalatest"        %% "scalatest"                  % scalatestVersion % "test,it",
      "org.scalactic"        %% "scalactic"                  % scalatestVersion % "test,it",
      "org.scalatestplus"    %% "scalacheck-1-16"            % "3.2.12.0"       % "test,it",
      "ch.qos.logback"        % "logback-classic"            % "1.2.11",
      "io.circe"             %% "circe-yaml"                 % circeYamlVersion,
      "io.circe"             %% "circe-core"                 % circeVersion,
      "io.circe"             %% "circe-generic"              % circeVersion,
      "io.circe"             %% "circe-generic-extras"       % circeVersion,
      "io.circe"             %% "circe-parser"               % circeVersion,
      "com.github.pathikrit" %% "better-files"               % "3.9.1",
      "com.github.scopt"     %% "scopt"                      % "4.0.1",
      "redis.clients"         % "jedis"                      % "4.2.3",
      "com.github.blemale"   %% "scaffeine"                  % "5.2.0",
      "com.github.fppt"       % "jedis-mock"                 % "1.0.2"          % "test,it",
      "org.scala-lang"        % "scala-reflect"              % scalaVersion.value,
      "io.findify"           %% "featury-flink"              % featuryVersion,
      "io.findify"           %% "featury-redis"              % featuryVersion,
      "org.apache.flink"      % "flink-statebackend-rocksdb" % flinkVersion,
      "org.apache.flink"      % "flink-connector-files"      % flinkVersion,
      "org.apache.flink"      % "flink-runtime-web"          % flinkVersion,
      "io.findify"           %% "flink-scala-api"            % "1.15-2",
      "org.apache.flink"      % "flink-connector-kafka"      % flinkVersion,
      "org.apache.flink"      % "flink-connector-pulsar"     % flinkVersion excludeAll (
        ExclusionRule("com.sun.activation", "javax.activation")
      ),
      "org.apache.flink" % "flink-test-utils" % flinkVersion excludeAll (
        ExclusionRule("org.apache.curator"),
        ExclusionRule("org.apache.logging.log4j", "log4j-slf4j-impl"),
        ExclusionRule("org.apache.logging.log4j")
      ),
      "org.apache.flink"           % "flink-s3-fs-hadoop"       % flinkVersion,
      "org.http4s"                %% "http4s-dsl"               % http4sVersion,
      "org.http4s"                %% "http4s-blaze-server"      % http4sVersion,
      "org.http4s"                %% "http4s-blaze-client"      % http4sVersion,
      "io.github.metarank"        %% "ltrlib"                   % "0.1.12",
      "com.github.ua-parser"       % "uap-java"                 % "1.5.2",
      "com.github.microwww"        % "redis-server"             % "0.3.0",
      "com.snowplowanalytics"     %% "scala-referer-parser"     % "2.0.0",
      "org.apache.lucene"          % "lucene-core"              % luceneVersion,
      "org.apache.lucene"          % "lucene-analysis-common"   % luceneVersion,
      "org.apache.lucene"          % "lucene-analysis-icu"      % luceneVersion,
      "org.apache.lucene"          % "lucene-analysis-smartcn"  % luceneVersion,
      "org.apache.lucene"          % "lucene-analysis-kuromoji" % luceneVersion,
      "org.apache.lucene"          % "lucene-analysis-stempel"  % luceneVersion,
      "org.apache.httpcomponents"  % "httpclient"               % "4.5.13",
      "com.fasterxml.jackson.core" % "jackson-annotations"      % "2.12.4" // should match flink's version
    ),
    Compile / mainClass             := Some("ai.metarank.Main"),
    Compile / discoveredMainClasses := Seq(),
    docker / dockerfile := {
      val artifact: File     = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from("flink:1.15")
        run("mkdir", "/opt/flink/plugins/s3-fs-hadoop")
        run("cp", "/opt/flink/opt/flink-s3-fs-hadoop-1.15.0.jar", "/opt/flink/plugins/s3-fs-hadoop/")
        run("rm", "/opt/flink/lib/flink-scala_2.12-1.15.0.jar")
        run("apt-get", "update")
        run("apt-get", "-y", "install", "htop", "procps", "curl", "inetutils-ping", "openjdk-11-jdk-headless")
        add(new File("deploy/metarank.sh"), "/metarank.sh")
        add(artifact, artifactTargetPath)
        entryPoint("/metarank.sh")
      }
    },
    docker / imageNames := Seq(
      ImageName("metarank/metarank:latest"),
      ImageName(s"metarank/metarank:${version.value}")
    ),
    ThisBuild / assemblyMergeStrategy := {
      case x if flinkS3Conflicts.exists(prefix => x.startsWith(prefix)) => flinkMergeStrategy
      case PathList("module-info.class")                                => MergeStrategy.discard
      case "META-INF/io.netty.versions.properties"                      => MergeStrategy.first
      case "findbugsExclude.xml"                                        => MergeStrategy.discard
      case "log4j2-test.properties"                                     => MergeStrategy.discard
      case x if x.endsWith("/module-info.class")                        => MergeStrategy.discard
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / assemblyJarName := "metarank.jar"
  )
