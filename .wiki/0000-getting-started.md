
## Getting Started

To install this plugin you can build the zip using gradlew inside of the plugin directory:

```
./proto_gen.sh && ./es/plugin/gradlew assemble
```

This will create a zip file that can be uploaded and manually installed to your
Elasticsearch instances in production.

```
/usr/share/elasticsearch/install-plugin install ./distributions/ml-grpc-rescore.zip
```

After a server restart of Elasticsearch the plugin should be hot and ready to go

