package com.shenen.rec.remote;

import java.util.EnumMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java control state; all calls occur on one owner thread. */
public final class ControllerEngine {
    public interface Clock { long now(); }
    public interface Sink {
        void send(char command, boolean priority);
        default void stopAll() { send('S', true); send('!', false); }
        void close();
    }
    public interface Listener { void changed(); void notice(String text); }
    public enum Group { DRIVE, ARM, GRIP }
    public enum Mode { IDLE, HANDSHAKE, ROBOT, TERMINAL, DEMO }
    private static final Pattern STATUS = Pattern.compile("^REC1 PWM=(\\d{1,3}) ARM=(\\d{1,3}) GRIP=(\\d{1,3}) DRIVE=(MOVING|STARTING|STOPPED)$");
    private static final Pattern LEGACY_STATUS = Pattern.compile("^Speed:\\s*(\\d{1,3})\\s+Drive:\\s*(moving|stopped)$", Pattern.CASE_INSENSITIVE);
    private static final class Held {
        final String owner; final char command;
        Held(String owner, char command) { this.owner = owner; this.command = command; }
    }
    private final Clock clock;
    private final Listener listener;
    private final EnumMap<Group, Held> held = new EnumMap<>(Group.class);
    private Sink sink;
    private long lastTick, lastFeedback, lastStop;
    public int requestedSpeed = -1;
    public boolean speedConfirmed, stopConfirmed;
    public Mode mode = Mode.IDLE;
    public boolean ready, locked;
    public int speed = -1, arm = -1, grip = -1;
    public String drive = "IDLE", feedback = "等待连接机器人", name = "", lastCommand = "—";

    public ControllerEngine(Clock clock, Listener listener) {
        this.clock = clock; this.listener = listener; lastTick = clock.now();
    }
    public void attach(Sink sink, String name) {
        if (this.sink != null) disconnect();
        held.clear(); this.sink = sink; this.name = name;
        mode = Mode.HANDSHAKE; ready = false; locked = false;
        speed = arm = grip = -1; requestedSpeed = -1; speedConfirmed = stopConfirmed = false; drive = "IDLE";
        lastFeedback = lastTick = clock.now(); feedback = "蓝牙已连接，等待配套固件 REC1 握手…";
        send('!', true); send('P', false); listener.changed();
    }
    /** Raw SPP mode works with existing sketches and requires no REC1 handshake. */
    public void attachTerminal(Sink sink, String name) {
        if (this.sink != null) disconnect();
        held.clear(); this.sink = sink; this.name = name;
        mode = Mode.TERMINAL; ready = true; locked = false;
        speed = arm = grip = -1; requestedSpeed = -1; speedConfirmed = stopConfirmed = false; drive = "IDLE";
        lastFeedback = lastTick = clock.now(); lastCommand = "—";
        feedback = "蓝牙串口已连接。透传模式，按 Arduino 现有程序执行指令。";
        send('P', false); listener.changed();
    }
    public void demo() {
        disconnect();
        mode = Mode.DEMO; ready = true; locked = false;
        name = "演示模式 · 未连接机器人"; speed = requestedSpeed = 200; speedConfirmed = true; arm = grip = 90; drive = "STOPPED";
        lastTick = clock.now(); feedback = "演示数据；不会发送到任何机器人。"; listener.changed();
    }
    public boolean start(Group group, String owner, char command) {
        if (!ready || locked || !valid(group, command)) return false;
        held.put(group, new Held(owner, command)); send(command, false); listener.changed(); return true;
    }
    public void release(Group group, String owner) {
        Held action = held.get(group);
        if (action == null || !action.owner.equals(owner)) return;
        held.remove(group); send(group == Group.DRIVE ? 'S' : group == Group.ARM ? 'J' : 'K', true);
        listener.changed();
    }
    public void stop(boolean latch) {
        held.clear();
        if (sink != null) { sink.stopAll(); lastCommand = "S!"; }
        else if (mode == Mode.DEMO) send('!', true);
        locked = ready && (locked || latch); stopConfirmed = mode == Mode.DEMO;
        lastStop = clock.now(); listener.changed();
    }
    public void resume() { held.clear(); locked = false; stopConfirmed = false; lastTick = clock.now(); listener.changed(); }
    public void changeSpeed(boolean faster) {
        if (!ready || locked) return;
        int baseline = requestedSpeed >= 0 ? requestedSpeed : speed;
        requestedSpeed = baseline < 0 ? -1 : clamp(baseline + (faster ? 20 : -20), 80, 255);
        speedConfirmed = false;
        send(faster ? '+' : '-', false);
        if (mode == Mode.DEMO) { requestedSpeed = speed; speedConfirmed = true; }
        else send('P', false);
        listener.changed();
    }
    public void status() { if (sink != null || mode == Mode.DEMO) { send('P', false); listener.changed(); } }
    public int heldCount() { return held.size(); }
    public void receive(String line) {
        if (sink == null) return;
        Matcher match = STATUS.matcher(line.trim());
        if (!match.matches()) {
            Matcher legacy = LEGACY_STATUS.matcher(line.trim());
            if (!legacy.matches()) return;
            int pwm = Integer.parseInt(legacy.group(1));
            if (pwm < 80 || pwm > 255) return;
            speed = requestedSpeed = pwm; speedConfirmed = true;
            drive = legacy.group(2).equalsIgnoreCase("moving") ? "MOVING" : "STOPPED";
            stopConfirmed = locked && drive.equals("STOPPED");
            feedback = line; listener.changed(); return;
        }
        int nextSpeed = Integer.parseInt(match.group(1)), nextArm = Integer.parseInt(match.group(2)), nextGrip = Integer.parseInt(match.group(3));
        if (nextSpeed < 80 || nextSpeed > 255 || nextArm > 180 || nextGrip > 180) return;
        speed = requestedSpeed = nextSpeed; speedConfirmed = true; arm = nextArm; grip = nextGrip; drive = match.group(4);
        stopConfirmed = locked && drive.equals("STOPPED");
        feedback = line; ready = true; mode = Mode.ROBOT; lastFeedback = clock.now(); listener.changed();
    }
    public void tick() {
        long now = clock.now(), delta = now - lastTick; lastTick = now;
        if (delta > 400 && !held.isEmpty()) {
            stop(true); listener.notice("操作被打断，已停止。请松开按钮并恢复操作。"); return;
        }
        if (sink != null && mode != Mode.TERMINAL && now - lastFeedback > (ready ? 3000 : 10000)) {
            disconnect(); listener.notice("固件反馈超时。检查配套程序、模块波特率和 TX/RX 接线。"); return;
        }
        if (ready && locked && sink != null && now - lastStop >= 240) {
            sink.stopAll(); lastCommand = "S!"; lastStop = now;
        }
        if (ready && !locked) {
            for (Held action : held.values()) send(action.command, false);
            if (mode == Mode.DEMO && !held.isEmpty()) listener.changed();
        }
    }
    public void disconnect() {
        stop(false); Sink previous = sink; sink = null; held.clear();
        mode = Mode.IDLE; ready = locked = false; speed = arm = grip = requestedSpeed = -1; speedConfirmed = stopConfirmed = false;
        drive = "IDLE"; name = ""; feedback = "连接已断开"; lastCommand = "—";
        if (previous != null) previous.close(); listener.changed();
    }
    private static boolean valid(Group group, char c) {
        return group == Group.DRIVE ? "FBLR".indexOf(c) >= 0 : group == Group.ARM ? "WX".indexOf(c) >= 0 : "EC".indexOf(c) >= 0;
    }
    private void send(char c, boolean priority) {
        if (mode == Mode.DEMO) {
            if ("FBLR".indexOf(c) >= 0) drive = String.valueOf(c);
            if (c == '!' || c == 'S') drive = "STOPPED";
            if (c == '+') speed = clamp(speed + 20, 80, 255);
            if (c == '-') speed = clamp(speed - 20, 80, 255);
            if (c == 'W') arm = clamp(arm + 5, 0, 155);
            if (c == 'X') arm = clamp(arm - 5, 0, 155);
            if (c == 'E') grip = clamp(grip + 5, 30, 150);
            if (c == 'C') grip = clamp(grip - 5, 30, 150);
            feedback = "演示 · PWM=" + speed + " ARM=" + arm + " GRIP=" + grip + " · 非真实反馈";
            lastCommand = "[演示] " + c;
        } else if (sink != null) { sink.send(c, priority); lastCommand = String.valueOf(c); }
    }
    private static int clamp(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
}
