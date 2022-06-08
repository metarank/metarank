# Supported ranking models

This document lists all the methods Metarank may use for ranking. It's the one defined in the `models.<name>.type` part
of [config file](sample-config.yml):
```yaml
models:
  default: 
    type: lambdamart 
```

## LambdaMART

LambdaMART is a Learn-to-Rank model, optimizing the [NDCG metric](https://en.wikipedia.org/wiki/Discounted_cumulative_gain). 
There is a [Lambdamart in Depth](https://softwaredoug.com/blog/2022/01/17/lambdamart-in-depth.html)
article by [Doug Turnbull](https://softwaredoug.com) describing all the details about how it works. In a simplified way,
LambdaMART in the scope of Metarank does the following:
1. Takes a ranking and some relevancy judgements over items as an input (judgements can be implicit, like clicks, or 
implicit like stars in movie recommendations)
2. All items in the ranking have a set of characteristics (ML features, like genre or CTR as an example)
3. A pair of items from the ranking is sampled.
4. ML model must be able to guess which item in this pair may have higher relevancy judgement.
5. Repeat over all pairs in the ranking.
6. Repeat over all the rankings in the dataset.

At the end, items with higher judgements should be ranked higher, making the resulting ranking more relevant.

In Metarank there are two supported library backends implementing this algorithm:
* XGBoost: [rank:pairwise](https://xgboost.readthedocs.io/en/stable/parameter.html) objective
* LightGBM: [lambdarank](https://lightgbm.readthedocs.io/en/latest/Parameters.html) objective

To configure the model, use the following snippet:
```yaml
  <model name>:
    type: lambdamart 
    path: /tmp/xgboost.model
    backend:
      type: xgboost # supported values: xgboost, lightgbm
      iterations: 100 # optional (default 100), number of interations while training the model
      seed: 0 # optional (default = random), a seed to make training deterministic
    weights: # types and weights of interactions used in the model training
      click: 1 # you can increase the weight of some events to hint model to optimize more for them
    features: # features from the previous section used in the model
      - foo
      - bar
```
* `path`: *required*, *string*, used to point metarank where to write model during training, and where to load it from 
during inference. It can also read-write it to/from S3-like filesystem.
* `backend`: *required*, *xgboost* or *lightgbm*, specifies the backend and it's configuration.
* `weights`: *required*, *list of string:number pairs*, specifies what interaction events are used for training. You can specify multiple events with different weights.
* `features`: *required*, *list of string*, features used for model training, see [Feature extractors](feature-extractors.md) documentation.

### XGBoost and LightGBM backend options

* *iterations*: *optional*, *number*, default: *100*, number of trees in the model.
* *learningRate*: *optional*, *number*, default: *0.1*, higher the rate - faster training - less precise model.
* *ndcgCutoff*: *optional*, *number*, default: *10*, only N first items may affect the NDCG.
* *maxDepth*: *optional*, *number*, default: *8*, the depth of the tree.
* *seed*: *optional*, *string* or *number*, default: *random* to make model training deterministic.

LightGBM also supports these specific options:
* *numLeaves*: *optional*, *number*, default: *16*, how many leaves the tree may have.

Please consult [LightGBM](https://lightgbm.readthedocs.io/en/latest/Parameters-Tuning.html) and 
[XGBoost](https://xgboost.readthedocs.io/en/stable/parameter.html) docs about tuning these parameters.

## Shuffle

A `shuffle` is a baseline model, which may be used in the a/b tests as a "worst-case" ranking scenario, when
the order of items is random. The shuffle model is configured in the following way:
```yaml
  <model name>:
    type: shuffle
    maxPositionChange: 5
```

* Parameter `maxPositionChange` controls the amount of randomness that shuffle can introduce in the original ranking. In 
other words, `maxPositionChange` sets how far away an item can drift from its original position.


## Noop

A `noop` is also a baseline model, which does nothing. The main purpose of this model to be a baseline of the original 
ranking sent to metarank during a/b tests. It's configured with the following snippet:
```yaml
  <model name>:
    type: noop
```

It has no options and does not do any modifications to the ranking, just bouncing it back as-is. 