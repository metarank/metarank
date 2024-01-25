# Generic feature extractors

## Word count

Sometimes it can be useful to get the word length of a field, especially for models designed to personalize different types of content.
You can use the `word_count` feature extractor to get the length of a string field with the following config:

```yaml
- name: title_length
  type: word_count
  scope: item
  field: item.title // must be a string
```

## Relative number

> Update, v0.7.x: relative_number is deprecated and removed. Both XGBoost and LightGBM natively support this out of the box for all numeric features, so please use the [number](scalar.md#numerical-extractor) feature.   

## List size

Counts the number of items in a string or numerical list. Example:
```yaml
- name: toggled_filters_count
  type: list_size
  field: filters
  source: item
```

