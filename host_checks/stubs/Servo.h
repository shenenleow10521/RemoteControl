#pragma once
struct Servo {
  int angle = 0;
  void write(int value) { angle = value; }
  void attach(int) {}
};
