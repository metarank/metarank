#!/bin/bash

docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

DOCKER_PLATFORM=amd64 sbt docker
DOCKER_PLATFORM=arm64 sbt docker
