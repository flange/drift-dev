#!/bin/bash

if [ "x$1" == "x" ]; then
  echo "need topic"
  exit 1;
fi


kafka-topics.sh --zookeeper localhost:2181 --delete --topic $1  >/dev/null
