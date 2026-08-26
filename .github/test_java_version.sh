#!/bin/bash

MV=`javap -v -classpath target/metarank.jar ai.metarank.util.VarNum|grep "major version"`

echo "$MV"

if [[ $MV = "  major version: 65" ]]
then
  echo "JVM version check - PASS"
else
  echo "Build with wrong JDK target, it should be 21"
  exit -1
fi