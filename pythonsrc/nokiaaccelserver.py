# This code includes parts that were distributed as a moficiation of ball.py
# originally shipped with Nokia's Python for Series 60, by Christopher Schmidt,
# and that were released under the same license as that code (see below).
# All new code is being released under the same Apache License, Version 2.0
# with
# Copyright (c) 2008 Rene Mayrhofer
#
# Original copyright was:
# Copyright (c) 2008 Christopher Schmidt
# Copyright (c) 2005 Nokia Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import socket
#import e32
import sensor

PORT = 12008

sockets = []

class SensorConnection(object):
    delta = []
    def __init__(self):
        """Connect to the sensor."""
	print 'Connecting to accelerometer and starting readout'
        sens = sensor.sensors()['AccSensor']
        self.sensconn = sensor.Sensor(sens['id'], sens['category'])
        self.sensconn.connect(self.newdata)
    
    def newdata(self, state):
	self.delta = []
        for key in ['data_1', 'data_2', 'data_3']:
            val = state[key]
            self.delta.append(val)
	for self.sock in sockets:
	   if self.sock:
	   	self.sock.sendall( "%i,%i,%i*"%(self.delta[0], self.delta[1], self.delta[2]) )

    def cleanup(self):
        """Cleanup after yourself. *Must be called* before exiting."""
	print '"Closing connection to accelerometer'
        self.sensconn.disconnect()

sense_conn = None

# this gives a permission denied error! why?
#def accept_callback((conn, addr)):
    ### conn is the new socket (e32socket.Socket type), addr the address it was connected from
    #print 'New connection from', addr
    #sockets.append(conn)
    #print 'Accepted connection from', addr, ' exiting callback'

running=1
def quit():
    global running
    running=0

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind((u'127.0.0.1', PORT))
s.listen(5)

while running:
	print "Waiting for connection"
	# this gives a permission denied error! why?
#	s.accept(accept_callback)
	conn, addr = s.accept()
	print 'New connection from', addr
	
	sockets.append(conn)
	sense_conn = SensorConnection()
	while running:
		data = conn.recv(1024)
    		if not data: break
    		conn.send(data)
	print 'Connection from', addr, 'terminated'
	sockets.remove(conn)
	conn.close()
	sense_conn.cleanup()
	
if sense_conn:
	sense_conn.cleanup()
# be sure to clean up
for sock in sockets:
	sock.close()


#def listen(self):
                #while True:
                        #conn, addr = self.sock.accept()
                        #print "append sockpair"
                        #self.sockPairList.append(SockPair(conn, None))

                
		#self.lthread = thread.start_new_thread(self.listen, ())
                #while True:
                        #<do read/write with sockets in self.sockPairList>


###############

