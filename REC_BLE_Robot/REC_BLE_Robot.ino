/*
  REC iPhone BLE remote companion — Arduino Nano/Uno (ATmega328P).
  L298N: IN1=D2 IN2=D3 IN3=D4 IN4=D7 ENA=D5 ENB=D6.
  Positional servos: arm=D9, gripper=D10.
  BLE UART bridge on hardware Serial D0/D1, UART_BAUD=9600.
  BLE module must provide FFE0/FFE1 or Nordic UART Service; HC-05
  does NOT work with this iPhone app. Match the module's UART baud.

  F/B/L/R drive; S stops wheels; +/- speed; W/X arm +/-;
  E/C grip +/-; J freezes arm; K freezes grip; ! stops all; P status.
  Repeat motion commands while holding buttons. Each actuator expires
  independently after 600 ms. Non-motion commands never refresh leases.
  Disconnect module UART wires before uploading. See README for wiring.
*/
#include <Arduino.h>
#include <Servo.h>

const uint8_t IN1 = 2, IN2 = 3, IN3 = 4, IN4 = 7;
const uint8_t ENA = 5, ENB = 6, ARM_PIN = 9, GRIP_PIN = 10;
const unsigned long UART_BAUD = 9600;
const unsigned long COMMAND_TIMEOUT_MS = 600;
const unsigned long SERVO_STEP_MS = 15;
const unsigned long REVERSAL_PAUSE_MS = 50;

// Calibration values inherited from your original REC_test sketch.
// Confirm these with an unloaded mechanism before competition.
const int ARM_OFFSET = 10, GRIP_OFFSET = 0;
const int ARM_MIN = 0, ARM_MAX = 155, GRIP_MIN = 30, GRIP_MAX = 150;
const int NUDGE = 5, SPEED_MIN = 80, SPEED_MAX = 255, SPEED_STEP = 20;
int driveSpeed = 200;
Servo arm, grip;
int armPos = 90, gripPos = 90, armTarget = 90, gripTarget = 90;
int dirL = 0, dirR = 0, requestedL = 0, requestedR = 0;
bool moving = false, waitingForDirection = false;
bool statusRequested = false;
unsigned long directionRequestedAt = 0;
unsigned long lastDriveCommand = 0, lastArmCommand = 0, lastGripCommand = 0;
unsigned long lastServoStep = 0, lastStatus = 0;

void writeMotor(uint8_t a, uint8_t b, uint8_t enable, int direction) {
  digitalWrite(a, direction > 0 ? HIGH : LOW);
  digitalWrite(b, direction < 0 ? HIGH : LOW);
  analogWrite(enable, direction == 0 ? 0 : driveSpeed);
}

void applyDrive() {
  writeMotor(IN1, IN2, ENA, dirL);
  writeMotor(IN3, IN4, ENB, dirR);
}

void stopDrive() {
  analogWrite(ENA, 0);
  analogWrite(ENB, 0);
  digitalWrite(IN1, LOW); digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW); digitalWrite(IN4, LOW);
  dirL = 0; dirR = 0;
  requestedL = 0; requestedR = 0;
  moving = false;
  waitingForDirection = false;
}

void stopAll() {
  stopDrive();
  armTarget = armPos;
  gripTarget = gripPos;
  // Positional servos keep holding their current position.
  // This is a software motion stop; the physical switch cuts power.
}

void requestDrive(int left, int right) {
  lastDriveCommand = millis();
  if (waitingForDirection && left == requestedL && right == requestedR) return;
  if (moving && left == dirL && right == dirR) return;
  stopDrive();
  requestedL = left; requestedR = right;
  directionRequestedAt = millis();
  waitingForDirection = true;
}

void writeServos() {
  arm.write(constrain(armPos + ARM_OFFSET, 0, 180));
  grip.write(constrain(gripPos + GRIP_OFFSET, 0, 180));
}

void printStatus() {
  Serial.print(F("REC1 PWM=")); Serial.print(driveSpeed);
  Serial.print(F(" ARM=")); Serial.print(armPos);
  Serial.print(F(" GRIP=")); Serial.print(gripPos);
  Serial.print(F(" DRIVE="));
  Serial.println(moving ? F("MOVING") : waitingForDirection ? F("STARTING") : F("STOPPED"));
  lastStatus = millis();
}

void handleCommand(char command) {
  if (command >= 'a' && command <= 'z') command -= ('a' - 'A');
  switch (command) {
    case 'F': requestDrive(1, 1); break;
    case 'B': requestDrive(-1, -1); break;
    case 'L': requestDrive(-1, 1); break;
    case 'R': requestDrive(1, -1); break;
    case 'S': stopDrive(); break;
    case '!': stopAll(); break;
    case '+':
      driveSpeed = min(driveSpeed + SPEED_STEP, SPEED_MAX);
      if (moving) applyDrive();
      statusRequested = true; break;
    case '-':
      driveSpeed = max(driveSpeed - SPEED_STEP, SPEED_MIN);
      if (moving) applyDrive();
      statusRequested = true; break;
    case 'W':
      armTarget = constrain(armTarget + NUDGE, ARM_MIN, ARM_MAX);
      lastArmCommand = millis(); break;
    case 'X':
      armTarget = constrain(armTarget - NUDGE, ARM_MIN, ARM_MAX);
      lastArmCommand = millis(); break;
    case 'E':
      gripTarget = constrain(gripTarget + NUDGE, GRIP_MIN, GRIP_MAX);
      lastGripCommand = millis(); break;
    case 'C':
      gripTarget = constrain(gripTarget - NUDGE, GRIP_MIN, GRIP_MAX);
      lastGripCommand = millis(); break;
    case 'J': armTarget = armPos; break;
    case 'K': gripTarget = gripPos; break;
    case 'P': statusRequested = true; break;
    case '\r': case '\n': case ' ': break;
    default: break; // Unknown bytes do not renew any actuator lease.
  }
}

void updateRobot() {
  const unsigned long now = millis();
  // Check expiry before starting a queued direction or moving a servo.
  if ((moving || waitingForDirection) && now - lastDriveCommand >= COMMAND_TIMEOUT_MS) stopDrive();
  if (now - lastArmCommand >= COMMAND_TIMEOUT_MS) armTarget = armPos;
  if (now - lastGripCommand >= COMMAND_TIMEOUT_MS) gripTarget = gripPos;
  if (waitingForDirection && now - directionRequestedAt >= REVERSAL_PAUSE_MS) {
    dirL = requestedL; dirR = requestedR;
    waitingForDirection = false;
    moving = true;
    applyDrive();
  }
  if (now - lastServoStep >= SERVO_STEP_MS) {
    lastServoStep = now;
    if (armPos < armTarget) ++armPos;
    else if (armPos > armTarget) --armPos;
    if (gripPos < gripTarget) ++gripPos;
    else if (gripPos > gripTarget) --gripPos;
    writeServos();
  }
  // Coalesce status requests so repeated P/+/- bytes cannot flood UART
  // output and delay the motion watchdog checks.
  if (now - lastStatus >= 1000 || (statusRequested && now - lastStatus >= 100)) {
    printStatus();
    statusRequested = false;
  }
}

void setup() {
  const uint8_t pins[] = {IN1, IN2, IN3, IN4, ENA, ENB};
  for (uint8_t i = 0; i < sizeof(pins) / sizeof(pins[0]); ++i) {
    digitalWrite(pins[i], LOW);
    pinMode(pins[i], OUTPUT);
  }
  stopAll();
  Serial.begin(UART_BAUD);
  writeServos();
  arm.attach(ARM_PIN);
  grip.attach(GRIP_PIN);
  printStatus();
}

void loop() {
  // Limit parsing work so a noisy UART cannot starve the timeout checks.
  uint8_t budget = 16;
  while (budget-- && Serial.available() > 0) handleCommand(Serial.read());
  updateRobot();
}
