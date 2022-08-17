#!/bin/bash

set -euxo pipefail
exec /opt/java/openjdk/bin/java -jar /app/metarank.jar "$@"

