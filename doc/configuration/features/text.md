# Text based extractors

## field_match

An extractor which can match a field from ranking event over an item field. In practice, it can be useful in search
related tasks, when you need to match a search query over multiple separate fields in document, like title-tags-category.

Field match extractor supports the following matching methods:
* BM25: a Lucene-specific BM25 score between ranking and item fields (for example, between query and item title)
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
### BM25 score

As BM25 formula requires term frequencies and some other index statistics, using BM25 requires you to build the term-freq dictionary beforehead, [see the CLI `termfreq` docs](../../cli.md#bm25-term-frequencies-dictionary) on how to do it.

Having the `term-freq.json` file in hand, you can then configure Metarank to compute BM25 score between ranking field (for example, `query`) and item field (like `title`):

```yaml
  - name: title_match
    type: field_match
    rankingField: ranking.query
    itemField: item.title
    method:
      type: bm25
      language: english
      termFreq: "/path/to/term-freq.json"
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

### Transformer LLM embedding similarity

Then with the following config snippet we can compute a cosine distance between title and query embeddings:

```yaml
- type: field_match
  name: title_query_match
  rankingField: ranking.query
  itemField: item.title
  distance: cos # optional, default cos, options: cos/dot 
  method:
    type: transformer
    model: metarank/all-MiniLM-L6-v2
    dim: 384 # required, dimensionality of the embedding
    itemFieldCache: /path/to/item.embedding # optional, pre-computed embedding cache for items 
    rankingFieldCache: /path/to/query.embedding # optional, pre-computed embedding cache for rankings
```

Metarank supports two embedding methods:
* `transformer`: ONNX-encoded versions of the [sentence-transformers](https://sbert.net/docs/pretrained_models.html) models. See the [metarank HuggingFace namespace](https://huggingface.co/metarank) for a list of currently supported models.
* `csv`: a comma-separated file with precomputed embeddings, where first row is source sentence. Useful for externally-generated embeddings with platforms like OpenAI and Cohere.

For `transformer` models, Metarank supports fetching model directly from the HuggingFace Hub, or loading it from a local dir, depending on the model handle format:
* `namespace/model`: fetch model from the HFHub
* `file:///<path>/<to>/<model dir>`: load ONNX-encoded embedding model from a local file.

#### Using CSV cache of precomputed embeddings

In some performance-sensitive cases you don't want to compute embeddings in realtime, but only use offline precomputed ones. This is possible with the `csv` `field_match` method:
```yaml
- type: field_match
  name: title_query_match
  rankingField: ranking.query
  itemField: item.title
  distance: cos # optional, default cos, options: cos/dot 
  method:
    type: csv
    dim: 384
    itemFieldCache: /path/to/item.embedding
    rankingFieldCache: /path/to/query.embedding
```

In this case Metarank will load item and query embeddings from a CSV file in the following format:

itemFieldCache:

```
item1,0,1,2,3,4,5
item2,5,4,3,2,1,9
item3,1,1,1,1,1,1
```

rankingFieldCache:
```
bananas,0,1,2,3,4,5
red socks,5,4,3,2,1,9
stone,1,1,1,1,1,1
```
* when both query and item embeddings are present, then `field_match` will produce a cosine distance between them.
* when at least one of the embeddings is missing, then `field_match` with `csv` method will produce a `nil` missing value. 
* when at least one of the embeddings is missing, then `field_match` with `transformer` method will compute the embedding real-time.
