FROM python:3.8
RUN mkdir /app
WORKDIR /app
ADD requirements.txt .
RUN pip install -r requirements.txt
ADD test_*.py ./
ADD run_tests.sh run_tests.sh

ENTRYPOINT ["/app/run_tests.sh"]
