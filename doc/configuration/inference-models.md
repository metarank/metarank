# Inference models

Inference models allow to re-rank search results using pre-train LLMs (Large Language Models). You can use Metarank's pre-built models or provide your own. 
At the moment `cross-encoder` and `bi-encoder` model types are supported.

Metarank uses [ONNX](https://onnx.ai/) for loading models and can handle referencing models from [HuggingFace](https://huggingface.co/metarank) or local files. 
In the `model` section of the configuration you can specify either a HuggingFace handle, e.g. `metarank/ce-msmarco-MiniLM-L6-v2metarank/ce-msmarco-MiniLM-L6-v2` or a local file `file://local-folder/model.onnx`

```yaml
inference:
  msmarco: # name of the model
    type: cross-encoder # model type
    model: metarank/ce-msmarco-MiniLM-L6-v2 # model source
```

## Cross-encoders

Cross-encoder LLMs are using the semantic power of LLMs to provider a better order for your results. Typically, cross-encoder LLM is much more precise than bi-encoder.
With `vocabFile` option you can specify a file containing text vocabulary. 
With `cache` option you can specify a csv file containing `query`, `item` and `score` columns. Providing such a file can significantly speed up the training process.

```yaml
inference:
  msmarco: # name of the model
    type: cross-encoder # model type
    model: metarank/ce-msmarco-MiniLM-L6-v2 # model source
    vocabFile: file://local/vocab.txt # optional
    cache: file://local/cache.csv # optional, 
```

## Bi-encoders

With `vocabFile` option you can specify a file containing text vocabulary.
`itemFieldCache` and `rankingFieldCache` files can be used to significantly speed up the training process bby providing item and ranking embeddings upfront.
The `itemFieldCache` file structure is as follows:
```csv
query,embedding
socks,1231313131
```

The `rankingFieldCache` file structure is as follows:
```csv
itemId, embedding
1,1231313131
```

```yaml
inference:
  msmarco: # name of the model
    type: bi-encoder # model type
    model: metarank/ce-msmarco-MiniLM-L6-v2 # model source
    vocabFile: file://local/vocab.txt # optional
    itemFieldCache: file://local/items.csv # optional
    rankingFieldCache: file://local/rankings.csv # optional
    cache: file://local/cache.csv # optional, 
```