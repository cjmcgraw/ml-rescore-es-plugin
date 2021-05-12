FROM python:3.8
WORKDIR /app
RUN apt-get update -yqq && apt-get install -yqq siege
RUN python --version
RUN pip --version
ADD requirements.txt requirements.txt
RUN pip install -r requirements.txt
ADD docker-entrypoint.sh docker-entrypoint.sh
ENTRYPOINT ["./docker-entrypoint.sh"]

