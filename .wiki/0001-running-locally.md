## Developing Locally

To develop locally with this project I highly recommend using docker-compose for all
development needs.

First you'll need to run the `proto_gen.sh` script, because this isn't easily automated
in docker containers. The only dependency needed for this should be docker!

```
$ ./gen_proto.sh
```

Doing that we see that there are some proto and pb files generated:

```
│               └── ./es/plugin/src/main/proto
│                   └── ./es/plugin/src/main/proto/recsys.proto
│   ├── ./grpc_server/recsys_pb2.py
```
These are generally needed for the project to work!

Then up all the containers:

```
docker-compose up
```

You'll have an instance of a grpc-server and elasticsearch with the plugin installed.
