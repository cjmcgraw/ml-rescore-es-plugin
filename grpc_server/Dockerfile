FROM python:3.8
RUN mkdir /app
WORKDIR /app
ADD requirements.txt requirements.txt
RUN pip install -r requirements.txt
ADD *.py /app/
EXPOSE 50051
ENTRYPOINT ["python", "server.py"]
