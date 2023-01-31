#!/bin/bash

set -euxo pipefail

V=$1

docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

PLATFORM=amd64 VERSION=$V sbt -mem 3000 dockerBuildAndPush
PLATFORM=arm64 VERSION=$V sbt -mem 3000 dockerBuildAndPush

docker manifest create --amend metarank/metarank:$V metarank/metarank:$V-arm64 metarank/metarank:$V-amd64
docker manifest create --amend metarank/metarank:latest metarank/metarank:$V-arm64 metarank/metarank:$V-amd64
docker manifest push metarank/metarank:$V
docker manifest push metarank/metarank:latest