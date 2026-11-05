#pragma once
#include <stdint.h>
#include <algorithm>
#include <sstream>
#include <deque>
using std::min;
using std::max;
#define HIGH 1
#define LOW 0
#define OUTPUT 1
#define F(value) value
extern unsigned long fakeTime;
extern int pwmPins[32];
extern int digitalPins[32];
inline unsigned long millis() { return fakeTime; }
inline void pinMode(uint8_t, uint8_t) {}
inline void digitalWrite(uint8_t pin, int value) { digitalPins[pin] = value; }
inline void analogWrite(uint8_t pin, int value) { pwmPins[pin] = value; }
template<class T> T constrain(T value, T low, T high) { return std::max(low, std::min(high, value)); }
struct FakeSerial {
  std::stringstream output;
  std::deque<char> input;
  void begin(unsigned long) {}
  int available() { return input.size(); }
  char read() { auto c = input.front(); input.pop_front(); return c; }
  template<class T> void print(T value) { output << value; }
  template<class T> void println(T value) { output << value << '\n'; }
};
extern FakeSerial Serial;
