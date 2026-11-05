package com.shenen.rec.remote;
import org.junit.Test;
public class EngineTest {
    @Test public void controlAndTerminalBehavior(){EngineChecks.runAll();}
    @Test public void correctedButtonsReachTheExpectedMotorAndGripperCommands(){
        org.junit.Assert.assertEquals('B',ButtonMapping.map('F',true,false,true));
        org.junit.Assert.assertEquals('F',ButtonMapping.map('B',true,false,true));
        org.junit.Assert.assertEquals('C',ButtonMapping.map('E',true,false,true));
        org.junit.Assert.assertEquals('E',ButtonMapping.map('C',true,false,true));
        for(char c:new char[]{'L','R','S','W','X','J','K','!'})
            org.junit.Assert.assertEquals(c,ButtonMapping.map(c,true,false,true));
        org.junit.Assert.assertEquals('F',ButtonMapping.map('F',false,false,false));
        org.junit.Assert.assertEquals('E',ButtonMapping.map('E',false,false,false));
    }
}
