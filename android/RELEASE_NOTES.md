# REC Remote Pad 1.0.7

- Corrected swapped left/right steering.
- Added a speed progress bar with PWM and percent; confirmed robot feedback is distinguished from pending targets.
- Supports both original REC_test.ino `Speed: ... Drive: ...` output and REC1 telemetry. Queries speed on connect and speed changes.
- Pause now latches, clears held actions, disables movement/speed controls, and repeats priority stops until Continue is pressed. Continue requires a fresh direction press.
- Rapid speed taps no longer collapse into one queued +/- byte.
- Regression checks cover legacy feedback, speed confirmation, stop retries, pause/resume, and all controls on a small landscape screen.
