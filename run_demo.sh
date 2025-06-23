#!/bin/bash

set -euxo pipefail

JAR=$1
CONF=$2

java -jar $JAR bootstrap $CONF

echo "Bootstrap done"

java -jar $JAR train $CONF xgboost

echo "Training done"

java -jar $JAR standalone $CONF
