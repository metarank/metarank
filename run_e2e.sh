#!/bin/sh

JAR=$1

if [[ ! -z "$JAR" ]];
  echo "use run_e2e.sh <path to jar>"
  exit 1
else
  java -jar $JAR bootstrap --events
fi