package com.shenen.rec.remote;

/** Maps semantic remote buttons; raw terminal commands bypass this mapping. */
public final class ButtonMapping {
    private ButtonMapping() {}
    public static char map(char command, boolean reverseDrive, boolean reverseArm, boolean reverseGrip) {
        if (command == 'L') return 'R';
        if (command == 'R') return 'L';
        if (reverseDrive && command == 'F') return 'B';
        if (reverseDrive && command == 'B') return 'F';
        if (reverseArm && command == 'W') return 'X';
        if (reverseArm && command == 'X') return 'W';
        if (reverseGrip && command == 'E') return 'C';
        if (reverseGrip && command == 'C') return 'E';
        return command;
    }
}
