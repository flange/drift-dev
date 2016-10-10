#!/usr/bin/env python
import pika

def callback(channel, method, properties, body):
  print("[RX] msg: ", body)

connection = pika.BlockingConnection(pika.ConnectionParameters("localhost"))
channel = connection.channel()

channel.exchange_declare(exchange="logs",
                         type="fanout")

result = channel.queue_declare(exclusive=True)
queue_name = result.method.queue

channel.queue_bind(exchange='logs',
                   queue=queue_name)

channel.basic_consume(callback, queue=queue_name, no_ack=True)

print("[RX] waiting for messages")
channel.start_consuming()
