#/usr/bin/env python

import pika

credentials = pika.PlainCredentials("sar", "foobar")

connParams = pika.ConnectionParameters('192.168.5.183',
                                       5672,
                                       '/',
                                       credentials)

connection = pika.BlockingConnection(connParams)

channel = connection.channel()
channel.queue_declare(queue="hello")

channel.basic_publish(exchange="", routing_key="hello", body="Hello, World!")

print("[TX] sending Msg")

connection.close()
