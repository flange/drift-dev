#!/bin/bash

RABBITMQ=/home/frank/1Uni/MA/ma/drift-dev/rabbitmq/rabbitmq_jars/rabbitmq-client.jar

javac -cp .:${RABBITMQ} Send.java
