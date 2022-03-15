#!/bin/sh

JAR=$1
TMPDIR=`mktemp -d /tmp/ranklens-XXXXXX`

java -jar $JAR bootstrap \
  --events src/test/resources/ranklens/events/ \
  --out $TMPDIR \
  --config src/test/resources/ranklens/config.yml

echo "Boostrap done into dir $TMPDIR"

java -jar $JAR train \
  --input $TMPDIR/dataset \
  --config src/test/resources/ranklens/config.yml \
  --model-type lambdamart-lightgbm \
  --model-file $TMPDIR/metarank.model

echo "Training done"

java -jar $JAR inference \
  --config src/test/resources/ranklens/config.yml \
  --model $TMPDIR/metarank.model \
  --embedded-redis-features-dir $TMPDIR/features \
  --format json \
  --savepoint-dir $TMPDIR/savepoint