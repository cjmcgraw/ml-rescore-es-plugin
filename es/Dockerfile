FROM openjdk:14 as builder

LABEL maintainer="carlm@accretivetg.com"

RUN mkdir -p /build
WORKDIR /build

COPY ./es/plugin/LICENSE.txt LICENSE.txt
COPY ./es/plugin/NOTICE.txt NOTICE.txt
COPY ./es/plugin/gradle gradle
COPY ./es/plugin/gradlew gradlew
COPY ./es/plugin/build.gradle build.gradle
COPY ./es/plugin/settings.gradle settings.gradle
COPY ./es/plugin/src src/
RUN unlink src/main/proto
COPY ./protos/ src/main/proto/
RUN ls -alR src/

RUN ./gradlew \
        --no-daemon \
        --parallel \
        clean assemble

FROM elasticsearch:7.9.0
RUN mkdir /ml-grpc-rescore-plugin/
COPY --from=builder /build/build/distributions/ml-grpc-rescore.zip /ml-grpc-rescore-plugin/
RUN bin/elasticsearch-plugin install file:///ml-grpc-rescore-plugin/ml-grpc-rescore.zip
ADD ./es/data.tar.gz /usr/share/elasticsearch/
RUN chown -R elasticsearch /usr/share/elasticsearch/data
RUN ls -alR /usr/share/elasticsearch/data

