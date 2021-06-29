#! /usr/bin/env bash
test_data_file="test_documents.newline-delimited-json"
index="test-index"
force_generate=""
fill_index=""
only_run="test_*"

function usage() {
    echo "$0 args"
    echo ""
    echo "optional arguments:"
    echo "  --index           index to use (defaults=${index})"
    echo "  --force-generate  generate the data, even if the data file already exists"
    echo "  --fill-index      fill the data in the index, generate it if missing"
    echo "  --only-run        regex of tests to run match ${only_run}.py (default=*)"
}

while [ $# ];do case $1 in
    --index) index="$2" ;;
    --force-generate) force_generate="true" ;;
    --fill-index) fill_index="true" ;;
    --only-run) only_run="$2";;
    --help) usage; exit 0 ;;
    *) break ;;
esac; shift; shift; done

set -u
if [[ -z "$index" ]]; then
    echo "index to fill cannot be empty!"
    echo ""
    usage
    exit 1
fi

es_host="es"
echo "checking for ES server availability..."
echo "attempting expected host ${es_host} first"
echo ""
ping -D -c 5 "${es_host}"
found_host=$?
if [[ $found_host -gt 0 ]]; then
    echo "  WARNING:"
    echo "    expected container running in docker network stack"
    echo "    it is expected that you run this command via docker!"
    echo "    please consider doing that instead of running on your"
    echo "    host machine!"
    echo ""
    es_host="localhost"
    echo "attempting ${es_host} instead"
    ping -D -c 5 "${es_host}"
    found_host=$?
    echo ""
fi

if [[ $found_host -gt 0 ]]; then
    echo "unable to find elasticsearch server!"
    echo "these tests will fail if the server is"
    echo "not up!"
    echo ""
    echo "please bring the elasticsearch container"
    echo "up in the environment!"
    echo ""
    echo "  docker-compose up -d es"
    echo ""
    echo "then run these tests with:"
    echo ""
    echo "  docker-compose run tests"
    echo ""
    exit 1
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

echo "found existing ES server at ${es_host}:9200!"
echo "checking for required documents"
echo ""

echo "using index=${index}"
echo ""

if  [[ -z "${fill_index}" ]]; then
    echo "checking if expected index exists!"
    index_exists=$(curl --fail --silent "${es_host}:9200/${index}")
    if [[ -z "${index_exists}" ]]; then
        echo "no index found. Toggling an index fill"
        fill_index="true"
    else
        echo "index exists!"
        echo "not forcing a fill of index!"
    fi
    echo ""
fi

if [[ ! -z "${fill_index}" ]] && [[ ! -f "${test_data_file}" ]]; then
    echo "no test data file found at ${test_data_file}"
    echo "setting up generation of data"
    echo ""
    force_generate="true"
fi
    
if [[ ! -z "$force_generate" ]]; then
    echo "generating new data to replace any existing data in the index"
    echo "generating..."
    python generate_data.py
    echo "finished"
    echo "generated $(cat "${test_data_file}" | wc -l) lines"
    echo ""
    fill_index="true"
fi

set -eu
if [[ ! -z "$fill_index" ]]; then
    echo "index fill triggered!"
    echo "clearing out any existing index=${index}"
    curl -XDELETE --silent "${es_host}:9200/${index}"
    echo ""
    echo "filling index ${index} with data located in ${test_data_file}"
    echo "found $(cat "${test_data_file}" | wc -l) records in file"
    echo "here is a sample of first 5..."
    head -n 5 "${test_data_file}"
    echo "filling..."
    curl --silent --fail \
        "${es_host}:9200/${index}/_bulk" \
        -H "Content-Type: application/x-ndjson" \
        --data-binary @${test_data_file} > /dev/null
    echo "finished filling index"
    echo "waiting while documents refresh in index=${index}"
    sleep 15
    echo "checking that the expected number of documents are in the index!"
    expected_docs=$(printf "%d" $(($(cat ${test_data_file} | wc -l) / 2)))
    actual_docs=$(curl --silent --fail "${es_host}:9200/_cat/indices" | grep "${index}" | awk '{print $7}')
    echo "expected_docs count = ${expected_docs}"
    echo "actual_docs count = ${actual_docs}"
    echo ""
    echo "checking that they are the same!"
    if [[ $expected_docs != $actual_docs ]]; then
        echo "unexpectedly missing documents from fill!"
        echo ""
        echo "bailing cowardly!"
        echo ""
        exit 1
    fi
    echo "document counts match!"
    echo "finished filling ES index=${index}"
    echo ""
    echo "cleaning up resources for test data"
    rm -rf "${test_data_file}"
    echo "finished cleaning up resources"
fi
echo ""

echo "starting testing"
set -eu
for test in $(find . -type f -name "$only_run.py" | grep -v 'test_helpers.py'); do
    echo "running test: ${test}"
    cmd="python '${test}' --es-host '${es_host}' --index '${index}'"
    eval $cmd
    echo "finished test: ${test}"
done
