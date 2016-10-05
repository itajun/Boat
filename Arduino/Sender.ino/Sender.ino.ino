#include <SoftwareSerial.h>

const int ANALOG_IN_PIN = A0;
const int ANALOG_OUT_PIN = A1;
const int LENGTH_EOC = 200;
const int LENGTH_STEP = 10;
const int CALIBRATION_TIME = 2000;
const int MIN_ACCEP_SENSOR_VAL = 20;

int sensorMax = 0;
int sensorMin = 1023;
int sensorValue;
int total;
unsigned long prevTimeValue = 0;
unsigned long prevTimeZero = 0;
unsigned int zeroCount = 0;
unsigned int valueCount = 0;

void printSensor() {
  Serial.print("\t sensorMin = ");
  Serial.print(sensorMin);
  Serial.print("\t sensorMax = ");
  Serial.println(sensorMax);
}

void setup() {
  Serial.begin(9600);

  pinMode(13, OUTPUT);

  int offSet = 0;
  while (sensorMax < MIN_ACCEP_SENSOR_VAL) {
    digitalWrite(13, HIGH);
    while (millis() - offSet < CALIBRATION_TIME) {
      sensorValue = analogRead(ANALOG_IN_PIN);
  
      if (sensorValue > sensorMax) {
        sensorMax = sensorValue;
      }
  
      if (sensorValue < sensorMin) {
        sensorMin = sensorValue;
      }
    }
    digitalWrite(13, LOW);
    if (sensorMax < MIN_ACCEP_SENSOR_VAL) {
      delay(500);
      offSet = millis();
    }

    printSensor();
  }

  prevTimeZero = millis();
  prevTimeValue = prevTimeZero + 1;
}

void loop() {
  sensorValue = analogRead(ANALOG_IN_PIN);
  sensorValue = map(sensorValue, sensorMin, sensorMax, 0, 10);

  if (sensorValue == 0) {
    if (zeroCount > valueCount && (millis() - prevTimeZero >= LENGTH_EOC)) {
      total = total - (total % LENGTH_EOC);
      if (valueCount > 0 && total > 0) {
        Serial.print("\t prev_time_zero = ");
        Serial.print(prevTimeZero);
        Serial.print("\t prev_time_value = ");
        Serial.print(prevTimeValue);
        Serial.print("\t zeroCount = ");
        Serial.print(zeroCount);
        Serial.print("\t valueCount = ");
        Serial.print(valueCount);
        Serial.print("\t sensorMin = ");
        Serial.print(sensorMin);
        Serial.print("\t sensorMax = ");
        Serial.print(sensorMax);
        Serial.print("\t millis = ");
        Serial.print(millis());
        Serial.print("\t length = ");
        Serial.println(total);
        tone(A1, 1000, total);
      }
      prevTimeZero = millis();
      valueCount = 0;
    }
    zeroCount++;
  } else {
    if (valueCount == 0) {
      prevTimeValue = millis();
    }
    total = millis() - prevTimeValue;
    zeroCount = 0;
    valueCount++;
  }
}
