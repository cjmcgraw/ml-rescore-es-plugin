FROM openjdk:14 as builder

LABEL maintainer="carlm@accretivetg.com"

WORKDIR /build
COPY ./plugin/LICENSE.txt LICENSE.txt
COPY ./plugin/NOTICE.txt NOTICE.txt
COPY ./plugin/gradle gradle
COPY ./plugin/gradlew gradlew
COPY ./plugin/build.gradle build.gradle
COPY ./plugin/settings.gradle settings.gradle
COPY ./plugin/src src/
RUN ls -alR src/

RUN --mount=type=cache,id=gradle_cache,target=/root/.gradle \
        ./gradlew \
        --no-daemon \
        --parallel \
        test

RUN --mount=type=cache,id=gradle_cache,target=/root/.gradle \
        ./gradlew \
        --no-daemon \
        --parallel \
        clean assemble

RUN ls -al /build/build/distributions/

FROM scratch as plugin_files
COPY --from=builder /build/build/distributions/ml-rescore-v0.zip /ml-rescore-v0.zip

FROM elasticsearch:7.9.0
RUN mkdir /ml-grcp-rescoring-esplugin/
COPY --from=builder /build/build/distributions/ml-rescore-v0.zip /ml-rescoring-esplugin/
RUN yes | bin/elasticsearch-plugin install file:///ml-rescoring-esplugin/ml-rescore-v0.zip
