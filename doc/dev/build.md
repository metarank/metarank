# Building metarank

Metarank is written in [Scala](https://www.scala-lang.org) and uses an [SBT](https://www.scala-sbt.org) build system.
It can be built on Windows, Linux and MacOS in the following way:
1. Clone the `metarank/metarank` repo with your favourite git client.
2. Install JDK 21+ and SBT 2.x, using the sbt [official installation manual](https://www.scala-sbt.org/download.html) for your OS.
3. From shell, run the `sbt assembly` command, and the metarank fat jar will be built into `target/metarank.jar`

```bash
$ sbt assembly
```

## Building docker image

The docker image is defined in `build.sbt` with [sbt-native-packager](https://sbt-native-packager.readthedocs.io/). To build it locally:
```bash
$ sbt Docker/publishLocal

$ docker images

REPOSITORY                        TAG           IMAGE ID       CREATED          SIZE
metarank/metarank                 0.8.0         e1caa262b1f1   45 seconds ago   632MB
```

## Releasing

Releases are automated with GitHub Actions (`.github/workflows/release.yml`):
1. Bump `ThisBuild / version` in `build.sbt` and commit to master.
2. Tag the release with a bare semver tag and push it:
```bash
$ git tag 0.8.1 && git push origin 0.8.1
```
3. The workflow verifies that the tag matches the build.sbt version, builds the jar, pushes the multi-arch (amd64+arm64) `metarank/metarank:<version>|latest|snapshot` images to Docker Hub, and creates a GitHub release with auto-generated notes and the `metarank-<version>.jar` attached.
