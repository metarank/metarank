# Metarank and Docker

Metarank official image is published in docker hub as [metarank/metarank](https://hub.docker.com/r/metarank/metarank/tags).

We publish the `:latest` tag, although it's not always recommended to have any production deployments without pinning a specific version. 

Official docker images are multi-arch, and cross-built for the following platforms:
* linux/amd64: a regular docker image
* linux/arm64/v8: docker image which will work natively (so without emulation) on platforms like Mac M1/M2.

## Running the docker image

All metarank sub-commands are wrapped into a single command-line API. To see the [CLI](/doc/cli.md), run the docker container:
```shell
$ docker run metarank/metarank:0.7.4 --help

+ exec /opt/java/openjdk/bin/java -jar /app/metarank.jar --help

                __                              __    
  _____   _____/  |______ ____________    ____ |  | __
 /     \_/ __ \   __\__  \\_  __ \__  \  /    \|  |/ /
|  Y Y  \  ___/|  |  / __ \|  | \// __ \|   |  \    < 
|__|_|  /\___  >__| (____  /__|  (____  /___|  /__|_ \
      \/     \/          \/           \/     \/     \/ ver:0.7.4
Usage: metarank <subcommand> <options>
```

### Resources

Metarank image exposes a `/data` volume to handle all the local IO. 
For example, you can pass the input training dataset from your local host using the docker's `-v` switch:
```shell
docker run -v /home/user/input:/data metarank/metarank:latest train <opts>
```

#### Memory

Metarank docker container uses 1Gb of JVM heap by default. In practice the actual RSS memory usage is a bit higher than the heap size due to JVM's extra overhead. 

This can be configured with the `JAVA_OPTS` environment variable:
```shell
docker run -e JAVA_OPTS="-Xmx5g" metarank/metarank:latest train <opts>
```


### Ports

The image exposes the following ports:
* 8080 for API access for the inference and ingestion APIs

To map these ports to your host, use the `-p` flag:
```shell
docker run -p 8080:8080 metarank/metarank:latest serve <opts>
```
