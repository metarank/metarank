#!/bin/bash

set -euxo pipefail

JAR=$1
EVENTS=$2
CONFIG=$3

TMPDIR=`mktemp -d /tmp/ranklens-XXXXXX`

java -jar $JAR bootstrap \
  --events $EVENTS \
  --out $TMPDIR \
  --config $CONFIG

echo "Boostrap done into dir $TMPDIR"

java -jar $JAR train \
  --input $TMPDIR/dataset \
  --config $CONFIG \
  --model-type lambdamart-xgboost \
  --model-file $TMPDIR/metarank.model

echo "Training done"

java -jar $JAR inference \
  --config $CONFIG \
  --model $TMPDIR/metarank.model \
  --format json \
  --savepoint-dir $TMPDIR/savepoint \
  --embedded-redis-features-dir $TMPDIR/features

