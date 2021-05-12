from uuid import uuid4 as uuid
import random as r
import string
import json
import tqdm

url = "http://es:9200/benchmarking-index/_search"

with open("./urls.txt", "w") as f:
    for _ in tqdm.tqdm(range(100_000)):
        request_body = json.dumps(
            {
                "query": {"wildcard": {"name": r.choice(string.ascii_lowercase) + "*"}},
                "rescore": {
                    "window_size": 1000,
                    "mlrescore-v1": {
                        "type": "ranking",
                        "name": 'item_id_half_plus_two',
                        "domain": "half-plus-two-model:8500",
                        "itemid_field": r.choice(['itemId1', 'itemId2', 'itemId3']),
                        "context": {
                            "query": [uuid().hex]
                        },
                    },
                },
            }
        )
        f.write(f"{url} POST {request_body}\n")
