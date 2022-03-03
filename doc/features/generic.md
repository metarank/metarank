# Generic feature extractors

## Word count

Sometimes it can be useful to get the word length of a field, especially for models designed to personalize different types of content.
You can use the `word_count` feature extractor to get the length of a string field with the following config:

```yaml
- name: title_length
  type: word_count
  field: metadata.title
```

## Relative number
More advanced feature type, which can scale numerical feature using different methods. Example config for a static scaling with 
predefined min and max values and log transformation:
```yaml
- name: price
  type: relative_number
  method:
    type: minmax
    min: 0
    max: 100   
  field: price
  source: item
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
  field: price
  source: item
```

## List size

Counts the number of items in a string or numerical list. Example:
```yaml
- name: toggled_filters_count
  type: list_size
  field: filters
  source: item
```

## Time difference

Computes the time difference in seconds between time now and a numerical field value. 
Field value should be a *unixtime*: number of seconds passed from 1970-01-01 00:00:00 in UTC timezone. 
Example:
```yaml
- name: age
  type: list_size
  field: created_at
  source: item
```