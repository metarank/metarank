# ML Feature extractors

Most common learn-to-rank tasks usually have typical shared set of ML features. As long as you follow the 
[ingestion event schema](../event-schema.md), Metarank tries to automate creation of these features for you.

## Mapping input events into ML features

When Metarank receives a stream of events (both online during inference, and offline while training), it joins them together
into a single view of visitor click chain:
* For each ranking event, we do a per-item join of item metadata events (and also pull user metadata events)
* All the interaction events like clicks and purchases are also joined together

So each feature extractor has full access to complete view of the click chain. Then a sequence of differently scoped
extractors take this view as an input and emit feature values in the following order:
* item: uses only item metadata as a source
* session: session-specific values
* interaction: ones from interaction events

## Configuration

All the feature extractors have a set of common fields:
* `name`: *required*, *string*. Feature name, should be unique across the whole config.
* `refresh`: *optional*, *time*, default value is specific to the extractor. How frequently this feature is updated.
* `ttl`: *optional*, *time*, default: *90d* (3 months). How long should this feature store it's value.
* `scope`: *optional*. See the [Scoping chapter](feature-extractors.md#scoping) for more information.

### Scoping

Metarank uses scopes for computing and storing feature values. Feature values are stored not as atomic
values but as a time-based changelog, where *scope* is a unique key for this particular
instance of the ML feature.

Metarank has four predefined types of scopes:

* *global*: the feature is scoped globally , for example, air temperature outside the server room.
* *item*: take the item id for a feature changelog, for example: an item popularity.
* *user*: use user id as a unique id, for example: number of past sessions.
* *session*: use session id as a unique id, for example: number of items in a cart right now.

Scoping is also used for optimizing feature grouping while doing inference and model training:
we compute all the ML features defined in the configuration for all candidate items, but some of the features are
constant for the full ranking: if you have a site-global feature of air temperature outside, then it will be
the same for all the items in the ranking, so no need to repeat the computation again.

While building training data for the offline bootstrapping stage, this optimization technique can save a lot of raw processing power.

### Scoping example

All ML feature extractors in Metarank have a `scope` option in their configuration. For example:

```yaml
- name: popularity
  type: number
  field: item.popularity // must be a number
  scope: item
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

The `scope: item` means that the extracted popularity field from item metadata should be stored by an item identifier key.

## Feature types

### Generic feature extractors
* [number](features/scalar.md#boolean-and-numerical-extractors): uses a raw numerical field value as a feature.
* [vector](features/scalar.md#vector-extractor): reduces a variable length number vector to a fixed size value array.
* [boolean](features/scalar.md#boolean-and-numerical-extractors): uses a raw boolean field as a 1 or 0 feature value.
* [string](features/scalar.md#string-extractors): uses a raw string or list<string> field as an input and does a one-hot encoding of it.
* [word_count](features/generic.md#word-count): how many words are in a string field.
* [relative_number](features/generic.md#relative-number): scales a numerical field to make it fit 0..1 range.
* [list_size](features/generic.md#list-size): size of string or numerical list.
* [time_diff](features/generic.md#time-difference): difference in seconds between current timestamp and the numerical field value.
* [field_match](features/text.md#field_match): match ranking field over item fields.

### User session feature extractors
* [ua/platform](features/user-session.md#user-agent-field-extractor): a one-hot encoded platform (mobile, desktop, tablet).
* [ua/os](features/user-session.md#user-agent-field-extractor): a one-hot encoded OS (ios, android, windows, linux, macos, chrome os).
* [ua/browser](features/user-session.md#user-agent-field-extractor)* a one-hot encoded browser (chrome, firefox, safari, edge).
* [interacted_with](features/user-session.md#interacted-with): for the current item, did this visitor have an interaction with other item with the same field.
* *[coming soon]* ip_country: a GeoIP-based country.
* *[coming soon]* ip_city: a GeoIP-based city.
* [referer_medium](features/user-session.md#referer): a source of traffic for this customer.
* *[coming soon]* session_length: length of the current visitor session in seconds.
* *[coming soon]* session_count: number of total sessions tracked for this customer.


### Ranking feature extractors
* [relevancy](features/relevancy.md#ranking): use a supplied per-product relevancy from the rerank request.
* [position](features/relevancy.md#position): Item position in ranking.
* [diversity](features/diversity.md): Search results diversification.

### Neural LLM extractors
* [biencoder](features/neural.md): run a bi-encoder LLM model to compute semantic similarity between ranking and item fields.

### Interaction feature extractors
* [interaction_count](features/counters.md#interaction-counter): number of interaction made within this session.
* [window_event_count](features/counters.md#windowed-counter): sliding window count of interaction events.
* [rate](features/counters.md#rate): rate of interaction events of type A over interaction events of type B. Useful for CTR/CVR rates.

### Date and time
* [local_time](features/datetime.md#local_time-extractor): map local visitor date-time to catch seasonality.
* [item_age](features/datetime.md#item_age): how much time passed since the item was updated?