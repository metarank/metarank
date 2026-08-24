#!/bin/bash

set -euxo pipefail

docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

# buildx builds and pushes amd64+arm64 images with a multi-arch manifest,
# tagged from the version in build.sbt: :<version>, :latest and :snapshot
sbt -mem 5000 Docker/publish
