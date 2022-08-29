# Metarank CLI

Metarank CLI has a set of command-line options to control its behavior. 

To run the main app, download the [latest jar file](https://github.com/metarank/metarank/releases) and run the following command:

```shell
$ java -jar metarank-x.x.x.jar
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
  -o, --offset  <arg>   offset: earliest, latest, ts=1661516516, last=1h
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
  -o, --offset  <arg>   offset: earliest, latest, ts=1661516516, last=1h
                        (optional, default=earliest)
  -h, --help            Show help message

Subcommand: sort - sort the input file by timestamp
  -c, --config  <arg>   path to config file
  -d, --data  <arg>     path to a directory with input files
  -f, --format  <arg>   input file format: json, snowplow, snowplow:tsv,
                        snowplow:json (optional, default=json)
  -o, --offset  <arg>   offset: earliest, latest, ts=1661516516, last=1h
                        (optional, default=earliest)
      --out  <arg>      output file path
  -h, --help            Show help message

For all other tricks, consult the docs on https://docs.metarank.ai

```

The command-line argument structure is:
```shell
$ java -jar metarank.jar <command> <args>
```

## CLI Options

Originally Metarank used a very complicated set of command line switches to control its behavior. But more things it
supported, more obscure the switches become. So for now metarank is configured using a separate config file. See
a [sample config file](../configuration/sample-config.yml) for a source of inspiration and basic options description.


## Running modes

Metarank CLI has a set of different running modes:
* `import`: import and process historical data, writing state to the chosen [persistecnce backend](../configuration/persistence.md) like Redis.
* `train`: train the ML model with XGBoost/LightGBM.
* `serve`: run the inference API to do realtime reranking
* `standalone`: run `import`, `train` and `serve` tasks at once.

## Environment variables

Config file can be passed to the Metarank not only as a command-line argument, but also as an environment variable.
This is typically used in docker and k8s-based deployments:
* `METARANK_CONFIG`: path to config file, for example `s3://bucket/prefix/config.yml`