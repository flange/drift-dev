#!/usr/bin/env python

from SimpleWebSocketServer import SimpleWebSocketServer, WebSocket

class SimpleEcho(WebSocket):

    def handleMessage(self):
        self.sendMessage(input())

    def handleConnected(self):
        print("connected")

    def handleClose(self):
        print('closed')

server = SimpleWebSocketServer('', 9000, SimpleEcho)
server.serveforever()
