#!/bin/bash

set -euxo pipefail

DIR=`pwd`

CONTAINER_ID=`docker run -d -p 8080:8080 -v $DIR/src/test/resources/ranklens/:/data metarank/metarank:snapshot serve --config /data/config.yml`

echo "Trying to connect to :8080"

timeout 30 sh -c 'until curl -v http://localhost:8080/health; do sleep 1; done'

echo "Stopping container $CONTAINER_ID"

docker kill $CONTAINER_ID