export const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
const groups = { drive: /^[FBLR]$/, arm: /^[WX]$/, grip: /^[EC]$/ };
const releaseCommands = { drive: 'S', arm: 'J', grip: 'K' };

export function parseStatus(line) {
  const match = /^REC1 PWM=(\d+) ARM=(\d+) GRIP=(\d+) DRIVE=(MOVING|STARTING|STOPPED)$/.exec(line.trim());
  if (!match) return null;
  const [, speed, arm, grip, drive] = match;
  if (+speed < 80 || +speed > 255 || +arm > 180 || +grip > 180) return null;
  return { speed: +speed, arm: +arm, grip: +grip, drive };
}

export class CommandQueue {
  constructor(write, onError, now = () => Date.now()) {
    this.write = write; this.onError = onError; this.now = now;
    this.items = []; this.running = false; this.closed = false;
  }
  send(command, priority = false) {
    if (this.closed) return;
    if (priority) this.items = [];
    else this.items = this.items.filter(item => item.command !== command);
    if (this.items.length >= 8) return;
    this.items.push({ command, priority, at: this.now() });
    void this.pump();
  }
  async pump() {
    if (this.running || this.closed) return;
    this.running = true;
    try {
      while (!this.closed && this.items.length) {
        const item = this.items.shift();
        if (!item.priority && this.now() - item.at > 240) continue;
        await this.write(new TextEncoder().encode(item.command));
      }
    } catch (error) {
      this.items = []; this.closed = true; this.onError(error);
    } finally { this.running = false; }
  }
  close() { this.closed = true; this.items = []; }
}

export class RobotController {
  constructor({ onChange = () => {}, onNotice = () => {}, now = () => Date.now() } = {}) {
    this.onChange = onChange; this.onNotice = onNotice; this.now = now;
    this.transport = null; this.active = new Map(); this.lastTick = now(); this.lastFeedback = 0;
    this.state = { mode: 'idle', ready: false, locked: false, name: '', speed: null, arm: null, grip: null,
      drive: 'IDLE', lastCommand: '', feedback: '', x: 0, y: 0, heading: 0 };
  }
  emit() { this.onChange(this.state); }
  demo() {
    if (this.transport) return;
    this.active.clear();
    Object.assign(this.state, { mode: 'demo', ready: true, locked: false, name: '界面演示 · 未连接机器人',
      speed: 200, arm: 90, grip: 90, drive: 'STOPPED', lastCommand: '', feedback: '演示数据，不会向机器人发送命令。', x: 0, y: 0, heading: 0 });
    this.lastTick = this.now(); this.emit();
  }
  attach(transport, name) {
    this.active.clear(); this.transport = transport; this.lastFeedback = this.now(); this.lastTick = this.now();
    Object.assign(this.state, { mode: 'connecting', ready: false, locked: false, name, speed: null,
      arm: null, grip: null, drive: 'IDLE', feedback: '正在等待 REC1 固件握手…', x: 0, y: 0, heading: 0 });
    this.send('!', true); this.send('P'); this.emit();
  }
  receive(line) {
    if (!this.transport) return;
    const parsed = parseStatus(line);
    if (!parsed) return;
    this.lastFeedback = this.now();
    Object.assign(this.state, parsed, { feedback: line, mode: 'robot', ready: true });
    this.emit();
  }
  start(group, token, command) {
    if (!this.state.ready || this.state.locked || !groups[group]?.test(command)) return false;
    this.active.set(group, { token, command }); this.send(command); this.emit(); return true;
  }
  release(group, token) {
    if (this.active.get(group)?.token !== token) return;
    this.active.delete(group); this.send(releaseCommands[group], true); this.emit();
  }
  stop(latch = true) {
    this.active.clear();
    if (this.state.ready || this.transport) this.send('!', true);
    this.state.locked = latch && this.state.ready;
    this.emit();
  }
  resume() { this.active.clear(); this.state.locked = false; this.emit(); }
  speed(faster) { if (this.state.ready) { this.send(faster ? '+' : '-'); this.emit(); } }
  status() { if (this.state.ready) this.send('P'); }
  send(command, priority = false) {
    if (this.state.mode === 'demo') {
      const s = this.state;
      if (/^[FBLR]$/.test(command)) { s.drive = command; }
      if (command === 'S' || command === '!') s.drive = 'STOPPED';
      if (command === '+') s.speed = clamp(s.speed + 20, 80, 255);
      if (command === '-') s.speed = clamp(s.speed - 20, 80, 255);
      if (command === 'W') s.arm = clamp(s.arm + 5, 0, 155);
      if (command === 'X') s.arm = clamp(s.arm - 5, 0, 155);
      if (command === 'E') s.grip = clamp(s.grip + 5, 30, 150);
      if (command === 'C') s.grip = clamp(s.grip - 5, 30, 150);
      s.lastCommand = command;
      s.feedback = `演示 · PWM ${s.speed} / ARM ${s.arm}° / GRIP ${s.grip}° · 未连接机器人`;
    } else if (this.transport) {
      this.transport.send(command, priority);
      this.state.lastCommand = command;
    }
  }
  tick() {
    const now = this.now(), elapsed = now - this.lastTick;
    this.lastTick = now;
    if (elapsed > 400 && this.active.size) {
      this.stop(true); this.onNotice('操作被打断，已停止。请松开按钮后恢复操作。'); return;
    }
    if (this.transport && now - this.lastFeedback > (this.state.ready ? 3000 : 10000)) {
      void this.disconnect(); this.onNotice('固件反馈超时，已断开。请检查波特率和 TX/RX 接线。'); return;
    }
    if (this.state.ready && !this.state.locked) {
      for (const { command } of this.active.values()) this.send(command);
      if (this.state.mode === 'demo') {
        const s = this.state, dt = Math.min(elapsed, 200) / 1000;
        if (s.drive === 'L') s.heading -= 95 * dt;
        if (s.drive === 'R') s.heading += 95 * dt;
        if (s.drive === 'F' || s.drive === 'B') {
          const distance = (s.drive === 'F' ? 1 : -1) * (s.speed / 255) * 33 * dt;
          s.x = clamp(s.x + Math.sin(s.heading * Math.PI / 180) * distance, -45, 45);
          s.y = clamp(s.y - Math.cos(s.heading * Math.PI / 180) * distance, -27, 27);
        }
        if (this.active.size || s.drive !== 'STOPPED') this.emit();
      }
    }
  }
  async disconnect() {
    this.stop(false);
    const transport = this.transport;
    this.transport = null; this.active.clear();
    Object.assign(this.state, { mode: 'idle', ready: false, locked: false, name: '', speed: null, arm: null,
      grip: null, drive: 'IDLE', lastCommand: '', feedback: '连接已断开。', x: 0, y: 0, heading: 0 });
    this.emit();
    if (transport) { try { await transport.close(); } catch {} }
  }
}
