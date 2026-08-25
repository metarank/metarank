#!/bin/bash
set -euo pipefail

TAG="${1:?Usage: check_release_version.sh <tag>}"

VERSION=$(sbt --error "print version" | tail -n 1 | tr -d '[:space:]')

if [ "$VERSION" != "$TAG" ]; then
  echo "::error::Tag ${TAG} does not match ThisBuild/version ${VERSION} in build.sbt"
  exit 1
fi
echo "Tag ${TAG} matches build.sbt version ${VERSION}"
