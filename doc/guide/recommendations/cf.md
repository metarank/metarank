# Collaborative-Filtering recommendations

In this guide we will show how you can build a well-known "you may also like" style recommender:

## Preparing data

For a demo purposes we will use a public [Movielens](TODO) dataset of visitors interacting with movies. As Metarank expects both item metadata and interactions in a special format (see [Event Schema] reference for details), we are going to re-format the original dataset into the JSON format.

Metarank expects multiple types of input events to build recommendation model:
* Item metadata. Usually these events define item-specific information like title and price, but as CF recommendations are interaction-only, these can be omitted in our case.
* Ranking events. This type of events can be used for collecting implicit relevance feedback on rankings, but these are unused for CF recommendations.

The only 