#!/usr/bin/env bash
set -eu

es_host="es"
echo "checking for ES server availability..."
echo "attempting expected host ${es_host} first"
echo ""
ping -D -c 5 "${es_host}"
found_host=$?
if [[ $found_host -gt 0 ]]; then
    echo ""
    echo ""
    echo "  WARNING:"
    echo "    expected container running in docker network stack"
    echo "    it is expected that you run this command via docker!"
    echo "    please consider doing that instead of running on your"
    echo "    host machine!"
    echo ""
    echo ""
    es_host="localhost"
    echo "attempting ${es_host} instead"
    ping -D -c 5 "${es_host}"
    found_host=$?
    echo ""
fi

echo "found es host at ${es_host}"
echo "checking if host is up and accepting requests..."
echo ""

attempts=0
es_is_inactive=1
while [[ $es_is_inactive -gt 0 ]]; do
    echo "attempting to communicate with ES server on 9200"
    curl --fail --silent --connect-timeout 10 "${es_host}:9200/"
    es_is_inactive=$?
    if [[ "${es_is_inactive}" -eq 0 ]]; then
        echo "found active ES server!"
    else
        echo "unable to communicate with es server!"
        echo ""
        echo "sleeping for 10 seconds before next attempt!"
        echo ""
        sleep 10
    fi

    let "attempts+=1"
    if [[ ${attempts} -gt 500 ]]; then
        echo "  ERROR:"
        echo "    failed to find ES server available!"
        echo "    "
        echo "    please bring up the ES container, or"
        echo "    check why it failed to come up!"
        echo ""
        exit 1
    fi
done
echo ""

echo "deleteing benchmarking index, if it exists"
curl --silent -XDELETE 'es:9200/benchmarking-index' > /dev/null
echo "ignore above errors if they occurred"
echo ""
echo "generating data to submit"
python generate_data.py
echo "finished generating benchmarking data"
echo ""
echo "filling up ES server with data"
curl --fail --silent -XPOST "${es_host}:9200/benchmarking-index/_bulk" \
    -H "Content-Type: application/x-ndjson" \
    --data-binary @benchmarking_documents.newline-delimited-json > /dev/null

echo ""
echo "generating urls.txt for siege"
python generate_urls.py
echo "finished generating urls"

echo "running server siege on half-plus-two-model with unique requests"
siege \
    --content-type application/json \
    --concurrent 15 \
    --file urls.txt \
    --internet \
    --benchmark \
    --reps=once \
    $@


echo "running server siege on half-plus-two-model with cached requests"
siege \
    --content-type application/json \
    --concurrent 15 \
    --file urls.txt \
    --internet \
    --benchmark \
    --reps=once \
    $@
