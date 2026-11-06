package com.shenen.rec.remote;
import org.junit.Test;
public class EngineTest {
    @Test public void controlAndTerminalBehavior(){EngineChecks.runAll();}
    @Test public void joystickCentersStopsAndSelectsStableFourWayDirections(){
        org.junit.Assert.assertEquals('S',JoystickDirections.direction(0,0,100,'F'));
        org.junit.Assert.assertEquals('S',JoystickDirections.direction(10,10,100,'R'));
        org.junit.Assert.assertEquals('F',JoystickDirections.direction(0,-80,100,'S'));
        org.junit.Assert.assertEquals('B',JoystickDirections.direction(0,80,100,'S'));
        org.junit.Assert.assertEquals('L',JoystickDirections.direction(-80,0,100,'S'));
        org.junit.Assert.assertEquals('R',JoystickDirections.direction(80,0,100,'S'));
        org.junit.Assert.assertEquals('F',JoystickDirections.direction(75,-70,100,'F'));
        org.junit.Assert.assertEquals('R',JoystickDirections.direction(90,-45,100,'F'));
        org.junit.Assert.assertEquals('S',JoystickDirections.direction(Float.NaN,0,100,'F'));
    }
    @Test public void correctedButtonsReachTheExpectedMotorAndGripperCommands(){
        org.junit.Assert.assertEquals('B',ButtonMapping.map('F',true,true,false));
        org.junit.Assert.assertEquals('F',ButtonMapping.map('B',true,true,false));
        org.junit.Assert.assertEquals('E',ButtonMapping.map('E',true,true,false));
        org.junit.Assert.assertEquals('C',ButtonMapping.map('C',true,true,false));
        org.junit.Assert.assertEquals('X',ButtonMapping.map('W',true,true,false));
        org.junit.Assert.assertEquals('W',ButtonMapping.map('X',true,true,false));
        for(char c:new char[]{'S','J','K','!'})
            org.junit.Assert.assertEquals(c,ButtonMapping.map(c,true,true,false));
        org.junit.Assert.assertEquals('R',ButtonMapping.map('L',true,true,false));
        org.junit.Assert.assertEquals('L',ButtonMapping.map('R',true,true,false));
        org.junit.Assert.assertEquals('F',ButtonMapping.map('F',false,false,false));
        org.junit.Assert.assertEquals('E',ButtonMapping.map('E',false,false,false));
    }
}
