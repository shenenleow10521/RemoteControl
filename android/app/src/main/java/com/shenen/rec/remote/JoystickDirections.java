package com.shenen.rec.remote;

/** Four-way joystick for the existing F/B/L/R/S Arduino protocol. */
public final class JoystickDirections {
    private JoystickDirections() {}
    public static char direction(float x, float y, float radius, char previous) {
        if (radius <= 0 || !Float.isFinite(x) || !Float.isFinite(y)
                || Math.hypot(x, y) <= radius * 0.24f) return 'S';
        float ax = Math.abs(x), ay = Math.abs(y);
        // Keep the current axis near a diagonal to avoid rapid direction chatter.
        if ((previous == 'F' || previous == 'B') && ax < ay * 1.2f) return y < 0 ? 'F' : 'B';
        if ((previous == 'L' || previous == 'R') && ay < ax * 1.2f) return x < 0 ? 'L' : 'R';
        return ax > ay ? (x < 0 ? 'L' : 'R') : (y < 0 ? 'F' : 'B');
    }
}
