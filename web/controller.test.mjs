import test from 'node:test';
import assert from 'node:assert/strict';
import { RobotController, CommandQueue, parseStatus } from './dist/controller.mjs';

function fixture() {
  let time = 1000;
  const sent = [], notices = [];
  const transport = { send: (c, p) => sent.push([c, !!p]), close: async () => {} };
  const robot = new RobotController({ now: () => time, onNotice: n => notices.push(n) });
  return { robot, sent, notices, transport, advance: ms => { time += ms; robot.tick(); } };
}
test('real controls remain locked until matching bounded REC1 telemetry arrives', () => {
  const f = fixture(); f.robot.attach(f.transport, 'test');
  assert.equal(f.robot.start('drive', 'touch', 'F'), false);
  f.robot.receive('Speed: 200 Drive: stopped'); assert.equal(f.robot.state.ready, false);
  f.robot.receive('REC1 PWM=999 ARM=90 GRIP=90 DRIVE=STOPPED'); assert.equal(f.robot.state.ready, false);
  f.robot.receive('REC1 PWM=200 ARM=90 GRIP=90 DRIVE=STOPPED'); assert.equal(f.robot.state.ready, true);
  assert.deepEqual(f.sent.slice(0,2), [['!',true],['P',false]]);
});
test('hold release only affects its owned actuator; stale releases cannot stop another touch', () => {
  const f = fixture(); f.robot.demo();
  f.robot.start('drive','first','F'); f.robot.start('arm','arm','W');
  f.robot.start('drive','second','B'); f.robot.release('drive','first');
  assert.equal(f.robot.active.get('drive').command, 'B');
  f.robot.release('drive','second'); assert.equal(f.robot.state.drive,'STOPPED');
  assert.equal(f.robot.active.get('arm').command,'W');
});
test('emergency stop latches and clears every motion, with explicit resume required', () => {
  const f = fixture(); f.robot.demo(); f.robot.start('drive','x','F'); f.robot.start('grip','g','C');
  f.robot.stop(); assert.equal(f.robot.active.size,0); assert.equal(f.robot.state.locked,true);
  assert.equal(f.robot.start('drive','x','F'),false);
  f.robot.resume(); assert.equal(f.robot.start('drive','x','F'),true);
});
test('a suspended event loop cancels held movement instead of replaying it', () => {
  const f = fixture(); f.robot.demo(); f.robot.start('drive','touch','F');
  f.advance(900); assert.equal(f.robot.state.locked,true); assert.equal(f.robot.active.size,0);
  assert.equal(f.robot.state.drive,'STOPPED'); assert.equal(f.notices.length,1);
});
test('lost firmware feedback closes a transport, including an unsuccessful handshake', async () => {
  const f=fixture(); f.robot.attach(f.transport,'test'); f.advance(10001);
  assert.equal(f.robot.transport,null); assert.equal(f.robot.state.ready,false);
  const g=fixture(); g.robot.attach(g.transport,'test');
  g.robot.receive('REC1 PWM=200 ARM=90 GRIP=90 DRIVE=STOPPED');
  g.advance(3001); assert.equal(g.robot.transport,null);
});
test('demo never writes to hardware and endpoints/speed stay bounded', () => {
  const f=fixture(); f.robot.demo();
  for(let i=0;i<100;i++){f.robot.speed(true);f.robot.send('W');f.robot.send('E');}
  assert.equal(f.robot.state.speed,255);assert.equal(f.robot.state.arm,155);assert.equal(f.robot.state.grip,150);
  for(let i=0;i<100;i++){f.robot.speed(false);f.robot.send('X');f.robot.send('C');}
  assert.equal(f.robot.state.speed,80);assert.equal(f.robot.state.arm,0);assert.equal(f.robot.state.grip,30);
  assert.equal(f.sent.length,0);
});
test('write queue drops stale queued movement and prioritizes stop', async () => {
  let time=0, unblock;
  const written=[];
  const q=new CommandQueue(async bytes=>{
    written.push(new TextDecoder().decode(bytes));
    if(written.length===1) await new Promise(resolve=>{unblock=resolve;});
  },error=>{throw error;},()=>time);
  q.send('F');q.send('W');q.send('E');time=500;unblock();
  await new Promise(resolve=>setTimeout(resolve,0));
  assert.deepEqual(written,['F']);
  let secondUnblock;
  const q2=new CommandQueue(async bytes=>{
    written.push(new TextDecoder().decode(bytes));
    if(written.length===2) await new Promise(resolve=>{secondUnblock=resolve;});
  },error=>{throw error;},()=>time);
  q2.send('B');q2.send('W');q2.send('E');q2.send('!',true);secondUnblock();
  await new Promise(resolve=>setTimeout(resolve,0));
  assert.deepEqual(written,['F','B','!']);
});
test('status parser rejects partial, incorrect and unbounded status lines', () => {
  assert.equal(parseStatus('REC1 PWM=200 ARM=90'),null);
  assert.equal(parseStatus('REC1 PWM=200 ARM=999 GRIP=90 DRIVE=STOPPED'),null);
  assert.equal(parseStatus('REC1 PWM=0 ARM=90 GRIP=90 DRIVE=MOVING'),null);
  assert.deepEqual(parseStatus('REC1 PWM=200 ARM=90 GRIP=90 DRIVE=STOPPED\r'),{speed:200,arm:90,grip:90,drive:'STOPPED'});
});
