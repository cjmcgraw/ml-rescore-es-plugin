# syntax=docker/dockerfile:1.2
FROM tensorflow/serving:2.5.1
RUN apt-get -y update && apt-get install -y python3-dev python3-pip
RUN apt-get install -y python3.8
RUN python3.8 --version
RUN --mount=type=cache,target=/root/.cache/pip python3.8 -m pip --cache-dir /root.cache/pip install --upgrade pip && python3.8 -m pip --cache-dir /root/.cache/pip install 'tensorflow==2.5.0'
RUN mkdir /app
WORKDIR /app
COPY build_model.py build_model.py
RUN python3.8 build_model.py
RUN mkdir -p /models/item_ranker_testing_model/1
RUN mv model_dir/* /models/item_ranker_testing_model/1
ENV MODEL_NAME item_ranker_testing_model
