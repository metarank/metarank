#!/bin/bash

set -euxo pipefail

# Metarank docker image entrypoint.
# Wraps flink one and extends it with metarank-specific ones.

case $1 in
  "bootstrap" | "inference" | "standalone" | "train" | "upload" | "validate" | "api" | "update" | "k8s-manifest" | "help" )
    exec /usr/local/openjdk-11/bin/java -jar /app/metarank.jar "$@"
    ;;
  *)
    exec /docker-entrypoint.sh "$@"
    ;;
esac

