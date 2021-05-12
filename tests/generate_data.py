#! /usr/bin/env python
from uuid import uuid4 as uuid
import random as r
import string
import json

with open("test_documents.newline-delimited-json", "w") as f:
    for first_char in string.ascii_lowercase:
        for _ in range(25):
            data = {
                "name": first_char + uuid().hex,
                "itemId1": r.randint(1, 10_00_000),
                "itemId2": r.randint(1, 10_00_000),
                "itemId3": r.randint(1, 10_00_000),
                "field1": uuid().hex,
                "field2": uuid().hex,
                "field3": uuid().hex
            }
            f.write(json.dumps({"index": {}}) + "\n")
            f.write(json.dumps(data) + "\n")
