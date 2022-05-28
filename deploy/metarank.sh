#!/bin/bash

case $1 in
  "bootstrap" | "inference" | "standalone" | "train" | "upload" | "validate" | "api" | "update" | "help")
    exec /usr/local/openjdk-11/bin/java -jar /app/metarank.jar "$@"
    ;;
  *)
    exec /docker-entrypoint.sh "$@"
    ;;
esac

