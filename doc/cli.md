# Metarank CLI

Metarank CLI has a set of command-line options to control its behavior. 

To run the main app, download the [latest jar file](https://github.com/metarank/metarank/releases) and run the following command:

```shell
java -jar metarank-x.x.x.jar
```

```shell

                __                              __    
  _____   _____/  |______ ____________    ____ |  | __
 /     \_/ __ \   __\__  \\_  __ \__  \  /    \|  |/ /
|  Y Y  \  ___/|  |  / __ \|  | \// __ \|   |  \    < 
|__|_|  /\___  >__| (____  /__|  (____  /___|  /__|_ \
      \/     \/          \/           \/     \/     \/ Metarank v:unknown
Usage: metarank <subcommand> <options>
Options:

  -h, --help      Show help message
  -v, --version   Show version of this program

Subcommand: import - import historical clickthrough data
  -c, --config  <arg>          path to config file
  -d, --data  <arg>            path to an input file
  -f, --format  <arg>          input file format: json, snowplow, snowplow:tsv,
                               snowplow:json (optional, default=json)
  -o, --offset  <arg>          offset: earliest, latest, ts=1663171036, last=1h
                               (optional, default=earliest)
  -s, --sort-files-by  <arg>   how should multiple input files be sorted
                               (optional, default: name, values:
                               [name,last-modified]
  -v, --validation  <arg>      should input validation be enabled (optional,
                               default=false)
  -h, --help                   Show help message

Subcommand: train - train the ML model
  -c, --config  <arg>   path to config file
  -e, --export  <arg>   a directory to export model training files
  -m, --model  <arg>    model name to train
  -h, --help            Show help message

Subcommand: serve - run the inference API
  -c, --config  <arg>   path to config file
  -h, --help            Show help message

Subcommand: standalone - import, train and serve at once
  -c, --config  <arg>          path to config file
  -d, --data  <arg>            path to an input file
  -f, --format  <arg>          input file format: json, snowplow, snowplow:tsv,
                               snowplow:json (optional, default=json)
  -o, --offset  <arg>          offset: earliest, latest, ts=1663171036, last=1h
                               (optional, default=earliest)
  -s, --sort-files-by  <arg>   how should multiple input files be sorted
                               (optional, default: name, values:
                               [name,last-modified]
  -v, --validation  <arg>      should input validation be enabled (optional,
                               default=false)
  -h, --help                   Show help message

Subcommand: validate - run the input data validation suite
  -c, --config  <arg>          path to config file
  -d, --data  <arg>            path to an input file
  -f, --format  <arg>          input file format: json, snowplow, snowplow:tsv,
                               snowplow:json (optional, default=json)
  -o, --offset  <arg>          offset: earliest, latest, ts=1663171036, last=1h
                               (optional, default=earliest)
  -s, --sort-files-by  <arg>   how should multiple input files be sorted
                               (optional, default: name, values:
                               [name,last-modified]
  -v, --validation  <arg>      should input validation be enabled (optional,
                               default=false)
  -h, --help                   Show help message

Subcommand: sort - sort the dataset by timestamp
  -d, --data  <arg>   path to a directory with input files
  -o, --out  <arg>    path to an output file
  -h, --help          Show help message

Subcommand: autofeature - generate reference config based on existing data
  -d, --data  <arg>            path to an input file
  -f, --format  <arg>          input file format: json, snowplow, snowplow:tsv,
                               snowplow:json (optional, default=json)
  -o, --offset  <arg>          offset: earliest, latest, ts=1663171036, last=1h
                               (optional, default=earliest)
      --out  <arg>             path to an output config file
  -r, --ruleset  <arg>         set of rules to generate config: stable, all
                               (optional, default=stable, values: [stable, all])
  -s, --sort-files-by  <arg>   how should multiple input files be sorted
                               (optional, default: name, values:
                               [name,last-modified]
  -v, --validation  <arg>      should input validation be enabled (optional,
                               default=false)
  -h, --help                   Show help message

For all other tricks, consult the docs on https://docs.metarank.ai
```

The command-line argument structure is:

```shell
java -jar metarank.jar <command> <args>
```


## Running modes

Metarank CLI has a set of different running modes:
* `import`: import and process historical data, writing state to the chosen [persistecnce backend](../configuration/persistence.md) like Redis.
* [`train`](#training-the-model): train the ML model with XGBoost/LightGBM.
* `serve`: run the inference API to do realtime reranking
* `standalone`: run `import`, `train` and `serve` tasks at once.
* [`validate`](#validation): validates data nd configuration files.
* [`sort`](#historical-data-sorting): pre-sorts the dataset by timestamp.
* [`autofeature`](#auto-feature-generation): automatically generates feature configuration based on yourr data.

### Validation

Metarank CLI provides `validate` command to validate both your data and configuration file. 

You will need to provide both data and configuration files
```shell
java -jar metarank-x.x.x.jar validate --config config.yml --data events.jsonl.gz
```

The above command will output validation checks performed on the files provided and will output information similar to the following:

```shell
17:46:45.790 INFO  ai.metarank.config.Config$ - api conf block is not defined: using default ApiConfig(Hostname(localhost),Port(8080))
17:46:45.793 INFO  ai.metarank.config.Config$ - state conf block is not defined: using default MemoryStateConfig()
17:46:45.798 INFO  ai.metarank.config.Config$ - Loaded config file, state=memory, features=[popularity,vote_avg,vote_cnt,budget,release_date,runtime,title_length,genre,ctr,liked_genre,liked_actors,liked_tags,liked_director,visitor_click_count,global_item_click_count,day_item_click_count], models=[xgboost]
17:46:45.874 INFO  ai.metarank.FeatureMapping - optimized schema: removed 6 unused features
17:46:46.023 INFO  ai.metarank.main.command.Validate$ - Dataset validation is enabled
17:46:46.024 INFO  ai.metarank.main.command.Validate$ - Validation loads all events to RAM, so use --validation=false to skip in case of OOM
17:46:46.085 INFO  ai.metarank.source.FileEventSource - path=events.jsonl.gz is a file
17:46:46.144 INFO  ai.metarank.source.FileEventSource - file events.jsonl.gz selected=true (timeMatch=true formatMatch=true)
17:46:46.146 INFO  ai.metarank.source.FileEventSource - reading file events.jsonl.gz (with gzip decompressor)
17:46:55.240 INFO  ai.metarank.main.command.Validate$ - Validation done
17:46:55.280 INFO  a.m.v.checks.EventOrderValidation$ - Event ordering check = PASS (58437 events sorted by timestamp)
17:46:55.351 INFO  a.m.v.checks.EventTypesValidation$ - event types check = PASS (2512 item events, 9800 rankings, 46125 interactions)
17:46:55.441 INFO  a.m.v.c.FeatureOverMissingFieldValidation$ - field reference check = PASS (16 features referencing existing 12 event fields)
17:46:55.509 INFO  a.m.v.c.InteractionKeyValidation$ - interaction-ranking join key check = PASS (9800 rankings, all interactions reference existing ones)
17:46:55.603 ERROR a.m.v.c.InteractionMetadataValidation$ - Interaction metadata check: FAIL (96 interaction happened on never seen items)
17:46:55.604 ERROR a.m.v.c.InteractionMetadataValidation$ - examples: List(ecc0c55e-8e30-4f7d-8c7e-26f05951300b, 22dda925-8dec-46e0-98d2-bc6a32dcdaec, 40342ea0-1575-417f-8c3d-d3e8dcdfa7f5, 1c0504a5-8e2c-4a0d-be92-b9930df8d041, bb9a79b2-689c-492b-b758-9f1ce0fade09, c2b5d050-3dae-4b24-a7ba-1652d6f4b2e2, 0689b95a-4532-4d05-b87d-e69daed6c910, 5592ff1e-cccd-4157-8c7e-052242c56d0b, d9d1fd91-0629-4623-af2d-1a0682ee62a7, 0d9ef491-ef19-4003-9f20-09d0daea481a)
17:46:55.827 INFO  a.m.v.c.InteractionPositionValidation$ - interaction positions check = PASS (int distribution: [2435,2309,2199,2068,2005,2008,1970,2003,1890,1925,1933,1847,1807,1778,1790,1781,1787,1778,1713,1783,1745,1789,1791,1991]
17:46:55.858 INFO  a.m.v.c.InteractionTypeValidation$ - interaction type check = PASS (46125 interactions have known types: Set(click))
17:46:55.859 INFO  ai.metarank.main.Main$ - My job is done, exiting.
```

### Historical data sorting

Metarank expects your historical data to be ordered by the timestamp in the ascending order. 
If for any reason, you cannot generate a sorted file, the `sort` sub-command can do the job for you. 

You can sort both signle files and folders with multiple files. In case of folders, `sort` command will merge all data into one sorted file. 

Sorting one file is a simple as 
```bash
java -jar metarank.jar sort --data unosrted_file.jsonl.gz --out sorted_file.jsonl.gz
```

You can do sorting with a folder as well
```bash
java -jar metarank.jar sort --data /my_folder --out sorted_file.jsonl.gz
```

### Auto feature generation

If you don't know what [features](/doc/configuration/feature-extractors.md) to include in the configuration file, the `autofeature` sub-command can generate the configuration for you
based on the historical data you have. 

Simply run

```shell
java -jar metarank.jar autofeature --data /path/to/events.json --out /path/to/config.yaml
```

Check out more about `autofeature` sub-command in our [Automatic feature engineering guide](/doc/howto/autofeature.md).

### Training the model

You can train the underlying ML ranking model:
```shell
java -jar metarank.jar train --config /path/to/config.yaml
```

* if the `--model <name>` option is not given, then Metarank will train all the defined models sequentally. 
* the `--export <dir>` option dumps train+test datasets in the CSV format. It can be useful for separate hyper-parameter optimization.


## Environment variables

Config file can be passed to the Metarank not only as a command-line argument, but also as an environment variable.
This is typically used in docker and k8s-based deployments:
* `METARANK_CONFIG`: path to config file, for example `s3://bucket/prefix/config.yml`