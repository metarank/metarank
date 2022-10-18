# Automatic feature engineering

A typical problem: to write a Metarank [config file](../configuration/overview.md) with event to feature mapping, 
you need to read the docs on [feature extraction](../configuration/feature-extractors.md) and well-understand your click-through
input dataset:
* Which fields do items have? Which values does each field have?
* Do these values look like categories?
* How many unique values are there per field?

Nobody likes reading docs and writing YAML, so Metarank has an [AutoML level-4 style](https://medium.com/@tunguz/six-levels-of-auto-ml-a277aa1f0f38)
generator of typical feature extractors based on the [historical click-through dataset](../event-schema.md) you already have.

## Running the autofeature generator

Use the `autofeature` sub-command from the main binary:
```bash
                __                              __    
  _____   _____/  |______ ____________    ____ |  | __
 /     \_/ __ \   __\__  \\_  __ \__  \  /    \|  |/ /
|  Y Y  \  ___/|  |  / __ \|  | \// __ \|   |  \    < 
|__|_|  /\___  >__| (____  /__|  (____  /___|  /__|_ \
      \/     \/          \/           \/     \/     \/ ver:None
Usage: metarank <subcommand> <options>

Subcommand: autofeature - generate reference config based on existing data
  -d, --data  <arg>      path to a directory with input files
  -f, --format  <arg>    input file format: json, snowplow, snowplow:tsv,
                         snowplow:json (optional, default=json)
  -o, --offset  <arg>    offset: earliest, latest, ts=1663161962, last=1h
                         (optional, default=earliest)
      --out  <arg>       path to an output config file
  -r, --ruleset  <arg>   set of rules to generate config: stable, all (optional,
                         default=stable, values: [stable, all])
  -h, --help             Show help message

For all other tricks, consult the docs on https://docs.metarank.ai
```

An example minimal command to generate the config file for your dataset:
```bash
java -jar metarank.jar autofeature --data /path/to/events.json --out /path/to/config.yaml
```

For a [RankLens](https://github.com/metarank/ranklens) dataset, for example, it will emit the following:
```
15:32:11.284 INFO  a.metarank.main.command.AutoFeature$ - Generating config file
15:32:11.524 INFO  ai.metarank.source.FileEventSource - path=/home/shutty/code/metarank/src/test/resources/ranklens/events/events.jsonl.gz is a file
15:32:11.537 INFO  ai.metarank.source.FileEventSource - file /home/shutty/code/metarank/src/test/resources/ranklens/events/events.jsonl.gz selected=true (timeMatch=true formatMatch=true)
15:32:11.538 INFO  ai.metarank.source.FileEventSource - reading file /home/shutty/code/metarank/src/test/resources/ranklens/events/events.jsonl.gz (with gzip decompressor)
15:32:15.686 INFO  a.metarank.main.command.AutoFeature$ - Event model statistics collected
15:32:15.691 INFO  a.m.m.c.a.r.InteractionFeatureRule$ - generated interacted_with feature for interaction 'click' over field 'writer'
15:32:15.692 INFO  a.m.m.c.a.r.InteractionFeatureRule$ - generated interacted_with feature for interaction 'click' over field 'tags'
15:32:15.692 INFO  a.m.m.c.a.r.InteractionFeatureRule$ - generated interacted_with feature for interaction 'click' over field 'director'
15:32:15.693 INFO  a.m.m.c.a.r.InteractionFeatureRule$ - generated interacted_with feature for interaction 'click' over field 'title'
15:32:15.693 INFO  a.m.m.c.a.r.InteractionFeatureRule$ - generated interacted_with feature for interaction 'click' over field 'genres'
15:32:15.693 INFO  a.m.m.c.a.r.InteractionFeatureRule$ - generated interacted_with feature for interaction 'click' over field 'actors'
15:32:15.700 INFO  a.m.m.c.a.r.NumericalFeatureRule$ - generated `number` feature for item field popularity in the range 0.6..967.351
15:32:15.704 INFO  a.m.m.c.a.r.NumericalFeatureRule$ - generated `number` feature for item field vote_avg in the range 3.2..8.7
15:32:15.705 INFO  a.m.m.c.a.r.NumericalFeatureRule$ - generated `number` feature for item field release_date in the range 3.18816E8..1.56168E9
15:32:15.707 INFO  a.m.m.c.a.r.NumericalFeatureRule$ - generated `number` feature for item field budget in the range 0.0..3.8E8
15:32:15.708 INFO  a.m.m.c.a.r.NumericalFeatureRule$ - generated `number` feature for item field vote_cnt in the range 15.0..30232.0
15:32:15.709 INFO  a.m.m.c.a.r.NumericalFeatureRule$ - generated `number` feature for item field runtime in the range 3.0..242.0
15:32:15.719 INFO  a.m.m.c.a.rules.StringFeatureRule - field writer is not looking like a categorial value, skipping
15:32:15.726 INFO  a.m.m.c.a.rules.StringFeatureRule - field tags is not looking like a categorial value, skipping
15:32:15.728 INFO  a.m.m.c.a.rules.StringFeatureRule - field director is not looking like a categorial value, skipping
15:32:15.730 INFO  a.m.m.c.a.rules.StringFeatureRule - field title is not looking like a categorial value, skipping
15:32:15.731 INFO  a.m.m.c.a.rules.StringFeatureRule - item field genres has 19 distinct values, generated 'string' feature with index encoding for top 11 items
15:32:15.734 INFO  a.m.m.c.a.rules.StringFeatureRule - field actors is not looking like a categorial value, skipping
15:32:15.735 INFO  a.m.m.c.a.rules.RelevancyRule$ - skipped generating relevancy feature: non_zero=0 min=Some(0.0) max=Some(0.0)
15:32:15.851 INFO  ai.metarank.main.Main$ - My job is done, exiting.

Process finished with exit code 0
```

## Supported heuristics

Metarank has multiple sets of heuristics to generate feature configuration, toggled by the `--ruleset` CLI option:
* stable: a default one, ruleset with less agressive heuristics, proven to be safe in production use.
* all: generates all features it can, even the problematic ones (like CTR, which may introduce biases).

The following `stable` heuristics are supported:
* **Numeric**: all numerical item fields are encoded as a [number](../configuration/features/scalar.md#numerical-extractor) feature.
So for a numeric field `budget` describing a movie budget in dollars, it will generate the following feature extractor defitition:
```yaml
- source: item.budget
  type: number
  name: budget
  scope: item
```
* **String**: string item fields with low-cardinality are encoded as a [string](../configuration/features/scalar.md#string-extractors) feature.
So movie genres field is a good candidate for this type of heuristic due to its low cardinality:
```yaml
- name: genres
  type: string
  source: item.genres
  scope: item
  encode: index
  values:
  - drama
  - comedy
  - thriller
  - action
  - adventure
  - romance
  - crime
  - science fiction
  - fantasy
  - family
  - horror
```
* **InteractedWith**: all interaction over low-cardinality fields are translated to [interacted_with](../configuration/features/user-session.md#interacted-with) feature.
So if a user clicked on an item with horror genre, other horror movies may get extra points:
```yaml
- name: click_genres
  type: interacted_with
  scope: user
  interaction: click
  field: item.genres
```
* **Relevancy**: if rankings with non-zero relevancy are present, then a feature [relevancy](../configuration/features/relevancy.md) is built:
```yaml
- name: relevancy
  type: relevancy
```

* **Vector**: all numerical vectors are transformed into statically-sized features. Vectors of static size are passed through as-is, and variable-length vectors are reduced into a quadruplets of `[min, max, size, avg]` values:

```yaml
- name: embedding
  type: vector
  field: item.embedding // must be a singular number or a list of numbers
  scope: item
  # which reducers to use. optional. Default: [min, max, size, avg]
  reduce: [vector16]
```

The `all` ruleset contains all `stable` heuristics with an addition of a couple of extra ones:
* **Rate**: For all interaction types a [rate](../configuration/features/counters.md#rate) feature is generated over multiple
typical time windows:
```yaml
- name: click_rate
  type: rate
  top: click
  bucket: 1d
  bottom: impression
  scope: item
  periods:
  - 3
  - 7
  - 14
  - 30
```
* **InteractionCount**: all interaction types are wrapped into [interaction_count](../configuration/features/counters.md#interaction-counter) feature.
```yaml
- name: count_click
  type: window_count
  bucket: 1d
  scope: item
  interaction: click
  periods:
  - 3
  - 7
  - 14
  - 30
```

## Why stable ruleset has no counters?

The main difference between two rulesets is the lack of `rate`/`window_count` features, which is made deliberately:
* `rate`/`window_count` features usually introduce a popularity bias to the final ranking: as people tend to click more
on popular items, ML model may attempt to always put popular items on top just because they're popular.
* this behavior may be not that bad from the business KPI standpoint, but may make your ranking more static and 
less affected by past visitor actions.