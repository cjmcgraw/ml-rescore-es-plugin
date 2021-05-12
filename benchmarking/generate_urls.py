from uuid import uuid4 as uuid
import tqdm
import json
import random as r

sites = ["streamate.com", "pornhublive.com", "xvideoslive.net", "youpornlive.com"]
countries = ["US", "FR", "DE", "GB", "CA", "ID", "CN", "MX", "BR", "PR"]
filters = [uuid().hex for _ in range(100)]
users =["26699273", "24077767", "23127208", "12653768", "4783511", "3662707", "7989296",
        "36277481", "7077617", "5453155", "16345858", "13801716", "9739171", "1649749", "16014500",
        "10746781", "10514850", "16018924", "87525807", "3624819", "37996896", "6182130", "32031988", 
        "1839602",  "12340094", "14507780", "47454114", "50132709", "876533", "26662338", "12459715",
        "8658179", "31667786", "49701197", "1462374", "18509089"

]
url = "http://es:9200/performer-state-dev/_search"

with open("./urls.txt", "w") as f:
    for _ in tqdm.tqdm(range(100_000)):
        request_body = json.dumps(
            {
                "query": {"bool": {"must": {"term": {"online": True}}}},
                "rescore": {
                    "window_size": 500,
                    "ml-rescore-v0": {
                        "type": "ranking",
                        "name": "performer-loggedoutrec",
                        "domain": "loggedoutrec:50051",
                        "exampleid_field": "performerId",
                        "context": {
                            "country": r.sample(countries, 1),
                            "sitename": r.sample(sites, 1),
                            "filters": r.sample(filters, r.randint(1, 10)),
                            "boosted_filters": r.sample(filters, r.randint(1, 10)),
                            "excluded_filters": r.sample(filters, r.randint(1, 10)),
                        },
                    },
                },
            }
        )
        f.write(f"{url} POST {request_body}\n")


with open("./loggedinurls.txt", "w") as f:
    for _ in tqdm.tqdm(range(100_000)):
        request_body = json.dumps(
            {
                "query": {"bool": {"must": {"term": {"online": True}}}},
                "rescore": {
                    "window_size": 500,
                    "ml-rescore-v0": {
                        "type": "ranking",
                        "name": "performer-loggedinrec",
                        "domain": "loggedinrec:50051",
                        "exampleid_field": "performerId",
                        "context": {
                            "userid": r.sample(users, 1),
                        },
                    },
                },
            }
        )
        f.write(f"{url} POST {request_body}\n")

