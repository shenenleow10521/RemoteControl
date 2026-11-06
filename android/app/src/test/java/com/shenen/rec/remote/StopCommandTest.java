package com.shenen.rec.remote;
import org.junit.Test;
import static org.junit.Assert.*;
public class StopCommandTest {
    @Test public void byteParserAndLineParserBothReceiveDistinctStops() {
        String frame=StopCommand.FRAME;
        assertTrue(frame.indexOf('S')<frame.indexOf('!'));
        java.util.List<String> lines=new java.util.ArrayList<>();
        for(String line:frame.split("\\r?\\n"))if(!line.isEmpty())lines.add(line);
        assertEquals(java.util.Arrays.asList("S","!","P"),lines);
        for(char c:frame.toCharArray())assertTrue("No drive command may be in stop frame","FBLRWXEC".indexOf(c)<0);
    }
}
