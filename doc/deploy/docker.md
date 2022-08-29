# Metarank and Docker

Metarank official image is published in docker hub as [metarank/metarank](https://hub.docker.com/r/metarank/metarank/tags).

We publish the `:latest` tag, although it's not always recommended to have any production deployments without pinning a specific
version. 

## Running the docker image

All metarank sub-commands are wrapped into a single command-line API. To see the [CLI](cli.md), run the docker container:
```shell
$ docker run metarank/metarank:0.5.0 --help

+ exec /opt/java/openjdk/bin/java -jar /app/metarank.jar --help

                __                              __    
  _____   _____/  |______ ____________    ____ |  | __
 /     \_/ __ \   __\__  \\_  __ \__  \  /    \|  |/ /
|  Y Y  \  ___/|  |  / __ \|  | \// __ \|   |  \    < 
|__|_|  /\___  >__| (____  /__|  (____  /___|  /__|_ \
      \/     \/          \/           \/     \/     \/
Usage: metarank <subcommand> <options>
Options:

  -h, --help      Show help message
  -v, --version   Show version of this program

Subcommand: import - import historical clickthrough data
  -c, --config  <arg>   path to config file
  -d, --data  <arg>     path to a directory with input files
  -f, --format  <arg>   input file format: json, snowplow, snowplow:tsv,
                        snowplow:json (optional, default=json)
  -o, --offset  <arg>   offset: earliest, latest, ts=1661764518, last=1h
                        (optional, default=earliest)
  -h, --help            Show help message

Subcommand: train - train the ML model
  -c, --config  <arg>   path to config file
  -m, --model  <arg>    model name to train
  -h, --help            Show help message

Subcommand: serve - run the inference API
  -c, --config  <arg>   path to config file
  -h, --help            Show help message

Subcommand: standalone - import, train and serve at once
  -c, --config  <arg>   path to config file
  -d, --data  <arg>     path to a directory with input files
  -f, --format  <arg>   input file format: json, snowplow, snowplow:tsv,
                        snowplow:json (optional, default=json)
  -o, --offset  <arg>   offset: earliest, latest, ts=1661764518, last=1h
                        (optional, default=earliest)
  -h, --help            Show help message

Subcommand: validate - run the input data validation suite
  -c, --config  <arg>   path to config file
  -d, --data  <arg>     path to a directory with input files
  -f, --format  <arg>   input file format: json, snowplow, snowplow:tsv,
                        snowplow:json (optional, default=json)
  -o, --offset  <arg>   offset: earliest, latest, ts=1661778589, last=1h
                        (optional, default=earliest)
  -v, --validation      should input validation be enabled (optional,
                        default=yes)
  -h, --help            Show help message

For all other tricks, consult the docs on https://docs.metarank.ai

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

To map these ports to your host, use the `-p` flag:
```shell
docker run -p 8080:8080 metarank/metarank:latest serve <opts>
```
