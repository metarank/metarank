# Metarank and Docker

Metarank official image is published in docker hub as [metarank/metarank](https://hub.docker.com/r/metarank/metarank/tags).

## Running the docker image

All metarank sub-commands are wrapped into a single command-line API. To see the [CLI options](cli-options.md), run the docker container:
```shell
$ docker run metarank/metarank:0.2.1 help

12:35:48.228 INFO  ai.metarank.Main$ - Usage: metarank <command> <options>

12:35:48.232 INFO  ai.metarank.Main$ - Supported commands: bootstrap, inference, train, upload, help.
12:35:48.232 INFO  ai.metarank.Main$ - Run 'metarank <command>' for extra options. 
12:35:48.233 INFO  ai.metarank.Main$ - - bootstrap: import historical data
12:35:48.234 INFO  ai.metarank.Main$ - - train: train the ranking ML model
12:35:48.234 INFO  ai.metarank.Main$ - - upload: push latest feature values to redis
12:35:48.235 INFO  ai.metarank.Main$ - - inference: run the inference API
12:35:48.236 INFO  ai.metarank.Main$ - - help: this help

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
docker -p 8080:8080 run metarank/metarank:latest inference <opts>
```