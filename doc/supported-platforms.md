# Supported platforms 

Metarank is a JVM applications distributed as a single JAR file. JVM makes a promise to write-once-run-everywhere, but
in pratice it's not always the case.

Supported JVM versions:
* JVM 8 is not supported anymore.
* JVM 11 recommended.
* JVM 17 has compatibility issues with the currently used Apache Flink 1.14.

Metarank uses [XGBoost](https://github.com/metarank/xgboost-java) and [LightGBM](https://github.com/metarank/lightgbm4j) 
libraries to ML inference. Official upstreams not always publish builds for all possible architectures.

Supported operating systems:
* Linux x64.
* MacOS x64. MacOS on M1 is not yet supported.
* Windows is not yet supported.

If you're on an unsupported platform (like Windows or M1 Mac), you can still use Metarank as a [docker container](deploy/docker.md): both
Windows and MacOS on M1 can run Linux x64 docker containers without any issues (but with slight performance degradation).