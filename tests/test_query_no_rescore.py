#! /usr/bin/env python
from pprint import pformat
import requests as r
import argparse
import string
import logging as log
import sys

import helpers

log.basicConfig(level=log.DEBUG, stream=sys.stdout)

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument("--es-host", required=True)
    p.add_argument("--index", required=True)

    args = p.parse_args()
    es_host = args.es_host
    index = args.index

    for v in string.ascii_lowercase:
        log.info(f"running test for name starts with {v}")
        response = r.post(
            f"http://{es_host}:9200/{index}/_search",
            json={"query": {"wildcard": {"name": f"{v}*"}}},
        )
        helpers.check_request_okay(response)
        names = [
            record["_source"]["name"] for record in response.json()["hits"]["hits"]
        ]
        assert len(names) > 0, f"""expected more than 0 names that start with {v}"""
        assert all([n.lower().startswith(v) for n in names])
