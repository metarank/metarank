#!/bin/bash

set -euxo pipefail

JAR=$1

java -jar $JAR bootstrap src/test/resources/ranklens/config.yml

