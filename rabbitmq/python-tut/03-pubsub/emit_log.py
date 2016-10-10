#/usr/bin/env python

import pika
import sys

if (len(sys.argv) != 2):
  print("Usage: python", sys.argv[0], "<msg string>")
  exit(1)

message = "".join(sys.argv[1])

connection = pika.BlockingConnection(pika.ConnectionParameters("localhost"))
channel = connection.channel()

channel.exchange_declare(exchange="logs", type="fanout")
channel.basic_publish(exchange="logs", routing_key="", body=message)

connection.close()
