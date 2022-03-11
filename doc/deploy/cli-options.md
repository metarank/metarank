# Metarank CLI tool options

Metarank CLI tool has a set of command-line options to control its behavior. To run the main app, download the jar file
and do the following:
```shell
$ java -jar metarank-assembly-x.x.x.jar

15:35:35.252 INFO  ai.metarank.Main$ - Usage: metarank <command> <options>

15:35:35.257 INFO  ai.metarank.Main$ - Supported commands: bootstrap, inference, train, upload, help.
15:35:35.258 INFO  ai.metarank.Main$ - Run 'metarank <command> for extra options. 
15:35:35.259 INFO  ai.metarank.Main$ - - bootstrap: import historical data
15:35:35.261 INFO  ai.metarank.Main$ - - train: train the ranking ML model
15:35:35.262 INFO  ai.metarank.Main$ - - upload: push latest feature values to redis
15:35:35.263 INFO  ai.metarank.Main$ - - inference: run the inference API
15:35:35.265 INFO  ai.metarank.Main$ - - help: this help

```

The command-line argument structure is:
```shell
$ java -jar metarank-assembly-x.x.x.jar <command> <args>
```

### Environment variables

Arguments for metarank subcommands can be also supplied using environment variables.
Variable name format is `METARANK_` prefix and uppercased option name. An example:
* `--parallelism` becomes `METARANK_PARALLELISM`
* `--savepoint-dir` becomes `METARANK_SAVEPOINT_DIR`

### Bootstrap

```shell
Metarank v0.x
Usage: Bootstrap [options]

  --events <value>       full path to directory containing historical events, with file:// or s3:// prefix
  --out <value>          output directory
  --config <value>       config file
  --parallelism <value>  parallelism for a bootstrap job
```

### Inference

```shell
Metarank v0.x
Usage: Inference API [options]

  --savepoint-dir <value>  full path savepoint snapshot, the /savepoint dir after the bootstrap phase
  --model <value>          full path to model file to serve
  --format <value>         state encoding format, protobuf/json
  --config <value>         config file with feature definition
  --port <value>           HTTP port to bind to, default 8080
  --interface <value>      network inferface to bind to, default is bind to everything
  --redis-host <value>     redis host
  --redis-port <value>     redis port, 6379 by default
  --batch-size <value>     redis batch size, default 1
  --embedded-redis-features-dir <value>
                           path to /features directory after the bootstrap for embedded redis, like file:///tmp/data or s3://bucket/dir
```

### Train

```shell
Usage: Train [options]

  --input <value>       path to /dataset directory after the bootstrap, like file:///tmp/data or s3://bucket/dir
  --split <value>       train/validation split in percent, default 80/20
  --model-file <value>  model output file
  --iterations <value>  number of iterations for model training, default 200
  --config <value>      config file with feature definition
  --model-type <value>  which model to train

```

### Upload

```shell
Usage: Train [options]

  --features-dir <value>  path to /features directory after the bootstrap, like file:///tmp/data or s3://bucket/dir
  --host <value>          redis host
  --port <value>          redis port, 6379 by default
  --batch-size <value>    write batch size, 1000 by default
  --format <value>        state encoding format, protobuf/json
```