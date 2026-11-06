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
    public void testAllNineControlsFitLandscapeViewport() throws Throwable {
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
                assertEquals("Only nine controls and Bluetooth connection",10,buttons.size());
                String[] required={"前进","后退","左转","右转","停止","左夹子\n开","左夹子\n关","右夹子\n开","右夹子\n关"};
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