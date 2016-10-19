#include <SoftwareSerial.h>

const int ANALOG_IN_PIN = A0;
const int ANALOG_OUT_PIN = A1;
const int DIGITAL_MOTOR_PIN = 8;
const int PWM_SERVO_PIN = 11;
const int LED_PIN = 13;

const int TONE_FREQUENCY = 1000;

const int LENGTH_EOP = 50;
const int LENGTH_EOI = 10;

const int MIN_ACCEP_SENSOR_VAL = 20;
const byte BUFFER_SIZE = 5;

unsigned short sensorMax = 0;
unsigned short sensorMin = 1023;

unsigned int accumulatedValueLen;

unsigned long prevTimeValue = 0;
unsigned long prevTimeZero = 0;

unsigned int zeroCount = 0;
unsigned int valueCount = 0;

byte idxInBuffer = 0;
unsigned short inBuffer[BUFFER_SIZE] = {};

byte idxOutBuffer = 0;
unsigned short outBuffer[BUFFER_SIZE] = {};
unsigned long sendSignalStarted = 0;
boolean sendingCommand = false;

void setup() {
  Serial.begin(9600);

  pinMode(ANALOG_IN_PIN, INPUT);
  pinMode(ANALOG_OUT_PIN, OUTPUT);
  pinMode(PWM_SERVO_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);

  while ((sensorMax - sensorMin) < MIN_ACCEP_SENSOR_VAL || sensorMax == 0) {
    digitalWrite(LED_PIN, HIGH);
    unsigned short sensorValue = analogRead(ANALOG_IN_PIN);
    if (sensorValue > sensorMax) {
      sensorMax = sensorValue;
    }
    if (sensorValue < sensorMin) {
      sensorMin = sensorValue;
    }
    if ((millis() / 1000) % 3 == 0) {
      tone(ANALOG_OUT_PIN, 1000);
      delay(1000);
      noTone(ANALOG_OUT_PIN);
    }
    digitalWrite(LED_PIN, LOW);
  }

  printCalibration();
}

void loop() {
  readCycle();
  execCycle();
  sendCycle();
}

void readCycle() {
  unsigned short sensorValue = analogRead(ANALOG_IN_PIN);

  if (sensorValue > sensorMax) {
    sensorMax = sensorValue;
  }
  if (sensorValue < sensorMin) {
    sensorMin = sensorValue;
  }

  sensorValue = map(sensorValue, sensorMin, sensorMax, 0, 2);

  if (sensorValue == 0) {
    if (zeroCount == 0) {
      prevTimeZero = millis();
    }

    zeroCount++;

    if ((millis() - prevTimeZero >= LENGTH_EOI)) {
      if (idxInBuffer < BUFFER_SIZE) {
        if (accumulatedValueLen > 0) {
          inBuffer[idxInBuffer++] = accumulatedValueLen;
          accumulatedValueLen = 0;
        }

        if (millis() - prevTimeZero >= LENGTH_EOP) {
          if (idxInBuffer > 0) {
            // Indicate that we won't read anything until it is cleared
            idxInBuffer = BUFFER_SIZE;
          }
        }
      }

      valueCount = 0;
    }
  } else {
    if (valueCount == 0) {
      prevTimeValue = millis();
    }
    valueCount++;
    zeroCount = 0;

    accumulatedValueLen = millis() - prevTimeValue;
  }
}

void execCycle() {
  if (idxInBuffer == BUFFER_SIZE) {
    // printCalibration();
     printDebug();
    if (inBuffer[0] < 15) {
      commandEcho();
    } else if (inBuffer[0] < 25) {
      commandMotor();
    } else if (inBuffer[0] < 35) {
      commandServo();
    } else if (inBuffer[0] < 45) {
      commandLED();
    }

    clearInBuffer();
  }
}

void sendCycle() {
  if (sendingCommand && millis() - sendSignalStarted >= outBuffer[idxOutBuffer]) {
    noTone(ANALOG_OUT_PIN);
    outBuffer[idxOutBuffer] = 0;
    ++idxOutBuffer == BUFFER_SIZE ? 0 : idxOutBuffer;
    sendSignalStarted = millis() + ((LENGTH_EOI + LENGTH_EOP) / 2);
    if (outBuffer[idxOutBuffer] == 0) {
      sendSignalStarted += LENGTH_EOP;
      clearOutBuffer();
    }
    sendingCommand = false;
  }
  if (!sendingCommand && outBuffer[idxOutBuffer] > 0 && sendSignalStarted < millis()) {
    tone(ANALOG_OUT_PIN, TONE_FREQUENCY);
    sendSignalStarted = millis();
    sendingCommand = true;
  }
}

void outputCommand(unsigned short values[]) {
  if (sendingCommand) {
    return;
  }
  
  for (int i = 0; i < BUFFER_SIZE; i++) {
    outBuffer[i] = values[i];
  }
}

void clearInBuffer() {
  for (idxInBuffer = BUFFER_SIZE - 1; idxInBuffer > 0; idxInBuffer--) {
    inBuffer[idxInBuffer] = 0;
  }
}

void clearOutBuffer() {
  for (idxOutBuffer = BUFFER_SIZE - 1; idxOutBuffer > 0; idxOutBuffer--) {
    outBuffer[idxOutBuffer] = 0;
  }
}

void commandMotor() {
  if (inBuffer[1] < 50) {
    digitalWrite(DIGITAL_MOTOR_PIN, HIGH);
  } else {
    digitalWrite(DIGITAL_MOTOR_PIN, LOW);
  }
}

void commandServo() {
  analogWrite(PWM_SERVO_PIN, inBuffer[1]);
}

void commandLED() {
  if (inBuffer[1] < 50) {
    digitalWrite(LED_PIN, HIGH);
  } else {
    digitalWrite(LED_PIN, LOW);
  }
}

void commandEcho() {
  outputCommand(inBuffer);
}

void printCalibration() {
  Serial.print("\t sensorMin = ");
  Serial.print(sensorMin);
  Serial.print("\t sensorMax = ");
  Serial.println(sensorMax);
}

void printDebug() {
  Serial.print("inBuffer = ");
  Serial.print(inBuffer[0]);
  Serial.print(",");
  Serial.print(inBuffer[1]);
  Serial.print(",");
  Serial.print(inBuffer[2]);
  Serial.print(",");
  Serial.print(inBuffer[3]);
  Serial.print(",");
  Serial.print(inBuffer[4]);
  Serial.print("\toutBuffer = ");
  Serial.print(outBuffer[0]);
  Serial.print(",");
  Serial.print(outBuffer[1]);
  Serial.print(",");
  Serial.print(outBuffer[2]);
  Serial.print(",");
  Serial.print(outBuffer[3]);
  Serial.print(",");
  Serial.print(outBuffer[4]);
  Serial.print("\t prev_time_zero = ");
  Serial.print(prevTimeZero);
  Serial.print("\t prev_time_value = ");
  Serial.println(prevTimeValue);
}

