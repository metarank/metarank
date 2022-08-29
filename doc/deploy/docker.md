# Metarank and Docker

Metarank official image is published in docker hub as [metarank/metarank](https://hub.docker.com/r/metarank/metarank/tags).

We publish the `:latest` tag, although it's not always recommended to have any production deployments without pinning a specific
version.

## Running the docker image

All metarank sub-commands are wrapped into a single command-line API. To see the [CLI](/doc/cli.md), run the docker container:
```shell
$ docker run metarank/metarank:latest help

12:33:57.337 INFO  ai.metarank.Main$ - Usage: metarank <command> <config file> <options>

12:33:57.342 INFO  ai.metarank.Main$ - Supported commands: bootstrap, inference, train, upload, help.
12:33:57.346 INFO  ai.metarank.Main$ - Run 'metarank <command> for extra options. 
12:33:57.347 INFO  ai.metarank.Main$ - - bootstrap: import historical data
12:33:57.348 INFO  ai.metarank.Main$ - - train: train the ranking ML model
12:33:57.350 INFO  ai.metarank.Main$ - - upload: push latest feature values to redis
12:33:57.351 INFO  ai.metarank.Main$ - - api: run the inference API
12:33:57.352 INFO  ai.metarank.Main$ - - update: run the Flink update job
12:33:57.352 INFO  ai.metarank.Main$ - - standalone: run the Flink update job, embedded redis and API in the same JVM
12:33:57.353 INFO  ai.metarank.Main$ - - validate: check config and data files for consistency
12:33:57.354 INFO  ai.metarank.Main$ - - help: this help

```

### Resources

Metarank image exposes a `/data` volume to handle all the local IO. 
For example, you can pass the input training dataset from your local host using the docker's `-v` switch:
```shell
docker -v /data:/home/user/input run metarank/metarank:latest train <opts>
```

### Ports

The image exposes the following ports:
* 8080 for API access for the inference and ingestion APIs
* 6123 to communicate with embedded flink cluster

To map these ports to your host, use the `-p` flag:
```shell
docker run -p 8080:8080 metarank/metarank:latest inference <opts>
```
