# Building metarank

Metarank is written in [Scala](https://www.scala-lang.org) and uses an [SBT](https://www.scala-sbt.org) build system.
It can be built on Windows, Linux and MacOS in the following way:
1. Clone the `metarank/metarank` repo with your favourite git client.
2. Install SBT, using its [official installation manual](https://www.scala-sbt.org/download.html) for your OS.
3. From shell, run the `sbt assembly` command, and metarank build will be built into `target/scala-2.13/metarank.jar`

```bash
$ sbt assembly

[info] welcome to sbt 1.11.7 (Eclipse Adoptium Java 21.0.8)
[info] loading global plugins from /home/code/.sbt/1.0/plugins
[info] loading settings for project metarank-build from plugins.sbt ...
[info] loading project definition from /home/code/metarank/project
[info] loading settings for project root from build.sbt ...
[info] set current project to metarank (in build file:/home/code/metarank/)
[info] compiling 28 Scala sources to /home/code/metarank/target/scala-2.13/classes ...
[info] compiling 24 Scala sources to /home/code/metarank/target/scala-2.13/classes ...
[success] Total time: 48 s, completed Aug 30, 2022, 3:40:06 PM

```

## Building docker image

Docker image can be built the same way as the JAR bundle, with the following SBT command:
```bash
$ sbt docker

[info] welcome to sbt 1.11.7 (Eclipse Adoptium Java 21.0.8)
[info] loading global plugins from /home/code/.sbt/1.0/plugins
[info] loading settings for project metarank-build from plugins.sbt ...
[info] loading project definition from /home/code/metarank/project
[info] loading settings for project root from build.sbt ...
[info] set current project to metarank (in build file:/home/code/metarank/)
[info] compiling 20 Scala sources to /home/code/metarank/target/scala-2.13/classes ...
[info] Assembly up to date: /home/code/metarank/target/scala-2.13/metarank.jar
[info] Sending build context to Docker daemon  154.5MB
[info] Step 1/6 : FROM --platform=amd64 ubuntu:jammy
[info] jammy: Pulling from library/ubuntu
[info] Status: Image is up to date for ubuntu:jammy
[info]  ---> fd22b5791853
[info] Step 2/6 : RUN apt-get update && apt-get install -y --no-install-recommends openjdk-21-jdk-headless htop procps curl inetutils-ping libgomp1 locales
[info]  ---> Using cache
[info]  ---> ad63a8b493f1
[info] Step 3/6 : ADD 0/metarank.sh /metarank.sh
[info]  ---> Using cache
[info]  ---> 5d7a4c02ed99
[info] Step 4/6 : ADD 1/metarank.jar /app/metarank.jar
[info]  ---> d6b43b43952c
[info] Step 5/6 : ENTRYPOINT ["\/metarank.sh"]
[info]  ---> Running in 2df8553f846e
[info] Removing intermediate container 2df8553f846e
[info]  ---> 40d97b917326
[info] Step 6/6 : CMD ["--help"]
[info]  ---> Running in c07e81924588
[info] Removing intermediate container c07e81924588
[info]  ---> e1caa262b1f1
[info] Successfully built e1caa262b1f1
[info] Tagging image e1caa262b1f1 with name: metarank/metarank:latest
[info] Tagging image e1caa262b1f1 with name: metarank/metarank:0.7.5
[success] Total time: 26 s, completed Aug 30, 2022, 3:41:27 PM

$ docker images

REPOSITORY                        TAG                           IMAGE ID       CREATED          SIZE
metarank/metarank                 0.7.5                      e1caa262b1f1   45 seconds ago   632MB

```