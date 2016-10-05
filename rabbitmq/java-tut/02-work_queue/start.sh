#!/bin/bash

if [ "x$1" == "x" ]; then

  echo "Usage: sh $0 [rabbitmq | master | worker ]"
  exit 1

fi

if [ "x$1" == "xrabbitmq" ]; then
  sudo rabbitmq-server
  exit 0
fi


if [ "x$1" == "xmaster" ]; then
  shift
  java -cp .:../00-rabbitmq_jars/commons-io-1.2.jar:../00-rabbitmq_jars/commons-cli-1.1.jar:../00-rabbitmq_jars/rabbitmq-client.jar NewTask $@
  exit 0
fi


if [ "x$1" == "xworker" ]; then
  java -cp .:../00-rabbitmq_jars/commons-io-1.2.jar:../00-rabbitmq_jars/commons-cli-1.1.jar:../00-rabbitmq_jars/rabbitmq-client.jar Worker
  exit 0
fi
