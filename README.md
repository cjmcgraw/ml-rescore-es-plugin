# Elasticsearch Plugin: ml-grpc-rescore

This is an Elasticsearch [rescore](https://www.elastic.co/guide/en/elasticsearch/reference/7.9//filter-search-results.html#rescore) [plugin](https://www.elastic.co/guide/en/elasticsearch/reference/current/modules-plugins.html). The purpose of this plugin is to allow an external model to dyanmically score documents in real time, in a hyper critical production system, where the underlying machine learning model changes rapidly , and the documents are extremely volatile in state.

## But why?

The first question I always ask myself when I see a github repo like this. Why? What
problem is trying to be solved here? Why do I care? What is the purpose of this all. I
sometimes like to include a section here to describe the problem, and how this
repository solves it.

As I write this I am the original author of our recommender system, and content
discovery/serving mechanisms at [Accretive Technology Group](https://accretivetg.com/).
Our goal is to serve hundreds of millions of daily users, with the best recommendations
for approximately 1 million documents. But there are a few wrinkles. I will enumerate
them briefly:

1) We get a lot of traffic. Day and night we consistently get on the order of 1e5 hits
per second

2) We have a lot of affiliate sites. Around the order of 1e5 sites. Which are getting
taffic constantly.

3) We have hundreds of millions of absurdly diverse users who want a customized user experience. Each
with their own language, country, preferences, etc that needs to be custom tailored on
the fly.

4) We have extremely volatile, substantially diverse documents. All together there are around 1e6 documents. But most
documents change state from valid/invalid dozens of times a second. At any given time
only 1% of our document base is valid for scoring.

5) We need to be able to release dozens of ML models, and run a concerted AB test
between them to determine at any given moment which model is most effective. Allowing us
to objectively learn from our mistakes and finally approach a good solution.

Because of these challenges scoring topN per user context is not valid. First the
keyspace for the user's context is around 1e20 in size (before documents). This is only
knowable at query time because of GDPR concerns. Second since the documents are so
volatile and so sparse if we scored the topN for each user, we almost guaranteed would
have no pre-scored documents for each user. So prescoring is nearly out the window.
Third, each affiliate site has sublists of volatile documents. Caching across all
affiliates is very error prone and difficult.

The final question. How do we dynamically score these diverse users, against these
volatile documents using state of the art Machine Learning technology?

The answer. This plugin! 

tl;dr: The purpose of this plugin is relatively simple.  We want to rescore every 
document, against a known user context. Adding the user documents score into the 
elasticsearch score, then finally sorting by the aggregation of the two. 

## How does it work?

I am assuming if you've read this far you are already familiar with
[Elasticsearch](https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html)
and how it works. Assuming that we've implemented this plugin as a rescoring solution to
allow for dynamic machine learning model scoring.

First you need to run your query against es:

```
$ http POST "${es_host}:9200/${index}/_search" <<< \
        '{
            "query": {...}, 
            "rescore": { 
                "window_size": 100, 
                "ml-rescore-v0": {
                    "type": "ranking"
                    "name": "half_plus_two", 
                    "domain": "half-plus-two:8500",
                    "itemid_field": "itemId1",
                    "context": {
                        "some-key": "some-value", 
                        "another-key": "another-value"
                    }
                }
            }
        }'
```

Here is the definition of the parameters:

* `type` - the type of model. Generally `ranking` vs `retrieval`
* `name` - the name of the model. This is from the class you've implemented in the plugin
* `domain` - the `domain:port` that the model is available on. Currently only `TensorflowServing` models are implemente
* `itemid_field` - the field to use as the unique identifier for each item, this should be present in each document
* `context` - the context for the query. Including all meaningful fields you care about

ES will run the rescore during the collection phase, after the scoring phase before
returning to the master node for sorting. This allows each shard to run their own LRU
cache and gRPC pool.

the gRPC model is served externally to the ES cluster, but ideally co-located close
enough to allow for fast connections. The LRU cache can be tuned with increased
weight/size for best effect.

## Wiki: 

Please refer to wiki for usage, and how to easily change to fit your use case:

* [getting started](./wiki/0000-getting-started.md)
* [running locally](./wiki/0001-running-locally.md)
* [implementing your own model](./wiki/0002-implementing-your-own-model.md)
* [ranking vs retrieval](./wiki/0003-ranking-vs-retrieval.md)
* [how to monitor in production](./wiki/0004-how-to-monitor.md)
* [how to tune lru cache](./wiki/0005-how-to-tune-lru-cache.md)

