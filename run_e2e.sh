#!/bin/bash

set -euxo pipefail

JAR=$1

java -jar $JAR bootstrap src/test/resources/ranklens/config.yml

echo "Boostrap done"

java -jar $JAR train src/test/resources/ranklens/config.yml xgboost

echo "Training done"

java -jar $JAR upload src/test/resources/ranklens/config.yml

echo "Upload done"

java -jar $JAR inference src/test/resources/ranklens/config.yml & echo $! > /tmp/inference.pid

PID=$(cat /tmp/inference.pid)

echo "Waiting for http server with pid=$PID to come online..."

while ! nc -z localhost 8080; do
  sleep 5
  echo "Trying to connect to :8080"
done

curl -v http://localhost:8080/health

kill -TERM $PID