package com.shenen.rec.remote;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class PadLayoutTest extends InstrumentationTestCase {
    public void testAllControlsFitLandscapeViewport() throws Throwable {
        Intent intent=new Intent();
        intent.setClassName(getInstrumentation().getTargetContext().getPackageName(),"com.shenen.rec.remote.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=getInstrumentation().startActivitySync(intent);
        getInstrumentation().waitForIdleSync();
        try {
            runTestOnUiThread(()->{
                View root=activity.findViewById(android.R.id.content);
                assertTrue("App must be landscape",root.getWidth()>root.getHeight());
                List<Button> buttons=new ArrayList<>();collect(root,buttons);
                assertEquals("Eleven controls and Bluetooth connection",12,buttons.size());
                String[] required={"Forward","Reverse","Left","Right","Pause","Left clamp\nOpen","Left clamp\nClose","Right clamp\nOpen","Right clamp\nClose"};
                for(String name:required){
                    Button match=null;
                    for(Button button:buttons)if(button.getText().toString().contains(name)){match=button;break;}
                    assertNotNull("Missing "+name,match);
                    Rect visible=new Rect();
                    assertTrue("Not visible "+name,match.getGlobalVisibleRect(visible));
                    assertEquals("Clipped vertically "+name,match.getHeight(),visible.height());
                    assertEquals("Clipped horizontally "+name,match.getWidth(),visible.width());
                    float dp=activity.getResources().getDisplayMetrics().density;
                    assertTrue("Touch target too short "+name,match.getHeight()/dp>=48);
                }
                for(String name:new String[]{"Speed up","Speed down"}){
                    Button match=null;
                    for(Button b:buttons)if(name.contentEquals(b.getContentDescription()==null?"":b.getContentDescription())){match=b;break;}
                    assertNotNull("Missing "+name,match);
                    Rect visible=new Rect();
                    assertTrue("Not visible "+name,match.getGlobalVisibleRect(visible));
                    assertEquals(match.getWidth(),visible.width());assertEquals(match.getHeight(),visible.height());
                    assertEquals("Speed button must be round",match.getWidth(),match.getHeight());
                    assertTrue("Speed touch target too small",match.getHeight()/activity.getResources().getDisplayMetrics().density>=48);
                }
                try {
                    java.lang.reflect.Field field=MainActivity.class.getDeclaredField("engine");field.setAccessible(true);
                    ControllerEngine engine=(ControllerEngine)field.get(activity);engine.demo();
                    Button pause=null,up=null,plus=null;
                    for(Button b:buttons){
                        if(b.getText().toString().contains("Pause"))pause=b;
                        if(b.getText().toString().contains("Forward"))up=b;
                        if("Speed up".contentEquals(b.getContentDescription()==null?"":b.getContentDescription()))plus=b;
                    }
                    assertNotNull(pause);assertNotNull(up);assertNotNull(plus);
                    java.lang.reflect.Field progressField=MainActivity.class.getDeclaredField("speedProgress");progressField.setAccessible(true);
                    android.widget.ProgressBar bar=(android.widget.ProgressBar)progressField.get(activity);
                    assertEquals(200,bar.getProgress());plus.performClick();assertEquals(220,bar.getProgress());
                    engine.start(ControllerEngine.Group.DRIVE,"held",'F');
                    long now=android.os.SystemClock.uptimeMillis();
                    android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,10,10,0);
                    pause.dispatchTouchEvent(down);down.recycle();
                    assertTrue(engine.locked);assertEquals(0,engine.heldCount());assertFalse(up.isEnabled());assertFalse(plus.isEnabled());
                    assertTrue(pause.getText().toString().contains("Resume"));
                    android.view.MotionEvent upEvent=android.view.MotionEvent.obtain(now,now+20,android.view.MotionEvent.ACTION_UP,10,10,0);
                    pause.dispatchTouchEvent(upEvent);upEvent.recycle();
                    assertTrue("Releasing Pause must not resume",engine.locked);
                    pause.performClick();assertFalse(engine.locked);assertTrue(up.isEnabled());assertEquals(0,engine.heldCount());
                    engine.disconnect();
                } catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            Bitmap screenshot=getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            File file=new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),"layout-800x360.png");
            try(FileOutputStream out=new FileOutputStream(file)){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}
        } finally {runTestOnUiThread(activity::finish);}
    }
    private void collect(View view,List<Button> buttons){
        if(view instanceof Button)buttons.add((Button)view);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collect(group.getChildAt(i),buttons);}
    }
}