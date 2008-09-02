import e32
import sensor, appuifw, key_codes

def sensor_event(data):
	global count, prev, normalize, normalize_values, canvas
	
	d = (data["data_1"] / 4, data["data_2"] / 4, data["data_3"] / 4)
	
	if normalize == 1:
		normalize_values = d
		normalize = 0
	
	if count > 280:
		reset()
	
	canvas.line((count + 19, prev[0] - normalize_values[0] + 40, count + 20, d[0] - normalize_values[0] + 40), outline = 0xFF0000, width = 1)
	canvas.line((count + 19, prev[1] - normalize_values[1] + 120, count + 20, d[1] - normalize_values[1] + 120), outline = 0x00DD00, width = 1)
	canvas.line((count + 19, prev[2] - normalize_values[2] + 200, count + 20, d[2] - normalize_values[2] + 200), outline = 0x4444FF, width = 1)
	
	count = count + 1
	prev = d
	
def reset():
	global count, canvas
	
	count = 0
	canvas.rectangle((0, 0, 320, 240), fill = 0x000000)

def keypress(event):
	global normalize
	
	if event["type"] == appuifw.EEventKeyDown and event["scancode"] == key_codes.EScancodeSelect:
		normalize = 1
		reset()
	

count = 0
prev = (0, 0, 0)
normalize = 1
normalize_values = (0, 0, 0)

canvas = appuifw.Canvas(event_callback = keypress)
appuifw.app.body = canvas
appuifw.app.screen = "full"
appuifw.app.orientation = "landscape"

sensors = sensor.sensors()
acc = sensor.Sensor(sensors["AccSensor"]["id"], sensors["AccSensor"]["category"])
acc.connect(sensor_event)

e32.ao_sleep(0.7)
normalize = 1
reset()

while 1:
	e32.ao_sleep(2)
	e32.reset_inactivity()

acc.disconnect()

# Copyright Janne Raiskila 2008
# All rights reserved
# This file to be made available only at http://acdx.net/n95_acc_grapher.py