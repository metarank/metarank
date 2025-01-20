# Semantic similarity

`semantic` is a content recommendation model, which computes item similarity only based on a difference between neural embeddings of items.

This model is useful for solving a cold-start problem of recommendations, as it requires no user feedback.

## Configuration

```yaml
- type: semantic
  encoder:
    type: bert
    model: metarank/all-MiniLM-L6-v2
    dim: 384 # embedding size
  itemFields: [title, description]
```

* itemFields: fields which should be used for embedding
* encoder: a method of computing embeddings

Metarank has quite limited support for embeddings:
* `bert` type of embeddings only supports ONNX-encoded models from sentence-transformers from HuggingFace
* `csv` type of embeddings allows loading a custom pre-made dictionary. 

```yaml
- type: semantic
  encoder:
    type: csv
    dim: 384 # embedding size
    path: /opt/dic.csv
  itemFields: [title, description]
```

A dictionary should be a comma-separated CSV-formatted file, where:
* 1st column is product id
* 2 till N+1 columns - float values for N-dimensional embedding

Example:
```
p1,1.0,2.0,3.0
p2,2.0,1.5,1.0
```