#! /usr/bin/env python
from uuid import uuid4 as uuid
import argparse
import requests as r
import logging as log
import random
import string
import sys

import helpers

log.basicConfig(level=log.DEBUG, stream=sys.stdout)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument("--es-host", required=True)
    p.add_argument("--index", required=True)
    p.add_argument("--model-domain", default="half-plus-two-model:8500")
    args = p.parse_args()
    es_host = args.es_host
    index = args.index
    model_domain = args.model_domain

    for itemid_field in ['itemId1', 'itemId2', 'itemId3']:
        for v in string.ascii_lowercase:
            response = r.post(
            f"http://{es_host}:9200/{index}/_search",
            json={
                    "query": {"wildcard": {"name": f"{v}*"}},
                    "rescore": {
                        "window_size": 600,
                        "ml-rescore-v0": {
                            "score_mode": "replace",
                            "type": "ranking",
                            "name": "item_id_half_plus_two",
                            "domain": model_domain,
                            "itemid_field": itemid_field,
                            "context": {
                                "query": ["abc"],
                                "some-key": ["some-value"]
                            },
                        },
                    },
                },
            )

            helpers.check_request_okay(response)

            raw_data = response.json()['hits']['hits']
            data = [r['_source'] for r in raw_data]
            names = [r['name'] for r in data]
            assert all(
                [n.lower().startswith(v) for n in names]
            ), f"queried name didn't start with {v}"

            actualScores = [r['_score'] for r in raw_data]
            expectedScores = [(r[itemid_field] / 2) + 2  for r in data]
            assert all(x == y for x, y in zip(expectedScores, actualScores)), f"""
                Failed to assert that the scores were the expected item
                ids divided by 2, with two added!

                expectedScores:
                {expectedScores}

                actualScores:
                {actualScores}
            """




