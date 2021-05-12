FROM python:3.8 as generate_urls
ADD requirements.txt requirements.txt
RUN pip install -r requirements.txt
ADD generate_urls.py generate_urls.py
RUN python generate_urls.py

FROM ubuntu:latest
RUN apt-get update -yqq && apt-get install -yqq siege
COPY --from=generate_urls urls.txt urls.txt
COPY --from=generate_urls loggedinurls.txt loggedinurls.txt
ADD docker-entrypoint.sh docker-entrypoint.sh
ENTRYPOINT ["./docker-entrypoint.sh"]

