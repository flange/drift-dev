#/usr/bin/env python

import pika

def callback(channel, method, properties, body):
  print("[RX] msg: %r" % body)


credentials = pika.PlainCredentials("sar", "foobar")

connParams = pika.ConnectionParameters('192.168.5.183',
                                       5672,
                                       '/',
                                       credentials)

connection = pika.BlockingConnection(connParams)
channel = connection.channel()

channel.queue_declare(queue="hello")
channel.basic_consume(callback, queue="hello", no_ack=True)

print("[RX] waiting for messages")
channel.start_consuming()
