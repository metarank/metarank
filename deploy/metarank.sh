#!/bin/bash

set -euxo pipefail
exec /usr/bin/java -jar /app/metarank.jar "$@"

