# Semantic similarity

`semantic` is a content recommendation model, which computes item similarity only based on a difference between neural embeddings of items.

This model is useful for solving a cold-start problem of recommendations, as it requires no user feedback.

## Configuration

```yaml
- type: semantic
  encoder:
    type: bert
    model: sentence-transformer/all-MiniLM-L6-v2
  itemFields: [title, description]
```

* itemFields: fields which should be used for embedding
* encoder: a method of computing embeddings

Metarank has quite limited support for embeddings:
* `bert` type of embeddings only support a `sentence-transformer/all-MiniLM-L6-v2` model from HuggingFace
* `csv` type of embeddings allows loading a custom pre-made dictionary. 

```yaml
- type: semantic
  encoder:
    type: csv
    path: /opt/dic.csv
  itemFields: [title, description]
```

A dictionary should be a comma-separated CSV-formatted file, where:
* 1st column is product id
* 2 till N+1 columns - float values for N-dimentional embedding

Example:
```
p1,1.0,2.0,3.0
p2,2.0,1.5,1.0
```