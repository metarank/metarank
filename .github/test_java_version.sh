#!/bin/bash

MV=`javap -v target/scala-2.13/classes/ai/metarank/util/VarNum.class|grep "major version"`

echo "$MV"

if [[ $MV = "  major version: 55" ]]
then
  echo "JVM version check - PASS"
else
  echo "Build with JDK 12+ target, it should be 11"
  exit -1
fi