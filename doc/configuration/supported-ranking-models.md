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
    backend:
      type: xgboost # supported values: xgboost, lightgbm
      iterations: 100 # optional (default 100), number of interations while training the model
      seed: 0 # optional (default = random), a seed to make training deterministic
    weights: # types and weights of interactions used in the model training
      click: 1 # you can increase the weight of some events to hint model to optimize more for them
    features: # features from the previous section used in the model
      - foo
      - bar
#    selector: # optional set of selectors to filter events for this specific model
#      rankingField: source
#      value: search
```

* `backend`: *required*, *xgboost* or *lightgbm*, specifies the backend and it's configuration.
* `weights`: *required*, *list of string:number pairs*, specifies what interaction events are used for training. You can specify multiple events with different weights.
* `features`: *required*, *list of string*, features used for model training, see [Feature extractors](feature-extractors.md) documentation.
* `selector`: *optional*, *list of selectors*, a set of rules to filter which events should be accepted by this model.

### Interaction weight

Interactions define the way your users interact with the items you want to personalize, e.g. `click`, `add-to-wishlist`, `purchase`, `like`.

Interactions can be used in the feature extractors, for example to calculate the click-through rate and 
by defining `weight` you can control the optimization goal of your model: do you want to increase the amount of likes or purchases or balance between them.

You can define interaction by `name` and set `weight` for how much this interaction affects the model: 

```yaml
  click: 1.0
```

### Event selectors

When serving multiple models, there are cases when you need to separate ranking and interaction events per model. This is useful when your models have different contexts, e.g. you do personalized ranking for search results and recommendation results using the same Metarank installation, but utilizing different models. 

Metarank supports `selector` configuration that can be used to route your events to correct model or drop events in certain scenarios.

Metarank supports the following event selectors:
* **Accept selector**. Enabled by default to accept all events, if no selectors are defined. 
```yaml
selector:
  accept: true # true = accept all, false = reject all
```
* **Field selector**. Accepts event when it has a specific string (or string-list) field defined for a ranking event. For example:
```yaml
selector:
  rankingField: source
  value: search
```
The filter above will accept only events that have the `source=search` field defined in the `fields` section of the event:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "source", "value": "search"}
  ],
  "items": [
    {"id": "item1", "fields": [{"name": "relevancy", "value": 1.0}]},
  ]
}
```
* **Sampling selector**: randomly accept or drop an event, depending on the acceptance ratio:
```yaml
selector:
  ratio: 0.5
```
The sampling selector above will accept only half of events randomly.
* **Max interaction position selector**: only accept click-through events when interaction position is not too high/low. Can be useful to exclude visitor sessions with discovery-style browsing behavior, or too short rankings.
```yaml
selector:
  maxInteractionPosition: 10
  minInteractionPosition: 3 # both fields are optional
```

* **Ranking length selector**: only accept click-through events with number of items within a defined range.

```yaml
selector:
  minItems: 10 # so there should be at least 10 items in the ranking
  maxItems: 20 # both min and max are optional
```

* **AND/OR/NOT selector**: combine multiple selectors within a single boolean combination:
```yaml
selector:
  and:
    - rankingField: source
      value: search
    - or:
        - rankingField: segment
          value: test
        - ratio: 0.5
        - not:
            accept: false
    
```
AND and OR selectors take a list of nested selectors as arguments, NOT selector only takes a single selector argument.

### XGBoost and LightGBM backend options

* *iterations*: *optional*, *number*, default: *100*, number of trees in the model.
* *learningRate*: *optional*, *number*, default: *0.1*, higher the rate - faster training - less precise model.
* *ndcgCutoff*: *optional*, *number*, default: *10*, only N first items may affect the NDCG.
* *maxDepth*: *optional*, *number*, default: *8*, the depth of the tree.
* *seed*: *optional*, *string* or *number*, default: *random* to make model training deterministic.
* *sampling*: *optional*, default: 0.8, fraction of features used to build a tree, useful to prevent over-fitting.

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