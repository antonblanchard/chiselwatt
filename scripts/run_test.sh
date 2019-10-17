#!/bin/bash -e

if [ $# -ne 1 ]; then
	echo "Usage: run_test.sh <test>"
	exit 1
fi

TEST=$1

TMPDIR=$(mktemp -d)

function finish {
	rm -rf "$TMPDIR"
}

trap finish EXIT

CHISELWATT_DIR=$PWD

cd $TMPDIR

${CHISELWATT_DIR}/scripts/bin2hex.py ${CHISELWATT_DIR}/tests/${TEST}.bin > insns.hex

${CHISELWATT_DIR}/chiselwatt 2> test.err | grep -v "^$" | grep -v GPR31 > test.out || true

grep -v "^$" ${CHISELWATT_DIR}/tests/${TEST}.out | grep -v GPR31 | grep -v XER > exp.out

diff -q test.out exp.out && echo "$TEST PASS" && exit 0

cat test.err
echo "$TEST FAIL ********"
exit 1
