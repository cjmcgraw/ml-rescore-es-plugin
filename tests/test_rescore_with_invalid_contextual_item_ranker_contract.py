#! /usr/bin/env python
from uuid import uuid4 as uuid
import argparse
import requests as r
from requests_toolbelt.utils import dump
import logging as log
import random
import string
import math
import sys

import helpers

log.basicConfig(level=log.DEBUG, stream=sys.stdout)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument("--es-host", required=True)
    p.add_argument("--index", required=True)
    p.add_argument("--model-domain", default="contextual-item-ranker:8500")
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
                        "mlrescore-v2": {
                            "score_mode": "replace",
                            "type": "ranking",
                            "name": "contextual_item_ranker",
                            "model_name": "contextual_item_ranker_testing_model",
                            "domain": model_domain,
                            "itemid_datatype": "string",
                            "itemid_field": itemid_field,
                            "context": {
                                "key5": ["{}[]\\|;:\"',./<>?"],
                            },
                        },
                    },
                },
            )

            assert response.status_code == 422, f"""
            Expected a 422 (Unprocessable Entity) status code when the context
            is known invalid. 

            status_code:
            {response.status_code}

            dump:
            {dump.dump_all(response).decode('utf-8')}

            """


