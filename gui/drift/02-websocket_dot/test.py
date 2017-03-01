#!/usr/bin/env python

import asyncio
import datetime
import random
import websockets

async def handle(websocket, path):

    while True:
        userInput = input()

        if userInput == "q":
          exit(0)

        print("sending:\n")
        await websocket.send(content)




with open('example.dot', 'r') as content_file:
    content = content_file.read()

start_server = websockets.serve(handle, '127.0.0.1', 9000)

asyncio.get_event_loop().run_until_complete(start_server)
asyncio.get_event_loop().run_forever()
