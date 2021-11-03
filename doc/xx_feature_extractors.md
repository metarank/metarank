# ML Feature extractors

Most common learn-to-rank tasks usually have typical shared set of ML features. As long as you follow the 
[ingestion event schema](xx_event_schema.md), Metarank tries to automate creation of these features for you.

## Mapping input events into ML features

When Metarank receives a stream of events (both online during inference, and offline while training), it joins them together
into a single view of visitor click chain:
* For each impression events, we do a per-item join of item metadata events
* All the interaction events like clicks and purchases are also joined together

So each feature extractor has full access to complete view of the click chain. Then a sequence of differently scoped
extractors take this view as an input and emit feature values in the following order:
* item: uses only item metadata as a source
* session: session-specific values
* interaction: 

## Feature types

Generic:
* number: uses a raw numerical field value as a feature
* boolean: uses a raw boolean field as a 1 or 0 feature value
* string: uses a raw string or list<string> field as an input and does a one-hot encoding of it
* relative_number: scales a numerical field to make it fit 0..1 range
* word_count: how many words are in a string field?
* list_size: size of string or numerical list
* time_diff: difference in seconds between current timestamp and the numerical field value

Session:
* ua_platform: a one-hot encoded mobile/desktop/tablet
* ua_os: a one-hot encoded OS like iOS/Android/Windows/etc.
* ua_browser: a one-hot encoded browser like Firefox/Chrome/Edge/etc.
* ip_country: a GeoIP-based country
* ip_city: a GeoIP-based city
* interaction_count: number of interaction made within this session
* ref_source: a source of traffic for this customer
* session_length: length of the current visitor session in seconds
* session_count: number of total sessions tracked for this customer
* interacted_with: for the current item, does this visitor had an interaction with other item with the same field?
* interaction_count: always increasing counter of interaction events
* 
Impression:
* items_count: number of items in the impression

Interaction features:
* window_event_count: sliding window count of interaction events
* text_match: matches a field from impression event over a field of item metadata
* rate: rate of interaction events of type A over interaction events of type B. Useful for CTR/CVR rates.


## Generic features

These features are applicable to any type of event and operate on it's fields.

### number

Extracts a numerical field from the event as-is. Can be used both with item, impression and interaction events. 

Config example for a price field taken from item metadata events:
```yaml
- name: price
  type: number
  source: price
```

### boolean

Maps a boolean value to a numerical value, so true = 1, and false = 0. Example:
```yaml
- name: availability
  type: boolean
  source: availability
```

### string

One-hot encoded mapping of string into a set of numbers. Example:
```yaml
- name: color
  type: string
  values: [red, green, blue]
  source: color
```
One-hot encoder with also add an extra value "other" at the end to catch all values not listed in the config. So for 
the color example above:
* for a field color=green
* features: `[color_red: 0, color_green: 1, color_blue: 0, color_other: 0]`

### relative_number

More advanced feature type, which can scale numerical feature using different methods. Example config for a static scaling with 
predefined min and max values and log transformation:
```yaml
- name: price
  type: relative_number
  method:
    type: minmax
    min: 0
    max: 100   
  source: price
```

Supported methods:
* *minmax*: uses `min` and `max` fields to scale
* *log_minmax*: uses `min` and `max` fields to scale, but the value is log-transformed before.
* *estimate_minmax*: using a sample of latest `pool_size` events (sampled with `sample_rate` rate), estimate 
min and max values used for scaling
* *estimate_histogram*: using a sample of latest `pool_size` events (sampled with `sample_rate` rate), use a histogram scaling
over `bucket_count` buckets. So for a price field from the example above, histogram scaling will translate absolute value
into a percentile over a sampled pool of values.

Estimate methods are useful for rough scaling of values, when you cannot easily define min and max:
* `estimate_minmax` should be used when the value can be linearly scaled, and there are no outliers
* `estimate_histogram` can handle skewed distributions and outliers, but has quantized output: there is only `bucket_count`
possible output values.

Example config for an `estimate_histogram`:
```yaml
- name: price
  type: relative_number
  method:
    type: estimate_histogram
    pool_size: 100 // for a pool size of 100
    sample_rate: 10 // we sample every 10th event in the pool 
    bucket_count: 5 // so value will be mapped to 0-20-40-60-80-100 percentiles
  source: price
```

### word_count

Counts number of words in a string field. Uses whitespace-based tokenization. Example:
```yaml
- name: title_words
  type: word_count
  source: title
```

### list_size
Counts number of items in a string or numerical list. Example:
```yaml
- name: toggled_filters_count
  type: list_size
  source: filters
```

### time_diff
Computes time difference in seconds between time now and numerical field value. Field value should be a unixtime: number of
seconds passed from 1970-01-01 00:00:00 in UTC timezone. Example:
```yaml
- name: age
  type: list_size
  source: created_at
```

## Session

These feature extractors operate on impression and interaction events, and mostly focused on past action history
of the visitor.

### ua_platform
A one-hot encoded mobile/desktop/tablet value extracted from the raw User-Agent field. Example:
```yaml
- name: platform
  type: ua_platform
  source: ua
```

So if your events have a field "User-Agent", this extractor can guess the platform of the visitor and one-hot encode it
to a set of three possible values: `mobile`, `desktop`, `tablet`. It will set all the values to 0 if it cannot properly
guess the platform.

### ua_os
Extracts OS from UA field and maps it into the following list:
* iOS
* Android
* Windows
* Mac OS X
* Chrome OS
* Linux
* Other

Example: 
```yaml
- name: os
  type: ua_os
  source: ua
```

### ua_browser
A one-hot encoded browser, extracted from the UA field. Mapped over the following possible values:
* Mobile Safari
* Chrome
* Chrome Mobile
* Safari
* Instagram
* Facebook
* Samsung Internet
* Edge
* Chrome Mobile iOS
* Firefox
* Other

Example:
```yaml
- name: browser
  type: ua_browser
  source: ua
```

### ip_country
Using a maxmind geoip database, estimates visitor's country over a predefined list of values. Example:
```yaml
- name: country
  type: ip_country
  values: [US, GB, DE]
  source: ip
```

### ip_city
The same as in `ip_country`, but for cities:
```yaml
- name: city
  type: ip_city
  values: ["New York", "Boston", "Berlin"]
  source: ip
```

### interaction_count
Counts number of interactions made within the current visitor session. Example, to count number of clicks made:
```yaml
- name: click_count
  type: interaction_count
  interaction: click
```

### ref_source
Using a HTTP Referer field and a snowplow's referer parser library, estimate a traffic source type over the following
values:
* search
* social
* email
* paid

Example:
```yaml
- name: traffic_source
  type: ref_source
  source: referer
```

### session_length

Number of seconds passed from the first event in the session, till now. Example:
```yaml
- name: session_seconds
  type: session_length
```

### session_count

Number of total sessions tracked for this customer. Example:
```yaml
- name: sessions
  type: session_count
```

### interacted_with
For the current item, does this visitor had an interaction with other item with the same field? Example:
```yaml
- name: clicked_color
  type: interacted_with
  impression: click
  field: color
```

For this example, Metarank will track all color field values for all items visitor clicked. And intersect this set
with per-item field values in the impression.

### interaction_count
Always increasing counter of interaction events within this session. Example:
```yaml
- name: click_count
  type: interaction_count
  interaction: click
```

## Impression

These feature extractors only operate over impression events.

### items_count
Number of items in the impression event. Example: 
```yaml
- name: displayed_items
  type: items_count
```


## Interaction

These feature extractors only operate over interaction events.

### window_count
A sliding window count of interaction events for a particular item. Example:
```yaml
- name: item_click_count
  type: window_count
  interaction: click
  bucket_size: 24h // make a counter for each 24h rolling window
  windows: [7, 14, 30, 60] // on each refresh, aggregate to 1-2-4-8 week counts
```

This feature is useful to maintain a rolling-window estimator of item event counts. Absolute event count
 is trivial to be implemented, but if you want to have a rolling window one, it requires some data wrangling.

### text_match
Matches a field from impression event over a field of item metadata. Example:
```yaml
- name: title_match
  type: text_match
  impression_field: query
  metadata_field: title
  method: 2gram  
```

The following methods are supported:
* <N>gram: split both fields to Ngrams and compute intersection to union ratio. N can be within [2, 4] range.
* word: number of words, intersection/union ratio

### rate
Rate of interaction events of type A over number of impressions for a specific item. Example:
```yaml
- name: ctr
  type: rate
  interaction: click
```
