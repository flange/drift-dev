#/usr/bin/env python

import pika
import signal
import sys
import time


def sigint_handler(signal, frame):
        connection.close()
        print("\n")
        sys.exit(0)


def callback(channel, method, properties, body):
  print("[RX] msg: %r" % body)
  time.sleep(int(body))
  print("[RX] done.")
  channel.basic_ack(delivery_tag=method.delivery_tag)


signal.signal(signal.SIGINT, sigint_handler)

connection = pika.BlockingConnection(pika.ConnectionParameters("localhost"))
channel = connection.channel()

channel.queue_declare(queue="task_queue")

channel.basic_qos(prefetch_count=1)
channel.basic_consume(callback, queue="task_queue")

print("[RX] waiting for messages")
channel.start_consuming()






