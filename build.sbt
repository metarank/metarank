import Deps._
import com.typesafe.sbt.packager.docker.{Cmd, ExecCmd}

ThisBuild / organization := "ai.metarank"
ThisBuild / scalaVersion := "3.8.4"
// version is derived from the latest git tag by sbt-dynver
ThisBuild / dynverVTagPrefix := false // tags are "0.8.0", not "v0.8.0"
ThisBuild / dynverSeparator  := "-"   // dynver's default "+" is not allowed in docker tags

// these DockerPlugin/UniversalPlugin defaults are unused because dockerCommands
// and Docker/mappings are fully overridden below
Global / excludeLintKeys ++= Set(
  Universal / executableScriptName,
  UniversalDocs / name,
  UniversalSrc / name,
  dockerEntrypoint
)

lazy val It = config("it").extend(Test)

lazy val root = (project in file("."))
  .enablePlugins(DockerPlugin)
  .configs(It)
  .settings(
    inConfig(It)(Defaults.testSettings),
    inConfig(It)(org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings(It)),
    name := "metarank",
    resolvers ++= Seq(
      ("maven snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")
    ),
    Test / logBuffered       := false,
    Test / parallelExecution := false,
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-Wunused:imports",
      "-release:21"
    ),
    javacOptions ++= Seq(
      "--release",
      "21"
    ),
    libraryDependencies ++= Seq(
      "org.typelevel"        %% "cats-effect"         % "3.7.1",
      "org.typelevel"        %% "shapeless3-typeable" % "3.6.0",
      "org.scalatest"        %% "scalatest"           % scalatestVersion % "test,it",
      "org.scalactic"        %% "scalactic"           % scalatestVersion % "test,it",
      "org.scalatestplus"    %% "scalacheck-1-19"     % "3.2.20.0"       % "test,it",
      "ch.qos.logback"        % "logback-classic"     % "1.6.3",
      "io.circe"             %% "circe-yaml"          % circeYamlVersion,
      "io.circe"             %% "circe-core"          % circeVersion,
      "io.circe"             %% "circe-generic"       % circeVersion,
      "io.circe"             %% "circe-parser"        % circeVersion,
      "com.github.pathikrit" %% "better-files"        % "3.9.2",
      "org.rogach"           %% "scallop"             % "6.0.0",
      "com.github.blemale"   %% "scaffeine"           % "5.3.0",
      "org.apache.kafka"      % "kafka-clients"       % "4.3.1",
      ("org.apache.pulsar"    % "pulsar-client"       % pulsarVersion).excludeAll(
        ExclusionRule("org.bouncycastle", "bcprov-ext-jdk18on")
      ),
      "org.apache.pulsar"      % "pulsar-client-admin"      % pulsarVersion % "test",
      "org.http4s"            %% "http4s-dsl"               % http4sVersion,
      "org.http4s"            %% "http4s-ember-server"      % http4sVersion,
      "org.http4s"            %% "http4s-ember-client"      % http4sVersion,
      "org.http4s"            %% "http4s-circe"             % http4sVersion,
      "org.typelevel"         %% "log4cats-core"            % log4catsVersion,
      "org.typelevel"         %% "log4cats-slf4j"           % log4catsVersion,
      "io.github.metarank"    %% "ltrlib"                   % "0.2.6",
      "io.github.metarank"     % "lightgbm4j"               % "4.6.0-2",
      "com.github.ua-parser"   % "uap-java"                 % "1.6.1",
      "org.apache.lucene"      % "lucene-core"              % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-common"   % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-icu"      % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-smartcn"  % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-kuromoji" % luceneVersion,
      "org.apache.lucene"      % "lucene-analysis-stempel"  % luceneVersion,
      ("software.amazon.awssdk" % "kinesis" % awsVersion).excludeAll(
        ExclusionRule("io.netty", "netty-codec") // monolithic jar, split into netty-codec-base in netty 4.2
      ),
      // lettuce 7.x needs netty 4.2 while awssdk's netty-nio-client is built on 4.1; the conflicting
      // monolithic netty-codec is excluded from awssdk below (pulsar shades its own netty, so is unaffected)
      "io.lettuce"             % "lettuce-core"            % "7.7.0.RELEASE",
      "com.google.guava"       % "guava"                   % "33.7.1-jre",
      "commons-io"             % "commons-io"              % "2.22.0",
      "io.sentry"              % "sentry-logback"          % "8.53.0",
      "com.fasterxml.util"     % "java-merge-sort"         % "1.1.0",
      "io.prometheus"          % "simpleclient"            % prometheusVersion,
      "io.prometheus"          % "simpleclient_hotspot"    % prometheusVersion,
      "io.prometheus"          % "simpleclient_httpserver" % prometheusVersion,
      ("software.amazon.awssdk" % "s3" % awsVersion).excludeAll(ExclusionRule("io.netty", "netty-codec")),
      ("software.amazon.awssdk" % "sts" % awsVersion).excludeAll(ExclusionRule("io.netty", "netty-codec")),
      "org.apache.commons"     % "commons-rng-sampling"    % "1.7",
      "org.apache.commons"     % "commons-rng-simple"      % "1.7",
      ("io.github.metarank"    % "librec-core"             % "3.0.0-1").excludeAll(
        ExclusionRule("org.nd4j", "guava"),
        ExclusionRule("org.nd4j", "protobuf"),
        ExclusionRule("org.jetbrains.kotlin", "kotlin-stdlib-jdk7"),
        ExclusionRule("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
        ExclusionRule("org.jetbrains.kotlin", "kotlin-stdlib-common")
      ),
      "org.rocksdb"               % "rocksdbjni"     % "10.10.1.1",
      ("org.mapdb"                % "mapdb"          % "3.1.0").exclude("net.jpountz.lz4", "lz4"),
      "com.github.jelmerk"        % "hnswlib-core"   % "1.2.1",
      "org.slf4j"                 % "jcl-over-slf4j" % "2.0.18", // librec uses commons-logging, which is JCL
      "com.microsoft.onnxruntime" % "onnxruntime"    % "1.29.0",
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
    Docker / packageName            := "metarank",
    dockerRepository                := Some("metarank"),
    dockerUpdateLatest              := true,
    dockerAliases += dockerAlias.value.withTag(Some("snapshot")),
    dockerBuildxPlatforms := Seq("linux/amd64", "linux/arm64"),
    Docker / mappings := Seq(
      assembly.value                                                                             -> "app/metarank.jar",
      fileConverter.value.toVirtualFile((baseDirectory.value / "deploy" / "metarank.sh").toPath) -> "metarank.sh"
    ),
    dockerCommands := Seq(
      Cmd("FROM", "ubuntu:jammy-20240227"),
      Cmd(
        "RUN",
        List(
          "apt-get update",
          "apt-get install -y --no-install-recommends openjdk-21-jdk-headless htop procps curl inetutils-ping libgomp1 locales",
          "sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && locale-gen",
          "rm -rf /var/lib/apt/lists/*"
        ).mkString(" && ")
      ),
      Cmd("ENV", "LANG=en_US.UTF-8", "LANGUAGE=en_US:en", "LC_ALL=en_US.UTF-8"),
      Cmd("COPY", "metarank.sh", "/metarank.sh"),
      Cmd("COPY", "app/metarank.jar", "/app/metarank.jar"),
      Cmd("RUN", "chmod +x /metarank.sh"),
      ExecCmd("ENTRYPOINT", "/metarank.sh"),
      ExecCmd("CMD", "--help")
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
    assembly / assemblyOutputPath := baseDirectory.value / "target" / "metarank.jar"
  )

// release guard: fails when the dynver version is not an exact clean git tag (extra
// commits on top of the tag, dirty working tree, or tags missing from the checkout)
lazy val assertTagVersion = taskKey[Unit]("assert that version is derived from an exact git tag")
assertTagVersion := {
  if (isSnapshot.value) sys.error(s"version ${version.value} is not an exact git tag version")
}
