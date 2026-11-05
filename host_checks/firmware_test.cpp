#include <cassert>
#include <iostream>
#include "Arduino.h"
unsigned long fakeTime = 0;
int pwmPins[32] = {};
int digitalPins[32] = {};
FakeSerial Serial;
#include "../REC_BLE_Robot/REC_BLE_Robot.ino"

void advance(unsigned long interval) { fakeTime += interval; updateRobot(); }
int main() {
  setup();
  assert(!moving && pwmPins[ENA] == 0 && pwmPins[ENB] == 0);
  assert(Serial.output.str().find("REC1 PWM=200") != std::string::npos);
  handleCommand('F');
  assert(waitingForDirection && !moving);
  advance(49); assert(!moving);
  advance(1); assert(moving && dirL == 1 && dirR == 1 && pwmPins[ENA] == 200);
  advance(400); handleCommand('F');
  assert(moving && !waitingForDirection); // repeats do not restart dead time
  advance(599); assert(moving);
  advance(1); assert(!moving && pwmPins[ENA] == 0);

  handleCommand('F'); advance(50);
  handleCommand('B'); assert(!moving && pwmPins[ENB] == 0);
  advance(50); assert(moving && dirL == -1 && dirR == -1);
  handleCommand('S'); assert(!moving);
  handleCommand('L'); advance(50); assert(dirL == -1 && dirR == 1);
  handleCommand('R'); advance(50); assert(dirL == 1 && dirR == -1);
  handleCommand('!'); assert(!moving && armTarget == armPos && gripTarget == gripPos);

  // Arm and status traffic cannot keep the drive alive after a lost touch.
  handleCommand('F'); advance(50);
  for (int i = 0; i < 6; ++i) { handleCommand('W'); handleCommand('P'); advance(100); }
  assert(!moving);
  const int before = armPos;
  advance(15); assert(armPos > before);
  handleCommand('J'); assert(armTarget == armPos);
  handleCommand('E'); assert(gripTarget > gripPos);
  handleCommand('K'); assert(gripTarget == gripPos);

  // Both servo targets expire independently, before another motion step.
  handleCommand('W'); handleCommand('E');
  const int heldArm = armPos, heldGrip = gripPos;
  advance(600);
  assert(armTarget == heldArm && armPos == heldArm);
  assert(gripTarget == heldGrip && gripPos == heldGrip);
  for (int i = 0; i < 100; ++i) { handleCommand('W'); handleCommand('E'); }
  assert(armTarget == ARM_MAX && gripTarget == GRIP_MAX);
  for (int i = 0; i < 100; ++i) { handleCommand('X'); handleCommand('C'); }
  assert(armTarget == ARM_MIN && gripTarget == GRIP_MIN);
  handleCommand('!');

  for (int i = 0; i < 20; ++i) handleCommand('+');
  assert(driveSpeed == 255);
  for (int i = 0; i < 20; ++i) handleCommand('-');
  assert(driveSpeed == 80);
  handleCommand('f'); advance(50); assert(moving && pwmPins[ENA] == 80);
  handleCommand('z'); advance(550); assert(!moving);
  // A pending direction cannot start after its lease already expired.
  handleCommand('F'); advance(600); assert(!moving && !waitingForDirection);
  // loop consumes a bounded UART batch; stop commands are parsed normally.
  Serial.input = {'F', '!', '\n'}; loop(); assert(!moving && !waitingForDirection);
  std::cout << "PASS: startup, directions, reversal pause, repeated holds, independent watchdogs, servo bounds, speed clamps, stop and UART parsing\n";
}
