#include <Servo.h>

//  income instruction
byte instruction;

//  control vars
boolean ON_OFF = true, ROTATE_CAM = true, FORCE_FNT_LIGHT = false, ENABLE_ALARM = false; 

//  serial message
String msg = "";

//  servo varsA
Servo myservo;
int counter = 1, degree = 500, rotate, servoPin = 9;

//  light sensor vars
int LDRpin = A1, LDRvalue = 0;

//  ultrasonic sensor vars
int CechoPin = 5, CtrigPin = 4, LechoPin = 7, LtrigPin = 6, RechoPin = 11, RtrigPin = 8;
long Cdistance, Ldistance, Rdistance;

//  temperature sensor vars
int tempPin = A0, tempValue = 0;

//  front light vars
int LEDfrontL = 23, LEDfrontR = 27;

//  piezo vars
int piezoPin = 3;

void setup() 
{ 
  //  start serial communication
  Serial.begin(9600);
  
  //  init servo
  myservo.attach(servoPin, 500, 2400);
  myservo.write(90);
  
  // init ultrasonic sensor
  pinMode(CtrigPin, OUTPUT);
  pinMode(CechoPin, INPUT);
  pinMode(LtrigPin, OUTPUT);
  pinMode(LechoPin, INPUT);
  pinMode(RtrigPin, OUTPUT);
  pinMode(RechoPin, INPUT);
  
  //  init piezo
  pinMode(piezoPin, OUTPUT);
  
  //  init front lights
  pinMode(LEDfrontL, OUTPUT);
  pinMode(LEDfrontR, OUTPUT);
} 

void loop() 
{
  
  while (Serial.available() > 0){
    instruction = Serial.read();
    
    switch (instruction){
      case 0:
        if (FORCE_FNT_LIGHT){
          FORCE_FNT_LIGHT = false;
        } else {
          FORCE_FNT_LIGHT = true;
          frontLightOn();
        }
        break;
        
      case 1:
        if (ENABLE_ALARM){
          ENABLE_ALARM = false;
          disableAlarm();
        } else {
          ENABLE_ALARM = true;
          enableAlarm();
        }
        break;
        
      case 2:
        if (ROTATE_CAM){
          ROTATE_CAM = false;
        } else {
          ROTATE_CAM = true;
        }
        break;
    }
  }
  
  if (ON_OFF){
    if (ROTATE_CAM){
      rotateServo();
    } else {
      msg += ":C:0:";
    }
  
    getUltraData();
  
    getTempData();
  
    if (!FORCE_FNT_LIGHT){
      lightSwitch();
    }
  }
  
  Serial.println(msg);
  msg = "";
  
  delay(100);
}

void getUltraData(){
  //  3-direction ultrasonic sensors
  Cdistance = calcDistance(CtrigPin, CechoPin);
  msg += ":d:";
  msg += Cdistance;
  
  Ldistance = calcDistance(LtrigPin, LechoPin);
  msg += ":";
  msg += Ldistance;
 
  Rdistance = calcDistance(RtrigPin, RechoPin);
  msg += ":";
  msg += Rdistance;
}

long calcDistance(int trig, int echo){
  //  calculate distance with given trig and echo pin number
  long dis = 0, dur, sum = 0, result;
  
  for (int n = 0; n < 3; n++){
    digitalWrite(trig, LOW); 
    delayMicroseconds(2); 
    digitalWrite(trig, HIGH);
    delayMicroseconds(10); 
    digitalWrite(trig, LOW);
  
    dur = pulseIn(echo, HIGH);
    
    dis = dur / 58.2;
    
    if (dis < 2000){
      sum += dis;
    } else {
      n--;
    }
    
    delay(5);
  }
  
  result = sum / 3;
  
  return result;
}

void getTempData(){
  //  temperature sensor
  tempValue = analogRead(tempPin);
  msg += ":t:";
  msg += tempValue;
}

void rotateServo(){
  //  servo rotate
  if (counter <= 95){
    rotate = 20;
  } else {
    rotate = -20;
  }
  
  if (counter == 189){
    counter = 1;
  }
  
  degree += rotate;
  myservo.writeMicroseconds(degree);
  counter++;
  msg += ":C:1:";
}

void lightSwitch(){
  LDRvalue = analogRead(A1);
  
  if (LDRvalue > 750){
    frontLightOn();
  } else {
    frontLightOff();
  }
}

void frontLightOn(){
  digitalWrite(LEDfrontL, HIGH);
  digitalWrite(LEDfrontR, HIGH);
  msg += ":L:1:";
}

void frontLightOff(){
  digitalWrite(LEDfrontL, LOW);
  digitalWrite(LEDfrontR, LOW);
  msg += ":L:0";
}

void enableAlarm(){
  tone(piezoPin, 2054);
  msg += ":A:1:";
}

void disableAlarm(){
  tone(piezoPin, 2054, 1);
  msg += ":A:0:";
}
