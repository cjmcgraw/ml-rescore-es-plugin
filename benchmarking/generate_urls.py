from uuid import uuid4 as uuid
import itertools as i
import multiprocessing as mp
import random as r
import string
import json
import tqdm

url = "http://es:9200/benchmarking-index/_search"

def generate_record(*args):
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
    return f"{url} POST {request_body}"




with open("./urls.txt", "w") as f:
        with mp.Pool() as p:
            strings = p.map(generate_record, tqdm.trange(100_000, desc="batch", leave=False))
        f.write("\n".join(strings))
