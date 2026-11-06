package com.shenen.rec.remote;
import java.util.ArrayList;
import java.util.List;
public final class EngineChecks {
    private static final class Fixture implements ControllerEngine.Clock, ControllerEngine.Listener, ControllerEngine.Sink {
        long time=1000; int closed, notices;
        final List<Character> sent=new ArrayList<>();
        final ControllerEngine engine=new ControllerEngine(this,this);
        public long now(){return time;}
        public void changed(){}
        public void notice(String text){notices++;}
        public void send(char c,boolean priority){sent.add(c);}
        public void close(){closed++;}
        void advance(long delta){time+=delta;engine.tick();}
    }
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void runAll(){
        Fixture f=new Fixture();f.engine.attach(f,"test");
        check(!f.engine.start(ControllerEngine.Group.DRIVE,"first",'F'),"strict mode requires handshake");
        f.engine.receive("REC1 PWM=999 ARM=90 GRIP=90 DRIVE=STOPPED");check(!f.engine.ready,"reject out-of-range feedback");
        f.engine.receive("REC1 PWM=200 ARM=90 GRIP=90 DRIVE=STOPPED");check(f.engine.ready,"valid handshake");
        f.engine.start(ControllerEngine.Group.DRIVE,"first",'F');f.engine.start(ControllerEngine.Group.DRIVE,"second",'B');
        int count=f.sent.size();f.engine.release(ControllerEngine.Group.DRIVE,"first");check(f.sent.size()==count,"old release cannot cancel new touch");
        f.engine.start(ControllerEngine.Group.ARM,"arm",'W');f.engine.release(ControllerEngine.Group.DRIVE,"second");check(f.engine.heldCount()==1,"independent actuators");
        f.engine.stop(true);check(f.engine.heldCount()==0&&f.engine.locked,"latched stop clears all holds");
        check(f.sent.get(f.sent.size()-2)=='S'&&f.sent.get(f.sent.size()-1)=='!',"stop supports old and new firmware");
        check(!f.engine.start(ControllerEngine.Group.DRIVE,"touch",'F'),"cannot move during latch");
        f.engine.stop(false);check(f.engine.locked,"ordinary stop cannot clear an emergency latch");
        f.engine.resume();f.engine.start(ControllerEngine.Group.DRIVE,"touch",'F');f.advance(900);check(f.engine.locked&&f.engine.heldCount()==0,"stall stops hold");
        f.engine.resume();f.advance(3001);check(!f.engine.ready&&f.closed==1,"missing feedback closes strict transport");
        Fixture h=new Fixture();h.engine.attach(h,"test");h.advance(10001);check(h.closed==1,"strict handshake times out");
        Fixture raw=new Fixture();raw.engine.attachTerminal(raw,"HC-05");
        check(raw.engine.ready&&raw.sent.size()==1&&raw.sent.get(0)=='P',"terminal asks for optional speed feedback without requiring a handshake");
        raw.sent.clear();
        raw.advance(20000);check(raw.engine.ready&&raw.closed==0,"legacy serial firmware needs no REC1 telemetry");
        raw.engine.start(ControllerEngine.Group.DRIVE,"touch",'F');check(raw.sent.get(0)=='F',"terminal drive forwards byte");
        raw.engine.release(ControllerEngine.Group.DRIVE,"touch");check(raw.sent.get(1)=='S',"legacy release sends S");
        raw.engine.receive("Speed: 200  Drive: moving");
        check(raw.engine.speed==200&&raw.engine.speedConfirmed,"parse original PWM sketch feedback");
        raw.engine.changeSpeed(false);
        check(raw.engine.requestedSpeed==180&&!raw.engine.speedConfirmed,"show unconfirmed target separately from actual feedback");
        raw.engine.receive("Speed: 180  Drive: moving");
        check(raw.engine.speed==180&&raw.engine.speedConfirmed,"speed confirmed by robot");
        raw.engine.receive("Speed: 999  Drive: moving");check(raw.engine.speed==180,"invalid speed rejected");
        raw.engine.start(ControllerEngine.Group.DRIVE,"touch",'F');
        raw.engine.stop(true);int stoppedCount=raw.sent.size();
        check(raw.engine.locked&&raw.engine.heldCount()==0,"pause clears all previous holds");
        check(!raw.engine.start(ControllerEngine.Group.DRIVE,"blocked",'B'),"pause blocks new motion");
        raw.engine.changeSpeed(true);check(raw.sent.size()==stoppedCount,"pause blocks speed changes");
        raw.advance(240);check(raw.sent.size()==stoppedCount+2,"pause retries hardware stop");
        raw.engine.receive("Speed: 180  Drive: stopped");check(raw.engine.locked&&raw.engine.stopConfirmed,"feedback cannot release pause");
        raw.engine.resume();stoppedCount=raw.sent.size();raw.advance(240);
        check(raw.engine.heldCount()==0&&raw.sent.size()==stoppedCount,"resume never replays a held direction");
        raw.advance(20000);check(raw.engine.ready,"legacy feedback stays optional and event driven");
        raw.engine.receive("REC1 PWM=180 ARM=80 GRIP=40 DRIVE=STOPPED");check(raw.engine.mode==ControllerEngine.Mode.ROBOT,"matching feedback upgrades protocol");
        raw.advance(3001);check(!raw.engine.ready,"upgraded firmware enforces feedback timeout");
        Fixture d=new Fixture();d.engine.demo();
        for(int i=0;i<100;i++){d.engine.changeSpeed(true);d.engine.start(ControllerEngine.Group.ARM,"a",'W');d.engine.start(ControllerEngine.Group.GRIP,"g",'E');}
        check(d.engine.speed==255&&d.engine.arm==155&&d.engine.grip==150,"demo upper limits");
        for(int i=0;i<100;i++){d.engine.changeSpeed(false);d.engine.start(ControllerEngine.Group.ARM,"a",'X');d.engine.start(ControllerEngine.Group.GRIP,"g",'C');}
        check(d.engine.speed==80&&d.engine.arm==0&&d.engine.grip==30,"demo lower limits");
        check(d.sent.isEmpty(),"demo never writes to hardware");
        d.engine.stop(true);check(d.engine.drive.equals("STOPPED"),"demo stop");
    }
    public static void main(String[] args){runAll();System.out.println("PASS: strict and terminal modes, legacy compatibility, independent holds, stop/latch, stale event loop, feedback timeout, bounds and demo isolation");}
}
