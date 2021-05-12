import requests
import json

def check_request_okay(response):
    request = response.request

    assert response.ok, f"""
    Failed to receive back a 200 http response!

    request:
      method: {request.method}
      path: {request.url}
      body:
         {json.dumps(json.loads(request.body.decode()), indent=4)}


    response:
        status: {response.status_code}
        body:
            {response.text}
    """

    data = response.json()
    shard_failures = data['_shards'].get('failures', [])
    assert len(shard_failures) == 0, f"""
    Unexpected shard failures during request!

    request:
      method: {request.method}
      path: {request.url}
      body:
         {json.dumps(json.loads(request.body.decode()), indent=4)}


    response:
        status: {response.status_code}
        body:
            {response.text}

    shard failures:
    
    {json.dumps(shard_failures, indent=4)}
    """
