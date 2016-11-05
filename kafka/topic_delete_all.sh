#!/bin/bash

queues=`kafka-topics.sh --zookeeper localhost:2181 --list | grep -v '__consumer'`

for q in $queues; do
  kafka-topics.sh --zookeeper localhost:2181 --delete --topic $q
done
