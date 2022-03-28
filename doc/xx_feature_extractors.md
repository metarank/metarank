# ML Feature extractors

Most common learn-to-rank tasks usually have typical shared set of ML features. As long as you follow the 
[ingestion event schema](xx_event_schema.md), Metarank tries to automate creation of these features for you.

## Mapping input events into ML features

When Metarank receives a stream of events (both online during inference, and offline while training), it joins them together
into a single view of visitor click chain:
* For each ranking evente, we do a per-item join of item metadata events
* All the interaction events like clicks and purchases are also joined together

So each feature extractor has full access to complete view of the click chain. Then a sequence of differently scoped
extractors take this view as an input and emit feature values in the following order:
* item: uses only item metadata as a source
* session: session-specific values
* interaction: ones from interaction events

## Configuration

All the feature extractors have a set of common fields:
* `name`: *required*, *string*. Feature name, should be unique across the whole config.
* `refresh`: *optional*, *time*, default: *0s* (realtime). How frequently this feature is updated.
* `ttl`: *optional*, *time*, default: *90d* (3 months). How long should this feature store it's value.

## Feature types

### Generic feature extractors
* [number](features/scalar.md#boolean-and-numerical-extractors): uses a raw numerical field value as a feature.
* [boolean](features/scalar.md#boolean-and-numerical-extractors): uses a raw boolean field as a 1 or 0 feature value.
* [string](features/scalar.md#string-extractors): uses a raw string or list<string> field as an input and does a one-hot encoding of it.
* [word_count](features/generic.md#word-count): how many words are in a string field.
* [relative_number](features/generic.md#relative-number): scales a numerical field to make it fit 0..1 range.
* [list_size](features/generic.md#list-size): size of string or numerical list.
* [time_diff](features/generic.md#time-difference): difference in seconds between current timestamp and the numerical field value.
* [field_match](features/text.md): match ranking field over item fields.

### User session feature extractors
* [ua/platform](/doc/features/user-session.md#user-agent-field-extractor): a one-hot encoded platform (mobile, desktop, tablet).
* [ua/os](features/user-session.md#user-agent-field-extractor): a one-hot encoded OS (ios, android, windows, linux, macos, chrome os).
* [ua/browser](features/user-session.md#user-agent-field-extractor)* a one-hot encoded browser (chrome, firefox, safari, edge).
* [interacted_with](features/user-session.md#interacted-with): for the current item, did this visitor have an interaction with other item with the same field.
* *[coming soon]* ip_country: a GeoIP-based country.
* *[coming soon]* ip_city: a GeoIP-based city.
* *[coming soon]* ref_source: a source of traffic for this customer.
* *[coming soon]* session_length: length of the current visitor session in seconds.
* *[coming soon]* session_count: number of total sessions tracked for this customer.


### Ranking feature extractors
* [relevancy](features/relevancy.md#ranking): use a supplied per-product relevancy from the rerank request.
* [items_count](features/relevancy.md#items-count): number of items in the ranking event.

### Interaction feature extractors
* [interaction_count](features/counters.md#interaction-counter): number of interaction made within this session.
* [window_event_count](features/counters.md#windowed-counter): sliding window count of interaction events.
* [rate](features/counters.md#rate): rate of interaction events of type A over interaction events of type B. Useful for CTR/CVR rates.
* *[coming soon]* text_match: matches a field from ranking event over a field of item metadata.

### Date and time
* [local_time](features/datetime.md): map local visitor date-time to catch seasonality.
* [item_age](features/datetime.md): how much time passed till item was updated?