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

    def generate_request(**kwargs):
        return {
            "query": {"wildcard": {"name": f"*"}},
            "rescore": {
                "window_size": 600,
                "ml-rescore-v0": {
                    "score_mode": "replace",
                    "type": "ranking",
                    "name": "item_id_half_plus_two",
                    "domain": model_domain,
                    "itemid_field": "itemId1",
                    "context": {
                        "query": ["abc"],
                        "some-key": ["some-value"]
                    },
                    **kwargs,
                }
            }
        }

    request_bodies = [
        generate_request(domain="unknown:1234"),
        generate_request(name="unknown"),
        generate_request(type="unknown"),
        generate_request(itemid_field="unknown"),
        generate_request(score_mode="unknown"),
    ]

    for req in request_bodies:
        response = r.post(
            f"http://{es_host}:9200/{index}/_search",
            json=req
        )

        assert response.status_code // 100 == 4, f"""
        Failed to recieved a 400 response with bad input
        parameters!

        request:
        {req}

        status_code:
        {response.status_code}

        body:
        {response.json()}
        """

