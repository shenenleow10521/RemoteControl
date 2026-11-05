import { RobotController, clamp } from './controller.mjs';
import { BLETransport, SerialTransport } from './transports.mjs';
const $ = id => document.getElementById(id);
const motionButtons = [...document.querySelectorAll('.motion')];
let toastTimeout, busy = false, pendingTransport = null, wakeLock;
let reverseArm = false, reverseGrip = false;
const controller = new RobotController({ onChange: render, onNotice: toast });
const commandNames = { F: '前进', B: '后退', L: '左转', R: '右转', W: '机械臂 +', X: '机械臂 −', E: '夹爪 +', C: '夹爪 −', S: '停止行驶', J: '保持机械臂', K: '保持夹爪', '!': '停止全部动作', '+': '提高速度', '-': '降低速度', P: '状态查询' };
const driveNames = { IDLE: '待连接', STOPPED: '已停止', MOVING: '行驶中', STARTING: '准备行驶', F: '前进中', B: '后退中', L: '向左旋转', R: '向右旋转' };

function toast(message) {
  $('toast').textContent = message; $('toast').hidden = false;
  clearTimeout(toastTimeout); toastTimeout = setTimeout(() => { $('toast').hidden = true; }, 5000);
}
function render(s) {
  const demo = s.mode === 'demo', ready = s.ready, enabled = ready && !s.locked;
  $('connectionTitle').textContent = demo ? '演示模式 · 未连接机器人' : ready ? s.name : s.mode === 'connecting' ? `连接中 · ${s.name}` : '尚未连接机器人';
  $('connectionDetail').textContent = demo ? '全部操作仅在此界面模拟，不发送真实命令' : ready ? '固件握手成功 · 松开按钮停止对应动作' : s.mode === 'connecting' ? '正在等待配套固件反馈…' : '连接设备，或先试试操作界面';
  $('statusDot').className = `status-dot ${demo ? 'demo' : ready ? 'ready' : ''}`;
  $('modeChip').textContent = demo ? '演示模式' : ready ? (s.locked ? '已急停' : '已连接') : '未连接';
  $('modeChip').className = `chip ${demo ? 'demo' : ready ? 'ready' : ''}`;
  $('connectButton').querySelector('span').textContent = s.mode === 'robot' || s.mode === 'connecting' ? '断开连接' : '连接机器人';
  $('viewTag').textContent = demo ? '演示 · 非实况' : '界面示意'; $('viewTag').className = `live-tag ${demo ? 'demo' : ''}`;
  $('viewNote').textContent = demo ? '演示中的位置与动作均为模拟，不代表真实机器人。' : '仅作界面示意，不提供真实定位或夹取检测。';
  $('speedValue').innerHTML = `${s.speed ?? '—'}<small>/ 255</small>`;
  $('speedFill').style.width = s.speed == null ? '0%' : `${s.speed / 255 * 100}%`;
  $('armValue').innerHTML = `${s.arm ?? '—'}<small>°</small>`;
  $('gripValue').innerHTML = `${s.grip ?? '—'}<small>°</small>`;
  $('driveState').textContent = driveNames[s.drive] || s.drive;
  $('consoleOutput').textContent = s.feedback || '机器人连接后将在此显示反馈。';
  $('lastCommand').textContent = s.lastCommand ? `${demo ? '[演示] ' : ''}${s.lastCommand} · ${commandNames[s.lastCommand] || ''}` : '等待操作';
  for (const button of motionButtons) {
    button.disabled = !enabled;
    const active = controller.active.get(button.dataset.group);
    button.classList.toggle('pressed', !!active && (active.token === button._pointerToken || active.token === `key:${button.dataset.command}`));
    button.setAttribute('aria-pressed', button.classList.contains('pressed') ? 'true' : 'false');
  }
  for (const id of ['speedUp', 'speedDown', 'padStop', 'emergencyStop', 'refreshStatus']) $(id).disabled = !ready;
  $('resumeBanner').hidden = !s.locked;
  $('demoButton').textContent = demo ? '退出演示' : '进入演示';
  $('practiceCard').querySelector('strong').textContent = demo ? '正在演示操作界面' : '先熟悉一下手感';
  $('robotVisual').style.transform = `translate(calc(-50% + ${s.x}px), calc(-50% + ${s.y}px)) rotate(${s.heading}deg)`;
  const open = (s.grip ?? 90) / 180 * 40;
  $('jawLeft').style.transform = `rotate(${-open}deg)`; $('jawRight').style.transform = `rotate(${open}deg)`;
}

function mappedCommand(button) {
  let cmd = button.dataset.command;
  if (button.dataset.group === 'arm' && reverseArm) cmd = cmd === 'W' ? 'X' : 'W';
  if (button.dataset.group === 'grip' && reverseGrip) cmd = cmd === 'E' ? 'C' : 'E';
  return cmd;
}

for (const button of motionButtons) {
  button.addEventListener('contextmenu', event => event.preventDefault());
  button.addEventListener('pointerdown', event => {
    if (event.button !== 0 || button.disabled || document.querySelector('dialog[open]')) return;
    event.preventDefault();
    const token = `pointer:${event.pointerId}:${button.dataset.command}`;
    button._pointerToken = token;
    if (controller.start(button.dataset.group, token, mappedCommand(button))) {
      button.setPointerCapture(event.pointerId);
      navigator.vibrate?.(8);
    }
  });
  const release = () => controller.release(button.dataset.group, button._pointerToken);
  button.addEventListener('pointerup', release);
  button.addEventListener('pointercancel', release);
  button.addEventListener('lostpointercapture', release);
  button.addEventListener('pointermove', event => {
    if (!button.hasPointerCapture(event.pointerId)) return;
    const r = button.getBoundingClientRect();
    if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) release();
  });
  // Keyboard/assistive activation makes a brief pulse, never an indefinite hold.
  button.addEventListener('click', event => {
    if (event.detail !== 0 || button.disabled) return;
    const token = `assist:${Date.now()}`;
    controller.start(button.dataset.group, token, mappedCommand(button));
    setTimeout(() => controller.release(button.dataset.group, token), 160);
  });
}

const keyCommands = { ArrowUp: 'F', ArrowDown: 'B', ArrowLeft: 'L', ArrowRight: 'R', w: 'W', x: 'X', e: 'E', c: 'C' };
document.addEventListener('keydown', event => {
  if (document.querySelector('dialog[open]') || /INPUT|SELECT|TEXTAREA/.test(event.target.tagName)) return;
  if (event.code === 'Space' || event.key === 'Escape') { event.preventDefault(); controller.stop(); return; }
  const command = keyCommands[event.key] || keyCommands[event.key.toLowerCase()];
  if (!command || event.repeat) return;
  event.preventDefault(); const button = motionButtons.find(b => b.dataset.command === command);
  if (button && !button.disabled) controller.start(button.dataset.group, `key:${command}`, mappedCommand(button));
});
document.addEventListener('keyup', event => {
  const command = keyCommands[event.key] || keyCommands[event.key.toLowerCase()];
  const button = motionButtons.find(b => b.dataset.command === command);
  if (button) controller.release(button.dataset.group, `key:${command}`);
});

for (const id of ['emergencyStop', 'padStop']) $(id).addEventListener('click', () => { controller.stop(); toast('已发出停止命令。继续前请点击「恢复操作」。'); });
$('resumeButton').addEventListener('click', () => { controller.resume(); toast('可继续操作；按住按钮才会移动。'); });
$('speedUp').addEventListener('click', () => controller.speed(true));
$('speedDown').addEventListener('click', () => controller.speed(false));
$('refreshStatus').addEventListener('click', () => { controller.status(); render(controller.state); });

function openDialog(id) { controller.stop(false); $(id).showModal(); }
for (const id of ['settingsButton', 'footerHelp']) $(id).addEventListener('click', () => openDialog('settingsDialog'));
for (const close of document.querySelectorAll('.close-dialog')) close.addEventListener('click', () => close.closest('dialog').close());
for (const dialog of document.querySelectorAll('dialog')) dialog.addEventListener('click', event => { if (event.target === dialog) { const r = dialog.getBoundingClientRect(); if (event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom) dialog.close(); } });
$('reverseArm').addEventListener('change', event => { controller.stop(false); reverseArm = event.target.checked; });
$('reverseGrip').addEventListener('change', event => { controller.stop(false); reverseGrip = event.target.checked; });

async function demoToggle() {
  if (controller.state.mode === 'demo') await controller.disconnect();
  else { await controller.disconnect(); controller.demo(); toast('已进入演示：没有连接或控制真实机器人。'); }
}
$('demoButton').addEventListener('click', demoToggle);
$('quickDemo').addEventListener('click', demoToggle);
$('dialogDemo').addEventListener('click', () => { $('connectDialog').close(); void demoToggle(); });
$('connectButton').addEventListener('click', async () => {
  if (controller.transport || controller.state.mode === 'connecting') { await controller.disconnect(); await releaseWake(); }
  else openDialog('connectDialog');
});

function selectTab(serial) {
  $('serialTab').classList.toggle('selected', serial); $('bleTab').classList.toggle('selected', !serial);
  $('serialContent').hidden = !serial; $('bleContent').hidden = serial;
}
$('bleTab').addEventListener('click', () => selectTab(false)); $('serialTab').addEventListener('click', () => selectTab(true));

async function connect(serial) {
  if (busy || controller.transport) return;
  busy = true; $('pairBLE').disabled = true; $('pairSerial').disabled = true;
  if (controller.state.mode === 'demo') await controller.disconnect();
  let attempt;
  const onLost = error => {
    if (pendingTransport === attempt) {
      void controller.disconnect(); toast(error?.message || '设备连接已断开'); void releaseWake();
    }
  };
  attempt = serial ? new SerialTransport(Number($('baud').value), line => controller.receive(line), onLost) : new BLETransport(line => controller.receive(line), onLost);
  pendingTransport = attempt;
  try {
    const name = await attempt.open();
    if (document.hidden || pendingTransport !== attempt) { await attempt.close(); return; }
    controller.attach(attempt, name);
    $('connectDialog').close();
    toast('设备已连接，正在等待配套固件确认…');
    try { if (navigator.wakeLock) wakeLock = await navigator.wakeLock.request('screen'); } catch {}
  } catch (error) {
    await attempt.close();
    if (error.name !== 'NotFoundError') toast(`连接失败：${error.message}`);
  } finally { busy = false; $('pairBLE').disabled = !navigator.bluetooth; $('pairSerial').disabled = !navigator.serial; }
}
$('pairBLE').addEventListener('click', () => void connect(false)); $('pairSerial').addEventListener('click', () => void connect(true));
async function releaseWake() { try { await wakeLock?.release(); } catch {} wakeLock = null; }
function interrupt() {
  controller.stop(true);
  if (controller.transport) void controller.disconnect();
  if (pendingTransport && !controller.transport) void pendingTransport.close();
  pendingTransport = null; void releaseWake();
}
window.addEventListener('blur', () => { if (controller.active.size) controller.stop(true); });
document.addEventListener('visibilitychange', () => { if (document.hidden) interrupt(); });
window.addEventListener('pagehide', interrupt);
setInterval(() => controller.tick(), 120);

let duration = 180, remaining = 180, running = false, endTime = 0;
function renderClock() {
  const mins = String(Math.floor(remaining / 60)).padStart(2, '0'), secs = String(remaining % 60).padStart(2, '0');
  $('timer').innerHTML = `${mins}<span>:</span>${secs}`;
  $('timerFill').style.width = `${remaining / duration * 100}%`;
  $('clockStart').innerHTML = running ? '暂停并停止 <span>Ⅱ</span>' : remaining === 0 ? '计时已结束 <span>✓</span>' : '开始计时 <span>↗</span>';
  $('clockStart').disabled = remaining === 0;
}
function resetClock(seconds) { controller.stop(false); running = false; duration = seconds; remaining = seconds; $('matchClock').classList.toggle('selected', seconds === 180); $('calibrationClock').classList.toggle('selected', seconds === 60); renderClock(); }
$('clockStart').addEventListener('click', () => {
  if (running) { remaining = Math.max(0, Math.ceil((endTime - Date.now()) / 1000)); running = false; controller.stop(); }
  else if (remaining > 0) { endTime = Date.now() + remaining * 1000; running = true; toast('计时已开始；机器人须在裁判信号后才可操作。'); }
  renderClock();
});
$('clockReset').addEventListener('click', () => resetClock(duration)); $('matchClock').addEventListener('click', () => resetClock(180)); $('calibrationClock').addEventListener('click', () => resetClock(60));
$('retryButton').addEventListener('click', () => { controller.stop(); toast('已停止。请向裁判申请 Retry；比赛计时不会重置。'); });
setInterval(() => { if (running) { remaining = clamp(Math.ceil((endTime - Date.now()) / 1000), 0, duration); if (remaining === 0) { running = false; controller.stop(); toast('本地计时结束，已发停止命令；以裁判指令为准。'); } renderClock(); } }, 200);

let installPrompt;
window.addEventListener('beforeinstallprompt', event => { event.preventDefault(); installPrompt = event; });
$('installButton').addEventListener('click', async () => {
  controller.stop(false);
  if (installPrompt) { await installPrompt.prompt(); installPrompt = null; }
  else toast('iPhone：Safari 点「分享」→「添加到主屏幕」。添加后仍受浏览器蓝牙兼容性限制。');
});

const canBLE = !!navigator.bluetooth && window.isSecureContext, canSerial = !!navigator.serial && window.isSecureContext;
$('compatibilityText').textContent = canBLE || canSerial ? `浏览器可尝试${canBLE ? ' BLE 蓝牙' : ''}${canBLE && canSerial ? ' /' : ''}${canSerial ? ' 串口连接' : ''}；须使用配套固件。` : '此浏览器可打开界面和演示；不支持这里的蓝牙连接。';
$('bleCapability').textContent = canBLE ? '当前浏览器提供 Web Bluetooth。连接仍需设备授权与兼容 BLE 模块。' : '此浏览器未提供 Web Bluetooth；iPhone Safari 可使用演示或安装原生 App。';
$('serialCapability').textContent = canSerial ? '浏览器提供 Web Serial。先在系统中配对 HC-05，再选择对应串口。' : '此浏览器未提供 Web Serial。请换兼容浏览器或使用演示。';
$('pairBLE').disabled = !canBLE; $('pairSerial').disabled = !canSerial;
render(controller.state); renderClock();
if ('serviceWorker' in navigator) navigator.serviceWorker.register('./sw.js').catch(() => {});
