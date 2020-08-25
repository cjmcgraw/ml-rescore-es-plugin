import logging as log
import asyncio
from grpc.experimental import aio
from random import random
import sys

from google.protobuf.json_format import MessageToJson, MessageToDict

import recsys_pb2
import recsys_pb2_grpc


class Recommender(recsys_pb2_grpc.RecommenderServicer):
    async def recommend(self, request, context):
        log.info(f"inbound request: {MessageToJson(request)}")
        scores = [random() for _ in request.exampleids]
        response = recsys_pb2.RecommenderResponse(outputs=scores)
        log.info(f"outbound response: {MessageToJson(response)}")
        return response


async def run_server():
    server = aio.server()
    server.add_insecure_port("0.0.0.0:50051")
    recsys_pb2_grpc.add_RecommenderServicer_to_server(Recommender(), server)
    await server.start()
    await server.wait_for_termination()


if __name__ == "__main__":
    log.basicConfig(level=log.DEBUG, stream=sys.stdout)
    loop = asyncio.get_event_loop()
    loop.create_task(run_server())
    loop.run_forever()
