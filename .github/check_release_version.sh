#!/bin/bash
set -euo pipefail

TAG="${1:?Usage: check_release_version.sh <tag>}"

# --batch + supershell/color off: sbt 2 otherwise emits terminal-control lines
# (e.g. ESC[0J) that end up as the tail of the output instead of the version
VERSION=$(sbt --error --batch -Dsbt.supershell=false -Dsbt.color=false -Dsbt.log.noformat=true "print version" \
  | sed -e 's/\x1b\[[0-9;]*[A-Za-z]//g' -e 's/[[:cntrl:]]//g' \
  | awk 'NF { last = $1 } END { print last }')

if [ "$VERSION" != "$TAG" ]; then
  echo "::error::Tag ${TAG} does not match ThisBuild/version ${VERSION} in build.sbt"
  exit 1
fi
echo "Tag ${TAG} matches build.sbt version ${VERSION}"
