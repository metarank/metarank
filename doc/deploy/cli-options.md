# Metarank CLI

Metarank CLI has a set of command-line options to control its behavior. 

To run the main app, download the [latest jar file](https://github.com/metarank/metarank/releases) and run the following command:

```shell
$ java -jar metarank-x.x.x.jar

15:35:35.252 INFO  ai.metarank.Main$ - Usage: metarank <command> <options>

15:35:35.257 INFO  ai.metarank.Main$ - Supported commands: bootstrap, inference, train, upload, help.
15:35:35.258 INFO  ai.metarank.Main$ - Run 'metarank <command>' for extra options. 
15:35:35.259 INFO  ai.metarank.Main$ - - bootstrap: import historical data
15:35:35.261 INFO  ai.metarank.Main$ - - train: train the ranking ML model
15:35:35.262 INFO  ai.metarank.Main$ - - upload: push latest feature values to redis
15:35:35.263 INFO  ai.metarank.Main$ - - inference: run the inference API
15:35:35.263 INFO  ai.metarank.Main$ - - validate: check config and data files for consistency
15:35:35.265 INFO  ai.metarank.Main$ - - help: this help

```

The command-line argument structure is:
```shell
$ java -jar metarank.jar <command> <args>
```

## CLI Options

Originally Metarank used a very complicated set of command line switches to control its behavior. But more things it
supported, more obscure the switches become. So for now metarank is configured using a separate config file. See
a [sample config file](../sample-config.yml) for a source of inspiration and basic options description.

### Bootstrap

```shell
$ java -jar metarank.jar bootstrap <config file>
```

### Inference

```shell
$ java -jar metarank.jar inference <config file>
```

### Train

```shell
$ java -jar metarank.jar train <config file> <model name from config>
```

### Upload

```shell
$ java -jar metarank.jar upload <config file>
```

### Validate

A useful tool to check your config file and data files for sanity.

```shell
Usage: $ java -jar metarank.jar metarank validate <options>
Possible options:
  --config <path>       - Validate feature configuration file
  --data <path>         - Validate historical events dataset
  --help                - This is help
```