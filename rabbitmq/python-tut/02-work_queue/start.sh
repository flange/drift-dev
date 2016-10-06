#!/bin/bash

if [ "x$1" == "x" ]; then

  echo "Usage: sh $0 [rabbitmq | send | recv ]"
  exit 1

fi

if [ "x$1" == "xrabbitmq" ]; then
  sudo rabbitmq-server
  exit 0
fi


if [ "x$1" == "xsend" ]; then
  exit 0
fi


if [ "x$1" == "xrecv" ]; then
  exit 0
fi
