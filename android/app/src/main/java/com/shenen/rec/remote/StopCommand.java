package com.shenen.rec.remote;

/** Separate stop commands for both byte-at-a-time and line-oriented sketches. */
public final class StopCommand {
    private StopCommand() {}
    public static final String FRAME = "\r\nS\r\n!\r\nP\r\n";
}
