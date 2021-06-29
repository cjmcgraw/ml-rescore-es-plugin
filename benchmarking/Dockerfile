FROM python:3.8
RUN apt-get update -y \
    && apt-get install -y \
    git \
    libssl-dev \
    openssl \
    zlib1g \
    zlib1g-dev

RUN git clone https://github.com/JoeDog/siege.git \
    && cd siege \
    && git checkout -b 'v4.0.9' \
    && ./utils/bootstrap \
    && ./configure \
    && make \
    && make uninstall \
    && make install

RUN siege --version

RUN sed -i 's/# reps =/reps = once/' /root/.siege/siege.conf
RUN sed -i 's/verbose = true/verbose = false/' /root/.siege/siege.conf
WORKDIR /app
RUN python --version
RUN pip --version
ADD requirements.txt requirements.txt
RUN pip install -r requirements.txt
ADD docker-entrypoint.sh docker-entrypoint.sh
ENTRYPOINT ["./docker-entrypoint.sh"]

