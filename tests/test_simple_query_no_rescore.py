#! /usr/bin/env python
import requests as r
from pprint import pformat
import string
import logging as log
import sys

log.basicConfig(
    level=log.DEBUG,
    stream=sys.stdout
)

for v in string.hexdigits.lower():
    log.info(f"running test for name starts with {v}")
    response = r.post(
        "http://es:9200/example-data/_search",
        json={
            "query": {"wildcard": {"name": f"{v}*"}}
        }
    )
    response.raise_for_status()
    names = [record['_source']['name'] for record in response.json()['hits']['hits']]
    assert len(names) > 0, f"""expected more than 0 names that start with {v}"""
    assert all([str(n).startswith(v) for n in names]), f"""
        Query failed unexpectedly! Potential regression!

            expected all names to start with {v}!
            
            names:\n{pformat(names)}
        """

