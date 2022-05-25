# Metarank CLI

Metarank CLI has a set of command-line options to control its behavior. 

To run the main app, download the [latest jar file](https://github.com/metarank/metarank/releases) and run the following command:

```shell
$ java -jar metarank-x.x.x.jar

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

The command-line argument structure is:
```shell
$ java -jar metarank.jar <command> <args>
```

## CLI Options

Originally Metarank used a very complicated set of command line switches to control its behavior. But more things it
supported, more obscure the switches become. So for now metarank is configured using a separate config file. See
a [sample config file](../sample-config.yml) for a source of inspiration and basic options description.


## Running modes

Metarank CLI has a set of different running modes:
* `bootstrap`: import and process historical data, build a state snapshot for the `update`/`standalone` jobs and training
data for the `train` job.
* `train`: train the ML model with XGBoost/LightGBM.
* `upload`: push the latest state snapshot to remote Redis server for the `update` job.
* `api`: run the inference API to do realtime reranking
* `update`: run the ML feature update job
* `standalone`: run `upload`, `api`, `update` tasks at once with the embedded redis server.
* `validate`: check the historical data for typical problems.

