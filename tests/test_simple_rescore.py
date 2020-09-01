#! /usr/bin/env python
from uuid import uuid4 as uuid
import requests as r
import logging as log
import string
import sys
log.basicConfig(
    level=log.DEBUG,
    stream=sys.stdout
)

for v in string.hexdigits.lower():
    response = r.post(
        "http://es:9200/example-data/_search",
        json={
            "query": {"wildcard": {"name": f"{v}*"}},
            "rescore": {
                "window_size": 500,
                "mlrescore": {
                    "model_name": "grpc-server:50051",
                    "context": uuid().hex
                }
            }
        }
    )
    response.raise_for_status()

    names, ids = zip(*[
        [r['_source']['name'], int(r['_id'])]
        for r in response.json()['hits']['hits']
    ])
    assert all([n.startswith(v) for n in names]), f"queried name didn't start with {v}"
    assert list(ids) == sorted(ids, reverse=True), f"""
    expected ids to come back in reverse sorted order
    
    ids: {ids}
    
    sorted: {sorted(ids, reverse=True)}
    """
