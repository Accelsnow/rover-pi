# rover-pi
A simple rover project using Raspberry Pi and Arduino. Implemented with Arduino language and Java.

## background
This project was coded & completed in Feb 2017 as a final project of my grade 10 engineering project. The actual project itself has been disassembled (i.e. the project is no longer in maintenance).

## hardware
Raspberry Pi, Arduino, ultrasonic sensors, LED, beepers, rover parts, motors, general electronics(resistors, wires, breadboards, etc.). All purchased online(mainly amazon).

## feature
- wifi remote control: the swing GUI control panel is accessable through connecting directly to Pi's ssh via remote desktop softwares (i.e. VNC).
- full manual control: rover can be manually controlled to turn & move in all directions with adjestable speed, or it can be set to move by itself with auto avoidance on. A beeper is also mounted, which can be manually triggered.
- auto avoidance: the rover tries to avoid obstacles that blocks its path with no specific logic preference using ultrasonar devices.
- rotating camera: the Raspberry Pi module is mounted with a camera, which loads on a motor that rotates 180 degrees in the front of the rover. The video files are stored locally on Pi and can be accessed through SSH.
- auto front light: the rover turn on its front light when it senses the light itensity drops below a certain threshold.
