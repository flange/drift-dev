#/usr/bin/env python

import pika

def callback(channel, method, properties, body):
  print("[RX] msg: %r" % body)


connection = pika.BlockingConnection(pika.ConnectionParameters("localhost"))
channel = connection.channel()

channel.queue_declare(queue="hello")
channel.basic_consume(callback, queue="hello", no_ack=True)

print("[RX] waiting for messages")
channel.start_consuming()
