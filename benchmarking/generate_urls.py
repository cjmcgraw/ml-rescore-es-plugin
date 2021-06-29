from uuid import uuid4 as uuid
import itertools as i
import multiprocessing as mp
import random as r
import string
import json
import tqdm

url = "http://es:9200/benchmarking-index/_search"

contextual_item_ranker_request = lambda: {
    "query": {"wildcard": {"name": r.choice(string.ascii_lowercase) + "*"}},
    "rescore": {
        "window_size": 1000,
        "mlrescore-v1": {
            "type": "ranking",
            "name": 'contextual_item_ranker',
            "domain": "contextual-item-ranker:8500",
            "model_name": "contextual_item_ranker_testing_model",
            "itemid_field": r.choice(['itemId1', 'itemId2', 'itemId3']),
            "context": {
                "some-cache-breaking-key": [uuid().hex]
            }
        }
    }
}

item_ranker_request = lambda: {
    "query": {"wildcard": {"name": r.choice(string.ascii_lowercase) + "*"}},
    "rescore": {
        "window_size": 1000,
        "mlrescore-v1": {
            "type": "ranking",
            "name": 'item_ranker',
            "domain": "item-ranker:8500",
            "model_name": "item_ranker_testing_model",
            "itemid_field": r.choice(['itemId1', 'itemId2', 'itemId3']),
        }
    }
}

def generate_record(*args):
    # much higher weight for loggedin requests. Since its performance critical
    requests = [contextual_item_ranker_request] * 50 + [item_ranker_request]
    fn = r.choice(requests)
    request_body = json.dumps(fn())
    return f"{url} POST {request_body}"


with open("./urls.txt", "w") as f:
        with mp.Pool() as p:
            strings = p.map(generate_record, tqdm.trange(10_000, desc="batch", leave=False))
        f.write("\n".join(strings))
