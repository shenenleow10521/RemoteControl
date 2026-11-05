package com.shenen.rec.remote;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

public final class JoystickView extends View {
    public interface Listener { void direction(char command); }
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float thumbX, thumbY;
    private int pointer = -1;
    private char command = 'S';
    private static final int MINT = Color.rgb(177,245,212);
    public JoystickView(Context context, Listener listener) {
        super(context); this.listener = listener;
        setFocusable(true); setClickable(true);
        setContentDescription("行驶摇杆：按住并拖动，上下左右控制，松开停止。点击可停止。");
    }
    private float radius() { return Math.min(getWidth(),getHeight()) * 0.39f; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx=getWidth()/2f,cy=getHeight()/2f,r=radius();
        paint.setAlpha(isEnabled()?255:90); paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(cx,cy,r,Color.rgb(42,65,77),Color.rgb(20,34,43),Shader.TileMode.CLAMP));
        canvas.drawCircle(cx,cy,r,paint); paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2f);paint.setColor(Color.rgb(66,95,106));
        canvas.drawCircle(cx,cy,r,paint);canvas.drawCircle(cx,cy,r*.24f,paint);
        paint.setColor(Color.rgb(48,72,83));
        canvas.drawLine(cx-r*.85f,cy,cx+r*.85f,cy,paint);canvas.drawLine(cx,cy-r*.85f,cx,cy+r*.85f,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);paint.setColor(MINT);
        canvas.drawText("↑",cx,cy-r-7,paint);canvas.drawText("↓",cx,cy+r+paint.getTextSize(),paint);
        canvas.drawText("←",cx-r-13,cy+paint.getTextSize()/3,paint);canvas.drawText("→",cx+r+13,cy+paint.getTextSize()/3,paint);
        float tx=cx+thumbX,ty=cy+thumbY,knob=r*.35f;
        paint.setColor(Color.argb(65,0,0,0));canvas.drawCircle(tx,ty+5,knob+2,paint);
        paint.setShader(new RadialGradient(tx-knob*.25f,ty-knob*.3f,knob*1.6f,MINT,Color.rgb(89,167,140),Shader.TileMode.CLAMP));
        canvas.drawCircle(tx,ty,knob,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(Color.rgb(205,255,229));canvas.drawCircle(tx,ty,knob,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(33,74,62));canvas.drawCircle(tx,ty,4,paint);paint.setAlpha(255);
    }
    private void update(float x,float y) {
        float dx=x-getWidth()/2f,dy=y-getHeight()/2f,r=radius(),distance=(float)Math.hypot(dx,dy),travel=r*.65f;
        float scale=distance>travel&&distance>0?travel/distance:1;
        thumbX=dx*scale;thumbY=dy*scale;
        char next=JoystickDirections.direction(dx,dy,r,command);
        if(next!=command){command=next;listener.direction(next);}
        invalidate();
    }
    public void cancelGesture() {
        pointer=-1;thumbX=thumbY=0;
        char old=command;command='S';
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        if(old!='S')listener.direction('S');
        invalidate();
    }
    @Override public void setEnabled(boolean enabled) {
        boolean changed=isEnabled()!=enabled;super.setEnabled(enabled);
        if(changed&&!enabled)cancelGesture();invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if(!isEnabled())return false;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointer=event.getPointerId(0);getParent().requestDisallowInterceptTouchEvent(true);update(event.getX(),event.getY());return true;
            case MotionEvent.ACTION_MOVE:
                int index=event.findPointerIndex(pointer);
                if(index<0)cancelGesture();else update(event.getX(index),event.getY(index));return true;
            case MotionEvent.ACTION_POINTER_UP:
                if(event.getPointerId(event.getActionIndex())==pointer)cancelGesture();return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
                cancelGesture();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();cancelGesture();return true;}
    @Override protected void onDetachedFromWindow(){cancelGesture();super.onDetachedFromWindow();}
}
