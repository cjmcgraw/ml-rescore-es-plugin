#!/usr/bin/env bash
set -eu
echo "waiting for ES container to be up"
sleep 600
echo "running server siege on loggedoutrec"
siege \
    --content-type application/json \
    --concurrent 10 \
    --file urls.txt \
    --internet \
    --benchmark \
    --time 2m

sleep 60
echo "running server siege on loggedinrec"
siege \
    --content-type application/json \
    --concurrent 10 \
    --file loggedinurls.txt \
    --internet \
    --benchmark \
    --time 2m
