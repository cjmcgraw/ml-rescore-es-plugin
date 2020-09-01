#! /usr/bin/env bash
echo "HELLO, WORLD"
#! /usr/bin/env python

for test in $(ls *.py); do
    echo "running test: ${test}"
    python "${test}"
    echo "finished test: ${test}"
done
