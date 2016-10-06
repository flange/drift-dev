#/usr/bin/env python

import pika
import sys

if (len(sys.argv) != 2):
  print("Usage: python", sys.argv[0], "<sleep sec>")


message = "".join(sys.argv[1])

connection = pika.BlockingConnection(pika.ConnectionParameters("localhost"))

channel = connection.channel()
channel.queue_declare(queue="task_queue")

channel.basic_publish(exchange="", routing_key="task_queue", body=message)




