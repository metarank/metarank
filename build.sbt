import Deps._

lazy val PLATFORM = Option(System.getenv("PLATFORM")).getOrElse("amd64")

ThisBuild / organization := "ai.metarank"
ThisBuild / scalaVersion := "2.13.14"
ThisBuild / version      := "0.7.10"

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
    javacOptions ++= Seq(
      "-source",
      "11",
      "-target",
      "11"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel"        %% "cats-effect"          % "3.5.4",
      "org.scalatest"        %% "scalatest"            % scalatestVersion % "test,it",
      "org.scalactic"        %% "scalactic"            % scalatestVersion % "test,it",
      "org.scalatestplus"    %% "scalacheck-1-16"      % "3.2.14.0"       % "test,it",
      "ch.qos.logback"        % "logback-classic"      % "1.5.6",
      "io.circe"             %% "circe-yaml"           % circeYamlVersion,
      "io.circe"             %% "circe-core"           % circeVersion,
      "io.circe"             %% "circe-generic"        % circeVersion,
      "io.circe"             %% "circe-generic-extras" % circeGenericExtrasVersion,
      "io.circe"             %% "circe-parser"         % circeVersion,
      "com.github.pathikrit" %% "better-files"         % "3.9.2",
      "org.rogach"           %% "scallop"              % "5.1.0",
      "com.github.blemale"   %% "scaffeine"            % "5.3.0",
      "org.apache.kafka"      % "kafka-clients"        % "3.7.1",
      "org.apache.pulsar"     % "pulsar-client"        % pulsarVersion excludeAll (
        ExclusionRule("org.bouncycastle", "bcprov-ext-jdk18on")
      ),
      "org.apache.pulsar"      % "pulsar-client-admin"      % pulsarVersion % "test",
      "org.http4s"            %% "http4s-dsl"               % http4sVersion,
      "org.http4s"            %% "http4s-ember-server"      % http4sVersion,
      "org.http4s"            %% "http4s-ember-client"      % http4sVersion,
      "org.http4s"            %% "http4s-circe"             % http4sVersion,
      "org.typelevel"         %% "log4cats-core"            % "2.7.0",
      "org.typelevel"         %% "log4cats-slf4j"           % "2.7.0",
      "io.github.metarank"    %% "ltrlib"                   % "0.2.6",
      "com.github.ua-parser"   % "uap-java"                 % "1.6.1",
      "com.snowplowanalytics" %% "scala-referer-parser"     % "2.0.0",
      "org.apache.lucene"      % "lucene-core"              % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-common"   % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-icu"      % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-smartcn"  % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-kuromoji" % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-stempel"  % luceneVersion,
      "software.amazon.awssdk" % "kinesis"                  % awsVersion,
      "io.lettuce"             % "lettuce-core"             % "6.3.2.RELEASE",
      "com.google.guava"       % "guava"                    % "33.2.1-jre",
      "commons-io"             % "commons-io"               % "2.16.1",
      "io.sentry"              % "sentry-logback"           % "7.22.0",
      "com.fasterxml.util"     % "java-merge-sort"          % "1.1.0",
      "io.prometheus"          % "simpleclient"             % prometheusVersion,
      "io.prometheus"          % "simpleclient_hotspot"     % prometheusVersion,
      "io.prometheus"          % "simpleclient_httpserver"  % prometheusVersion,
      "software.amazon.awssdk" % "s3"                       % awsVersion,
      "org.apache.commons"     % "commons-rng-sampling"     % "1.5",
      "org.apache.commons"     % "commons-rng-simple"       % "1.5",
      "io.github.metarank"     % "librec-core"              % "3.0.0-1" excludeAll (
        ExclusionRule("org.nd4j", "guava"),
        ExclusionRule("org.nd4j", "protobuf"),
        ExclusionRule("org.jetbrains.kotlin", "kotlin-stdlib-jdk7"),
        ExclusionRule("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
        ExclusionRule("org.jetbrains.kotlin", "kotlin-stdlib-common")
      ),
      "org.rocksdb"               % "rocksdbjni"     % "9.4.0",
      "org.mapdb"                 % "mapdb"          % "3.1.0" exclude ("net.jpountz.lz4", "lz4"),
      "com.github.jelmerk"        % "hnswlib-core"   % "1.1.2",
      "org.slf4j"                 % "jcl-over-slf4j" % "2.0.13", // librec uses commons-logging, which is JCL
      "com.microsoft.onnxruntime" % "onnxruntime"    % "1.18.0",
      "ai.djl"                    % "api"            % djlVersion,
      "ai.djl.huggingface"        % "tokenizers"     % djlVersion,
      "co.fs2"                   %% "fs2-core"       % fs2Version,
      "co.fs2"                   %% "fs2-io"         % fs2Version
    ),
    excludeDependencies ++= Seq(
      "commons-logging" % "commons-logging"
    ),
    Compile / mainClass             := Some("ai.metarank.main.Main"),
    Compile / discoveredMainClasses := Seq(),
    docker / dockerfile := {
      val artifact: File     = assembly.value
      val artifactTargetPath = s"/app/${artifact.name}"

      new Dockerfile {
        from(s"--platform=$PLATFORM ubuntu:jammy-20240227")
        runRaw(
          List(
            "apt-get update",
            "apt-get install -y --no-install-recommends openjdk-21-jdk-headless htop procps curl inetutils-ping libgomp1 locales",
            "sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && locale-gen",
            "rm -rf /var/lib/apt/lists/*"
          ).mkString(" && ")
        )
        env(
          Map(
            "LANG"     -> "en_US.UTF-8  ",
            "LANGUAGE" -> "en_US:en ",
            "LC_ALL"   -> "en_US.UTF-8  "
          )
        )
        add(new File("deploy/metarank.sh"), "/metarank.sh")
        add(artifact, artifactTargetPath)
        entryPoint("/metarank.sh")
        cmd("--help")
      }
    },
    docker / imageNames := Seq(
      ImageName(s"metarank/metarank:${version.value}-$PLATFORM"),
      ImageName(s"metarank/metarank:snapshot")
    ),
    docker / buildOptions := BuildOptions(
      removeIntermediateContainers = BuildOptions.Remove.Always,
      pullBaseImage = BuildOptions.Pull.Always
    ),
    ThisBuild / assemblyMergeStrategy := {
      case PathList("module-info.class")                                         => MergeStrategy.discard
      case "META-INF/io.netty.versions.properties"                               => MergeStrategy.first
      case "META-INF/MANIFEST.MF"                                                => MergeStrategy.discard
      case "META-INF/versions/9/OSGI-INF/MANIFEST.MF"                            => MergeStrategy.discard
      case "META-INF/native-image/reflect-config.json"                           => MergeStrategy.concat
      case "META-INF/native-image/io.netty/netty-common/native-image.properties" => MergeStrategy.first
      case "META-INF/native-image/io.netty/netty-transport/reflect-config.json"  => MergeStrategy.first
      case "META-INF/okio.kotlin_module"                                         => MergeStrategy.first
      case "findbugsExclude.xml"                                                 => MergeStrategy.discard
      case "log4j2-test.properties"                                              => MergeStrategy.discard
      case x if x.endsWith("/module-info.class")                                 => MergeStrategy.discard
      case x if x.startsWith("META-INF/versions/9/org/yaml/snakeyaml/internal")  => MergeStrategy.discard
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    },
    assembly / assemblyJarName := "metarank.jar"
  )
