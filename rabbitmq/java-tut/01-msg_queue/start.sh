#!/bin/bash

if [ "x$1" == "x" ]; then

  echo "Usage: sh $0 [rabbitmq | send | recv ]"
  exit 1

fi

if [ "x$1" == "xrabbitmq" ]; then
  sudo rabbitmq-server
  exit 0
fi



JARSHOME="../00-rabbitmq_jars";

JARS="$JARSHOME/commons-io-1.2.jar:$JARSHOME/commons-cli-1.1.jar:rabbitmq-client.jar"



if [ "x$1" == "xsend" ]; then
  java -cp .:../00-rabbitmq_jars/commons-io-1.2.jar:../00-rabbitmq_jars/commons-cli-1.1.jar:../00-rabbitmq_jars/rabbitmq-client.jar Send
  exit 0
fi


if [ "x$1" == "xrecv" ]; then
  java -cp .:../00-rabbitmq_jars/commons-io-1.2.jar:../00-rabbitmq_jars/commons-cli-1.1.jar:../00-rabbitmq_jars/rabbitmq-client.jar Recv
  exit 0
fi
