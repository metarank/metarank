# Text based extractors

## field_match

An extractor which can match a field from ranking event over an item field. In practice, it can be useful in search
related tasks, when you need to match a search query over multiple separate fields in document, like title-tags-category.

Field match extractor supports the following matching methods:
* ngram: split item/query fields to N-grams and compute intersection over union score
* term: use Lucene to perform language specific tokenization
* bert: build LLM embeddings for item/query fields and compute a distance between them

### Dataset example

Given this metadata event:
```json
{
  "event": "item",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "item": "item1", 
  "timestamp": "1599391467000", 
  "fields": [
    {"name": "title", "value": "red socks"},
    {"name": "category", "value": "socks"},
    {"name": "brand", "value": "buffalo"},
    {"name": "description", "value": "lorem ipsum dolores sit amet"}
  ]
}
```

And a following ranking event:
```json
{
  "event": "ranking",
  "id": "81f46c34-a4bb-469c-8708-f8127cd67d27",
  "timestamp": "1599391467000",
  "user": "user1",
  "session": "session1",
  "fields": [
      {"name": "query", "value": "sock"}
  ],
  "items": [
    {"id": "item3"},
    {"id": "item1"},
    {"id": "item2"} 
  ]
}
```

### Ngram matching

With the following config file snippet you can do a per-field matching of `ranking.query` field over `item.title` field of
the items in the ranking with 3-grams:
```yaml
- name: title_match
  type: field_match
  itemField: item.title // must be a string
  rankingField: ranking.query // must be a string
  method:
    type: ngram // for now only ngram and term are supported
    language: en // ISO-639-1 language code
    n: 3
  refresh: 0s // optional, how frequently we should update the value, 0s by default
  ttl: 90d // optional, how long should we store this field
```

### Term matching 

In a similar way you can do the same with term matching:
```yaml
- name: title_match
  type: field_match
  itemField: item.title // must be a string
  rankingField: ranking.query // must be a string
  method:
    type: term // for now only ngram and term are supported
    language: en // ISO-639-1 language code
```

Both term and ngram matching methods leverage Lucene for text analysis and support the following set of languages:
- *generic*: no language specific transformations
- *en*: English
- *cz*: Czech
- *da*: Danish
- *nl*: Dutch
- *et*: Estonian
- *fi*: Finnish
- *fr*: French
- *de*: German
- *gr*: Greek
- *it*: Italian
- *no*: Norwegian
- *pl*: Polish
- *pt*: Portuguese
- *es*: Spanish
- *sv*: Swedish
- *tr*: Turkish
- *ar*: Arabic
- *zh*: Chinese
- *ja*: Japanese


Both term and ngram method share the same approach to the text analysis:
* text line is split into terms (using language-specific method)
* stopwords are removed
* for non-generic languages each term is stemmed
* then terms/ngrams from item and ranking are scored using intersection/union method.

### BERT LLM embedding similarity

Then with the following config snippet we can compute a cosine distance between title and query embeddings:

```yaml
- type: field_match
  name: title_query_match
  rankingField: ranking.query
  itemField: item.title
  distance: cos # optional, default cos, options: cos/dot 
  method:
    type: bert # the only one supported for now
    model: sentence-transformer/all-MiniLM-L6-v2 # the only one supported now
```

