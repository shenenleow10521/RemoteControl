package com.shenen.rec.remote;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.SystemClock;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** RFCOMM SPP client for already-paired HC-05/HC-06 modules. No discovery/location. */
@SuppressLint("MissingPermission")
public final class ClassicBluetooth {
    public interface Listener {
        void connected(ControllerEngine.Sink sink, String name);
        void line(String text);
        void chunk(String text);
        void failed(String reason);
    }
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final Handler main;
    private final Listener listener;
    private Session current;
    private volatile String lastWrite = "No completed write yet";
    public String diagnostics() { return "Bluetooth: " + (isConnected()?"connected":"disconnected") + "\nLast completed write: " + lastWrite; }
    public ClassicBluetooth(Handler main, Listener listener) { this.main = main; this.listener = listener; }

    public void connect(BluetoothDevice device) {
        disconnect(); lastWrite = "No completed write yet";
        Session session = new Session(device); current = session;
        Thread thread = new Thread(() -> {
            try {
                session.socket = device.createRfcommSocketToServiceRecord(SPP);
                if (session.closing) { session.forceClose(); return; }
                session.socket.connect();
                if (session.closing) { session.forceClose(); return; }
                session.output = session.socket.getOutputStream();
                session.input = session.socket.getInputStream();
                session.connected = true;
                String name = device.getName() == null ? "Bluetooth SPP robot" : device.getName();
                main.post(() -> { if (current == session && !session.closing) listener.connected(session, name); });
                session.readLoop();
            } catch (Exception error) { session.fail(error); }
        }, "rec-connect-reader");
        thread.setDaemon(true); thread.start();
        main.postDelayed(() -> {
            if (current == session && !session.connected && !session.closing) {
                current = null; session.forceClose();
                listener.failed("Bluetooth connection timed out. Check pairing, power, and that no other phone is connected.");
            }
        }, 12000);
    }
    public void disconnect() {
        Session old = current; current = null;
        if (old != null) old.close();
    }
    public boolean isConnected() { return current != null && current.connected && !current.closing && !current.closeScheduled; }
    public void sendText(String text, boolean priority) {
        Session session = current;
        if (session != null && session.connected) session.sendText(text, priority);
    }
    private static final class Command {
        final String value; final boolean priority; final long at;
        Command(String value, boolean priority) { this.value = value; this.priority = priority; at = SystemClock.elapsedRealtime(); }
    }
    private final class Session implements ControllerEngine.Sink {
        final BluetoothDevice device;
        volatile BluetoothSocket socket;
        volatile InputStream input;
        volatile OutputStream output;
        volatile boolean closing, connected;
        final ArrayDeque<Command> queue = new ArrayDeque<>();
        final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "rec-writer"); t.setDaemon(true); return t;
        });
        boolean draining, closeScheduled;
        Session(BluetoothDevice device) { this.device = device; }
        @Override public synchronized void send(char c, boolean priority) {
            sendText(String.valueOf(c), priority);
        }
        @Override public void stopAll() { sendText(StopCommand.FRAME, true); }
        synchronized void sendText(String text, boolean priority) {
            if (closing || output == null || closeScheduled) return;
            if (text.length() > 512) return;
            enqueue(text, priority);
        }
        private synchronized void enqueue(String c, boolean priority) {
            if (priority) queue.clear();
            else {
                Iterator<Command> it = queue.iterator();
                while (it.hasNext()) if (it.next().value.equals(c) && !c.equals("+") && !c.equals("-")) it.remove();
            }
            if (queue.size() >= 32) return;
            queue.add(new Command(c, priority));
            if (!draining) { draining = true; writer.execute(this::drain); }
        }
        private void drain() {
            try {
                while (!closing) {
                    Command command;
                    synchronized (this) {
                        command = queue.poll();
                        if (command == null) { draining = false; return; }
                    }
                    long ttl = command.value.equals("+") || command.value.equals("-") ? 1000 : 240;
                    if (!command.priority && SystemClock.elapsedRealtime() - command.at > ttl) continue;
                    OutputStream out = output;
                    if (out == null) return;
                    out.write(command.value.getBytes(StandardCharsets.UTF_8)); out.flush();
                    lastWrite = command.value.replace("\r","<CR>").replace("\n","<LF>");
                }
            } catch (Exception error) { fail(error); }
            finally { synchronized (this) { draining = false; } }
        }
        void readLoop() throws Exception {
            InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
            char[] characters = new char[128]; StringBuilder text = new StringBuilder();
            while (!closing) {
                int count = reader.read(characters);
                if (count < 0) throw new java.io.IOException("Bluetooth disconnected");
                String chunk = new String(characters, 0, count);
                main.post(() -> { if (current == this && !closing) listener.chunk(chunk); });
                text.append(chunk);
                if (text.length() > 2048) text.delete(0, text.length() - 1024);
                int end;
                while ((end = text.indexOf("\n")) >= 0) {
                    String line = text.substring(0, end).trim(); text.delete(0, end + 1);
                    if (!line.isEmpty()) main.post(() -> { if (current == this && !closing) listener.line(line); });
                }
            }
        }
        void fail(Exception error) {
            if (closing || closeScheduled) return;
            forceClose();
            main.post(() -> {
                if (current == this) {
                    current = null;
                    listener.failed(error.getMessage() == null ? "Bluetooth connection failed" : error.getMessage());
                }
            });
        }
        @Override public synchronized void close() {
            if (closing || closeScheduled) return;
            closeScheduled = true; queue.clear();
            if (output != null) enqueue(StopCommand.FRAME, true);
            // Socket close cancels blocked connect/read/write, even if stop delivery fails.
            main.postDelayed(this::forceClose, 250);
        }
        synchronized void forceClose() {
            closing = true; queue.clear(); writer.shutdownNow();
            try { if (socket != null) socket.close(); } catch (Exception ignored) { }
        }
    }
}
