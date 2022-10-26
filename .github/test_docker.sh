#!/bin/bash

set -euxo pipefail

docker run -d -v `pwd`/src/test/resources/ranklens/:/data metarank/metarank:snapshot serve --config /data/config.yml

curl -v http://localhost:8080/health