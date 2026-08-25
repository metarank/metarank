#!/bin/bash

MV=`javap -v target/out/jvm/scala-3.7.4/metarank/classes/ai/metarank/util/VarNum.class|grep "major version"`

echo "$MV"

if [[ $MV = "  major version: 65" ]]
then
  echo "JVM version check - PASS"
else
  echo "Build with wrong JDK target, it should be 21"
  exit -1
fi