# Search

In this series of guides we will go through typical cases of using Metarank to improve the relevance of your search engine.

There are two main approaches to search reranking:
* **Zero-shot**: using generic approaches not fine-tuned on your dataset and visitor behavior. This does not require any telemetry collection and is a good starting point.
* **Learn-to-Rank**: adapt ranking to the dataset and visitor behavior. Can yield better quality, with the drawback of requiring proper visitor analytics.

## Zero-shot re-ranking

If you're not familiar with concepts of re-ranking and Metarank, start with these intro guides to get better understanding about how things work:

* [Search re-ranking with cross-encoder LLMs](cross-encoders.md): How to use a general-purpose cross-encoder, pre-trained on MS-MARCO dataset to improve your ElasticSearch search relevance.
* TODO: Semantic search with sentence-transformers and Qdrant: setting up Metarank as an inference server for bi-encoders for semantic retrieval with vector search.

## Learn-to-Rank

* TODO: Setting up data collection
  * TODO: explicit and implicit relevance labels
* TODO: Configuring ranking factors
  * TODO: Automatic config generation based on your existing data
  * TODO: Personalization and tracking visitor profile


