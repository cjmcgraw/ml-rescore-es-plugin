#! /usr/bin/env python
from uuid import uuid4 as uuid
import argparse
import requests as r
import logging as log
import random
import string
import math
import time
import sys

import helpers

log.basicConfig(level=log.DEBUG, stream=sys.stdout)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument("--es-host", required=True)
    p.add_argument("--index", required=True)
    p.add_argument("--model-domain", default="item-ranker:8500")
    args = p.parse_args()
    es_host = args.es_host
    index = args.index
    model_domain = args.model_domain
    
    log.info("""
    first we want to setup the killswitch to clear out all successes and errors

    We do this by every 10 seconds, sending in one request that allows it to reset
    its known errors.
    """)
    for _ in range(10):
        response = r.post(
        f"http://{es_host}:9200/{index}/_search",
        json={
                "query": {"wildcard": {"name": "a*"}},
                "rescore": {
                    "window_size": 600,
                    "mlrescore-v2": {
                        "score_mode": "replace",
                        "type": "ranking",
                        "name": "item_ranker",
                        "model_name": "item_ranker_testing_model",
                        "domain": model_domain,
                        "itemid_datatype": "uint32",
                        "itemid_field": "itemId1",
                    },
                },
            },
        )

        helpers.check_request_okay(response)
        time.sleep(10)

    log.info("""
    Now we have the killswitch in a known state with a specific number of requests
    lets run some successes and ensure it works
    """)

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
                            "name": "item_ranker",
                            "model_name": "item_ranker_testing_model",
                            "domain": model_domain,
                            "itemid_datatype": "uint32",
                            "itemid_field": itemid_field,
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

            def get_expected_score(record):
                return len(str(record[itemid_field])) * math.e

            actualScores = [round(r['_score'], 4) for r in raw_data]
            expectedScores = [round(float(get_expected_score(r)), 4)  for r in data]
            assert all(x == y for x, y in zip(expectedScores, actualScores)), f"""
                Failed to assert that the scores were the expected item id inversed

                expectedScores:
                {expectedScores}

                actualScores:
                {actualScores}
            """


    log.info("""
    Now that we've run some successes are know they worked. We are going to run atleast
    as many errors and that should cause the killswitch to trigger.
    """)
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
                            "name": "item_ranker",
                            "model_name": "item_ranker_testing_model",
                            "domain": "unknown_domain:50051",
                            "itemid_datatype": "uint32",
                            "itemid_field": itemid_field,
                        },
                    },
                },
            )


    log.info("The killswitch should be active now. Lets confirm it is")
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
                            "name": "item_ranker",
                            "model_name": "item_ranker_testing_model",
                            "domain": model_domain,
                            "itemid_field": itemid_field,
                        },
                    },
                },
            )

            assert response.status_code == 429, f"""
            Expected killswitch to be active and fail requests for the next
            few minutes.
            """

    log.info("we need to sleep for 60 seconds while the kill switch coolsdown")
    time.sleep(60 + 1)
    log.info("finished sleeping. Now checking that the killswitch has deactivated")
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
                            "name": "item_ranker",
                            "model_name": "item_ranker_testing_model",
                            "domain": model_domain,
                            "itemid_field": itemid_field,
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

            def get_expected_score(record):
                return len(str(record[itemid_field])) * math.e

            actualScores = [round(r['_score'], 4) for r in raw_data]
            expectedScores = [round(float(get_expected_score(r)), 4)  for r in data]
            assert all(x == y for x, y in zip(expectedScores, actualScores)), f"""
                Failed to assert that the scores were the expected item id inversed

                expectedScores:
                {expectedScores}

                actualScores:
                {actualScores}
            """

