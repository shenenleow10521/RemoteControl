import { CommandQueue } from './controller.mjs';
const NUS = '6e400001-b5a3-f393-e0a9-e50e24dcca9e';
const RX = '6e400002-b5a3-f393-e0a9-e50e24dcca9e';
const TX = '6e400003-b5a3-f393-e0a9-e50e24dcca9e';
const HM = '0000ffe0-0000-1000-8000-00805f9b34fb';
const HM_CHAR = '0000ffe1-0000-1000-8000-00805f9b34fb';

class BaseTransport {
  constructor(onLine, onLost) { this.onLine = onLine; this.onLost = onLost; this.buffer = ''; this.closing = false; }
  consume(bytes) {
    this.buffer += new TextDecoder().decode(bytes);
    if (this.buffer.length > 2048) this.buffer = this.buffer.slice(-1024);
    let end;
    while ((end = this.buffer.indexOf('\n')) !== -1) {
      const line = this.buffer.slice(0, end).trim(); this.buffer = this.buffer.slice(end + 1);
      if (line) this.onLine(line);
    }
  }
  send(command, priority) { this.queue?.send(command, priority); }
  lost(error) { if (!this.closing) this.onLost(error); }
  async drainStop() {
    this.queue?.send('!', true);
    // Let an in-flight write and the stop complete, with a bounded close delay.
    const until = Date.now() + 250;
    while (this.queue?.running && Date.now() < until) await new Promise(resolve => setTimeout(resolve, 10));
    this.queue?.close();
  }
}

export class BLETransport extends BaseTransport {
  async open() {
    if (!navigator.bluetooth) throw new Error('这个浏览器不支持 Web Bluetooth。iPhone Safari 可使用演示模式。');
    this.device = await navigator.bluetooth.requestDevice({ filters: [{ services: [HM] }, { services: [NUS] }], optionalServices: [HM, NUS] });
    if (this.closing) throw new DOMException('连接已取消', 'AbortError');
    this.disconnectListener = () => this.lost(new Error('蓝牙连接已断开'));
    this.device.addEventListener('gattserverdisconnected', this.disconnectListener);
    try {
      const server = await this.device.gatt.connect();
      if (this.closing) { this.device.gatt.disconnect(); throw new DOMException('连接已取消', 'AbortError'); }
      let service;
      try { service = await server.getPrimaryService(NUS); }
      catch { service = await server.getPrimaryService(HM); }
      const isNUS = service.uuid.toLowerCase() === NUS;
      this.tx = await service.getCharacteristic(isNUS ? RX : HM_CHAR);
      this.rx = isNUS ? await service.getCharacteristic(TX) : this.tx;
      if (!this.tx.properties.write && !this.tx.properties.writeWithoutResponse) throw new Error('该 UART 特征不可写入');
      if (!this.rx.properties.notify && !this.rx.properties.indicate) throw new Error('该 UART 不提供固件状态通知');
      this.notifyListener = event => this.consume(new Uint8Array(event.target.value.buffer, event.target.value.byteOffset, event.target.value.byteLength));
      this.rx.addEventListener('characteristicvaluechanged', this.notifyListener);
      await this.rx.startNotifications();
      if (this.closing) { this.device.gatt.disconnect(); throw new DOMException('连接已取消', 'AbortError'); }
      this.queue = new CommandQueue(async bytes => {
        if (this.tx.properties.writeWithoutResponse && this.tx.writeValueWithoutResponse) await this.tx.writeValueWithoutResponse(bytes);
        else if (this.tx.writeValueWithResponse) await this.tx.writeValueWithResponse(bytes);
        else await this.tx.writeValue(bytes);
      }, error => this.lost(error));
      return this.device.name || 'BLE UART Robot';
    } catch (error) { await this.close(); if (this.device.gatt?.connected) this.device.gatt.disconnect(); throw error; }
  }
  async close() {
    if (this.closing) return;
    this.closing = true;
    await this.drainStop();
    if (this.rx && this.notifyListener) this.rx.removeEventListener('characteristicvaluechanged', this.notifyListener);
    if (this.device) {
      this.device.removeEventListener('gattserverdisconnected', this.disconnectListener);
      if (this.device.gatt?.connected) this.device.gatt.disconnect();
    }
  }
}

export class SerialTransport extends BaseTransport {
  constructor(baud, onLine, onLost) { super(onLine, onLost); this.baud = baud; }
  async open() {
    if (!navigator.serial) throw new Error('此浏览器不支持 Web Serial。请使用兼容浏览器或演示模式。');
    this.port = await navigator.serial.requestPort();
    if (this.closing) throw new DOMException('连接已取消', 'AbortError');
    try {
      await this.port.open({ baudRate: this.baud });
      if (this.closing) { await this.port.close(); throw new DOMException('连接已取消', 'AbortError'); }
      this.writer = this.port.writable.getWriter();
      this.reader = this.port.readable.getReader();
      this.queue = new CommandQueue(bytes => this.writer.write(bytes), error => this.lost(error));
      this.reading = this.readLoop();
      return `Serial UART · ${this.baud} baud`;
    } catch (error) { await this.close(); throw error; }
  }
  async readLoop() {
    try {
      while (!this.closing) {
        const { value, done } = await this.reader.read();
        if (done) break;
        if (value) this.consume(value);
      }
      if (!this.closing) this.lost(new Error('串口已断开'));
    } catch (error) { this.lost(error); }
    finally { this.reader?.releaseLock(); this.reader = null; }
  }
  async close() {
    if (this.closing) return;
    this.closing = true;
    await this.drainStop();
    if (this.reader) { try { await this.reader.cancel(); } catch {} }
    if (this.reading) { try { await this.reading; } catch {} }
    if (this.writer) { try { this.writer.releaseLock(); } catch {} this.writer = null; }
    if (this.port) { try { await this.port.close(); } catch {} }
  }
}
