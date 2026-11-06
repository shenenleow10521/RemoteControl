package com.shenen.rec.remote;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private static final int BG = Color.rgb(16,27,34), PANEL = Color.rgb(24,37,46), MINT = Color.rgb(177,245,212),
        INK = Color.rgb(16,35,31), TEXT = Color.rgb(236,242,241), MUTED = Color.rgb(148,166,172), LINE = Color.rgb(48,70,81);
    private final List<Button> motion = new ArrayList<>();
    private ControllerEngine engine;
    private ClassicBluetooth bluetooth;
    private LinearLayout content;
    private TextView connectionTitle, connectionDetail, speedLabel, speedFeedback;
    private ProgressBar speedProgress;
    private Button connectButton, padStop, speedUp, speedDown;
    private boolean connecting, destroyed, reverseArm = true, reverseGrip, reverseDrive = true;
    private AlertDialog activeDialog;
    private TextView terminalOutput;
    private Button terminalSend;
    private final StringBuilder terminalLog = new StringBuilder();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            engine.tick();
            main.postDelayed(this, 120);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        engine = new ControllerEngine(SystemClock::elapsedRealtime, new ControllerEngine.Listener() {
            public void changed() { if (!destroyed && connectionTitle != null) render(); }
            public void notice(String text) { if (!destroyed) { connecting = false; bluetooth.disconnect(); MainActivity.this.notice(text); render(); } }
        });
        bluetooth = new ClassicBluetooth(main, new ClassicBluetooth.Listener() {
            public void connected(ControllerEngine.Sink sink, String name) {
                if (destroyed) { sink.close(); return; }
                connecting = false; engine.attachTerminal(sink, name);
                appendLog("\n[CONNECTED] " + name + "\n");
            }
            public void line(String text) { if (!destroyed) engine.receive(text); }
            public void chunk(String text) { if (!destroyed) appendLog(text); }
            public void failed(String reason) {
                if (destroyed) return;
                connecting = false; engine.disconnect(); notice("Connection lost: " + reason); render();
            }
        });
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        // Flip both existing clamp directions once when upgrading from 1.0.8.
        // Fresh installs inherit the corrected left=true / right=false mapping.
        if(!prefs.getBoolean("clampDirectionsV108",false)){
            prefs.edit().putBoolean("reverseArm",!prefs.getBoolean("reverseArm",false))
                .putBoolean("reverseGrip",!prefs.getBoolean("reverseGrip",true))
                .putBoolean("clampDirectionsV108",true).apply();
        }
        reverseDrive=prefs.getBoolean("reverseDrive",true);
        reverseGrip=prefs.getBoolean("reverseGrip",false);
        reverseArm=prefs.getBoolean("reverseArm",true);
        buildUI(); render(); main.post(ticker);
    }

    private void buildUI() {
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(BG);
        outer.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        content=column(); content.setPadding(dp(10),dp(6),dp(10),dp(6));
        outer.addView(content,new LinearLayout.LayoutParams(-1,-1)); setContentView(outer);
        LinearLayout header=row();
        LinearLayout status=column(); connectionTitle=label("REC PAD 1.0.10",14,MINT,true); status.addView(connectionTitle);
        connectionDetail=label("",10,MUTED,false);status.addView(connectionDetail);
        connectionTitle.setOnLongClickListener(v->{showDiagnostics();return true;});
        header.addView(status,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout speedPanel=column();speedPanel.setGravity(Gravity.CENTER_VERTICAL);
        speedLabel=label("Speed · No feedback",11,MINT,true);speedLabel.setPadding(0,0,0,0);speedPanel.addView(speedLabel);
        speedProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        speedProgress.setMax(255);speedProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(MINT));
        speedProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(LINE));
        speedPanel.addView(speedProgress,new LinearLayout.LayoutParams(-1,dp(8)));
        speedFeedback=label("PWM 80–255",9,MUTED,false);speedFeedback.setPadding(0,0,0,0);speedPanel.addView(speedFeedback);
        LinearLayout.LayoutParams speedParams=new LinearLayout.LayoutParams(dp(165),-1);
        speedParams.setMargins(dp(6),0,dp(12),0);header.addView(speedPanel,speedParams);
        connectButton=button("Connect BT",MINT,INK);connectButton.setOnClickListener(v->{
            if(connecting||bluetooth.isConnected()||engine.mode==ControllerEngine.Mode.HANDSHAKE)disconnect();else showDevices();
        });
        header.addView(connectButton,new LinearLayout.LayoutParams(dp(110),-1));
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(50)));space(content,4);
        LinearLayout controls=row();controls.setGravity(Gravity.FILL);
        LinearLayout drive=panel();drive.setPadding(dp(4),dp(4),dp(4),dp(4));
        GridLayout pad=new GridLayout(this);pad.setColumnCount(3);pad.setRowCount(3);pad.setUseDefaultMargins(false);
        for(int r=0;r<3;r++)for(int c=0;c<3;c++){
            View cell;
            if(r==0&&c==1)cell=move("↑\nForward",ControllerEngine.Group.DRIVE,'F');
            else if(r==1&&c==0)cell=move("←\nLeft",ControllerEngine.Group.DRIVE,'L');
            else if(r==1&&c==2)cell=move("→\nRight",ControllerEngine.Group.DRIVE,'R');
            else if(r==2&&c==0)cell=speedControl(true);
            else if(r==2&&c==2)cell=speedControl(false);
            else if(r==2&&c==1)cell=move("↓\nReverse",ControllerEngine.Group.DRIVE,'B');
            else if(r==1&&c==1){
                padStop=button("Ⅱ\nPause",Color.rgb(64,43,47),Color.rgb(255,180,170));
                installPauseHandler();cell=padStop;
            }else cell=blank();
            gridCell(pad,cell,r,c);
        }
        drive.addView(pad,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-1,1.15f);left.setMargins(0,0,dp(8),0);controls.addView(drive,left);
        LinearLayout clamps=panel();clamps.setPadding(dp(4),dp(4),dp(4),dp(4));
        GridLayout gripGrid=new GridLayout(this);gripGrid.setColumnCount(2);gripGrid.setRowCount(2);
        gridCell(gripGrid,move("Left clamp\nOpen",ControllerEngine.Group.ARM,'W'),0,0);
        gridCell(gripGrid,move("Left clamp\nClose",ControllerEngine.Group.ARM,'X'),0,1);
        gridCell(gripGrid,move("Right clamp\nOpen",ControllerEngine.Group.GRIP,'E'),1,0);
        gridCell(gripGrid,move("Right clamp\nClose",ControllerEngine.Group.GRIP,'C'),1,1);
        clamps.addView(gripGrid,new LinearLayout.LayoutParams(-1,-1));
        controls.addView(clamps,new LinearLayout.LayoutParams(0,-1,1));
        content.addView(controls,new LinearLayout.LayoutParams(-1,0,1));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void installPauseHandler() {
        padStop.setOnClickListener(v->togglePause());
        padStop.setOnTouchListener((view,event)->{
            if(!view.isEnabled())return false;
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                view.setPressed(true);togglePause();
            }else if(event.getActionMasked()==MotionEvent.ACTION_UP || event.getActionMasked()==MotionEvent.ACTION_CANCEL){
                view.setPressed(false);
            }
            return true;
        });
    }
    private void togglePause() {
        if(!engine.ready)return;
        if(engine.locked)engine.resume();else engine.stop(true);
    }
    private void showDiagnostics() {
        TextView text=label(bluetooth.diagnostics()+"\n\nReceived from robot:\n"+(terminalLog.length()==0?"No received data":terminalLog.toString()),11,TEXT,false);
        text.setTextIsSelectable(true);text.setTypeface(Typeface.MONOSPACE);text.setPadding(dp(16),dp(12),dp(16),dp(12));
        ScrollView scroll=new ScrollView(this);scroll.addView(text);
        activeDialog=new AlertDialog.Builder(this).setTitle("Bluetooth diagnostics").setView(scroll).setPositiveButton("Close",null).create();
        activeDialog.show();
    }

    private View speedControl(boolean faster) {
        LinearLayout holder=column();holder.setGravity(Gravity.CENTER);
        Button b=button(faster?"＋":"−",MINT,INK);
        b.setContentDescription(faster?"Speed up":"Speed down");
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(0,0,0,0);b.setTextSize(24);b.setGravity(Gravity.CENTER);
        GradientDrawable circle=new GradientDrawable();circle.setShape(GradientDrawable.OVAL);circle.setColor(MINT);
        b.setBackground(circle);
        b.setOnClickListener(v->engine.changeSpeed(faster));
        holder.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView caption=label(faster?"Speed up":"Speed down",10,MINT,false);
        caption.setPadding(0,0,0,0);caption.setGravity(Gravity.CENTER);holder.addView(caption);
        if(faster)speedUp=b;else speedDown=b;
        return holder;
    }

    private void gridCell(GridLayout grid,View cell,int row,int column){
        if(cell instanceof Button){
            Button b=(Button)cell;b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
            b.setTextSize(14);b.setPadding(dp(3),dp(2),dp(3),dp(2));b.setGravity(Gravity.CENTER);
        }
        GridLayout.LayoutParams p=new GridLayout.LayoutParams(GridLayout.spec(row,1f),GridLayout.spec(column,1f));
        p.width=0;p.height=0;p.setGravity(Gravity.FILL);p.setMargins(dp(3),dp(3),dp(3),dp(3));grid.addView(cell,p);
    }

    @SuppressLint("ClickableViewAccessibility")
    private Button move(String name, ControllerEngine.Group group, char command) {
        Button button=button(name,LINE,MINT); button.setMinHeight(dp(group==ControllerEngine.Group.DRIVE?56:60));
        final String owner="button:"+command;
        button.setOnTouchListener((view,event) -> {
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if(!button.isEnabled()) return false;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    char mapped=ButtonMapping.map(command,reverseDrive,reverseArm,reverseGrip);
                    engine.start(group,owner,mapped); return true;
                case MotionEvent.ACTION_MOVE:
                    if(event.getX()<0 || event.getX()>view.getWidth() || event.getY()<0 || event.getY()>view.getHeight()) engine.release(group,owner);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    engine.release(group,owner); view.getParent().requestDisallowInterceptTouchEvent(false); return true;
                default: return true;
            }
        });
        // TalkBack/keyboard activation is a bounded pulse rather than a toggle.
        button.setOnClickListener(v -> {
            char mapped=ButtonMapping.map(command,reverseDrive,reverseArm,reverseGrip);
            engine.start(group,owner,mapped); main.postDelayed(() -> engine.release(group,owner),160);
        });
        motion.add(button); return button;
    }
    private void render() {
        boolean connected=bluetooth.isConnected(), enabled=engine.ready&&!engine.locked;
        connectionTitle.setText(connecting?"Connecting…":connected?engine.name:"REC PAD 1.0.10 · Disconnected");
        connectionDetail.setText(engine.locked?(engine.stopConfirmed?"Paused · Firmware reports STOPPED":"Paused · Sending stop"):connected?"Hold to move · Release to stop":"Pair HC-05 in Bluetooth settings");
        connectButton.setText(connecting||connected?"Disconnect":"Connect BT");
        for(Button b:motion){b.setEnabled(enabled);b.setAlpha(enabled?1f:.35f);}
        for(Button b:new Button[]{speedUp,speedDown}){b.setEnabled(enabled);b.setAlpha(enabled?1f:.35f);}
        padStop.setEnabled(engine.ready);padStop.setAlpha(engine.ready?1f:.4f);
        padStop.setText(engine.locked?"▶  Resume":"Ⅱ  Pause");
        int displayed=engine.requestedSpeed>=0?engine.requestedSpeed:engine.speed;
        speedProgress.setProgress(Math.max(0,displayed));
        speedLabel.setText(displayed<0?"Speed · No feedback":"PWM "+displayed+"/255 · "+Math.round(displayed*100f/255)+"%");
        speedFeedback.setText(!engine.ready?"Connect to read speed":engine.speedConfirmed?"Confirmed PWM · not road speed":displayed<0?"No speed feedback received":"Target PWM · Awaiting confirmation");
        if(terminalSend!=null)terminalSend.setEnabled(connected);
        if(engine.ready)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private static String angle(int n) { return n<0?"—":n+"°"; }
    private static String driveLabel(String s) {
        switch(s) { case "IDLE":return "Awaiting connection"; case "STOPPED":return "Stopped"; case "STARTING":return "Starting"; case "MOVING":return "Moving"; case "F":return "Forward (demo)";case "B":return "Reverse (demo)";case "L":return "Left (demo)";case "R":return "Right (demo)";default:return s; }
    }
    private void disconnect() { connecting=false; engine.disconnect(); bluetooth.disconnect(); render(); }

    @SuppressLint("MissingPermission")
    private void showDevices() {
        engine.stop(false);
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},101); return;
        }
        BluetoothManager manager=getSystemService(BluetoothManager.class); BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null) { notice("Bluetooth is unavailable on this device."); return; }
        if(!adapter.isEnabled()) { notice("Enable Bluetooth and pair HC-05 in system settings."); startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return; }
        List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
        devices.sort(Comparator.comparing(d -> d.getName()==null?"":d.getName()));
        if(devices.isEmpty()) {
            activeDialog=new AlertDialog.Builder(this).setTitle("No paired devices").setMessage("Pair HC-05 / HC-06 in Bluetooth settings, then return here to connect.")
                .setPositiveButton("Open settings",(d,w)->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))).setNegativeButton("Cancel",null).create(); activeDialog.show(); return;
        }
        String[] names=new String[devices.size()];
        for(int i=0;i<devices.size();i++) names[i]=(devices.get(i).getName()==null?"Unnamed device":devices.get(i).getName())+"\n"+devices.get(i).getAddress();
        activeDialog=new AlertDialog.Builder(this).setTitle("Select paired HC-05 / HC-06").setItems(names,(d,index)->{
            if(engine.mode==ControllerEngine.Mode.DEMO) engine.disconnect();
            connecting=true; render(); bluetooth.connect(devices.get(index));
        }).setNegativeButton("Cancel",null).create(); activeDialog.show();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==101) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) showDevices();
            else notice("Allow Nearby devices / Bluetooth connection permission in the app settings.");
        }
    }
    private void showSettings() {
        engine.stop(false);
        LinearLayout settings=column(); settings.setPadding(dp(20),dp(8),dp(20),dp(12));
        CheckBox drive=new CheckBox(this); drive.setText("Reverse forward / reverse"); drive.setChecked(reverseDrive); drive.setTextColor(TEXT); drive.setOnCheckedChangeListener((v,c)->{reverseDrive=c;getPreferences(MODE_PRIVATE).edit().putBoolean("reverseDrive",c).apply();}); settings.addView(drive);
        CheckBox arm=new CheckBox(this); arm.setText("Reverse left clamp open / close"); arm.setChecked(reverseArm); arm.setTextColor(TEXT); arm.setOnCheckedChangeListener((v,c)->{reverseArm=c;getPreferences(MODE_PRIVATE).edit().putBoolean("reverseArm",c).apply();}); settings.addView(arm);
        CheckBox grip=new CheckBox(this); grip.setText("Reverse right clamp open / close"); grip.setChecked(reverseGrip); grip.setTextColor(TEXT); grip.setOnCheckedChangeListener((v,c)->{reverseGrip=c;getPreferences(MODE_PRIVATE).edit().putBoolean("reverseGrip",c).apply();}); settings.addView(grip);
        settings.addView(label("Bluetooth Classic / SPP for HC-05 / HC-06; pair in system settings before connecting. Only one terminal can connect at a time.\n\nThe terminal does not require REC1. Available actions depend on the Arduino sketch.\n\nNano / Uno + L298N\nIN1 D2 · IN2 D3 · IN3 D4 · IN4 D7\nENA D5 · ENB D6 · Left clamp D9 · Right clamp D10\n\nHC-05 TX to D0; match voltage levels from D1 to HC-05 RX. Use a common ground. Disconnect D0 / D1 module wires before uploading.\n\nREC_BLE_Robot firmware supports all controls and actuator timeouts. Its UART_BAUD defaults to 9600 and must match the module. The app does not configure UART baud.\n\nHeld actions repeat every 120 ms. Release sends S for wheels; pause sends separate S and ! lines and locks controls until Resume. Companion firmware stops each actuator after about 600 ms without its motion command. Legacy firmware may not stop on disconnection. The app disconnects in the background. Software stop does not cut power.\n\nCalibrate directions and angles with the mechanism unloaded. Servos may move on power-up.\n\nHandbook: maximum size 20 × 20 × 20 cm; nominal battery voltage at most 9 V; physical power switch required; carry at most two blocks and do not push or drag blocks on the ground. Complete your own target placement before entering the Center Zone. Follow referee instructions for start, finish, and Retry.",12,MUTED,false));
        ScrollView scroll=new ScrollView(this); scroll.addView(settings);
        activeDialog=new AlertDialog.Builder(this).setTitle("Settings and connection help").setView(scroll).setPositiveButton("Done",null)
            .setNeutralButton("Firmware and setup guide",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/shenenleow10521/RemoteControl/blob/main/android/README.md")))).create(); activeDialog.show();
    }
    private void appendLog(String text) {
        terminalLog.append(text);
        if(terminalLog.length()>12000) terminalLog.delete(0,terminalLog.length()-8000);
        if(terminalOutput!=null) terminalOutput.setText(terminalLog.toString());
    }
    private void showTerminal() {
        engine.stop(false);
        LinearLayout terminal=column(); terminal.setPadding(dp(16),0,dp(16),dp(12));
        terminal.addView(label("Bluetooth Classic / SPP\nSend commands after connecting; no Arduino handshake required.",11,MUTED,false));
        terminalOutput=label(terminalLog.length()==0?"Waiting for data…":terminalLog.toString(),11,MINT,false);
        terminalOutput.setTypeface(Typeface.MONOSPACE); terminalOutput.setTextIsSelectable(true);
        ScrollView logs=new ScrollView(this); logs.setBackground(background(BG,8)); logs.setPadding(dp(9),dp(9),dp(9),dp(9)); logs.addView(terminalOutput);
        terminal.addView(logs,new LinearLayout.LayoutParams(-1,dp(200)));
        EditText input=new EditText(this); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setHint("Enter command: F, S, 90…"); input.setSingleLine(true); input.setMaxLines(1); terminal.addView(input);
        Spinner ending=new Spinner(this);
        String[] endings={"None","LF","CR","CR+LF"};
        ending.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,endings)); terminal.addView(ending);
        terminalSend=button("Send →",MINT,INK); terminalSend.setEnabled(bluetooth.isConnected());
        terminalSend.setOnClickListener(v -> {
            if(!bluetooth.isConnected()) { notice("Connect to HC-05 first."); return; }
            String raw=input.getText().toString();
            if(raw.isEmpty()) return;
            if(raw.length()>256) { notice("Maximum 256 characters per message."); return; }
            String[] suffix={"","\n","\r","\r\n"};
            bluetooth.sendText(raw+suffix[ending.getSelectedItemPosition()],false);
            appendLog("\n[TX submitted] "+raw+"\n"); input.setText("");
        }); terminal.addView(terminalSend);
        LinearLayout macros=row();
        for(String command:new String[]{"F","B","L","R","S"}) {
            Button b=button(command,LINE,MINT);
            b.setOnClickListener(v -> {
                if(!bluetooth.isConnected()) { notice("Connect to HC-05 first."); return; }
                bluetooth.sendText(command,command.equals("S")); appendLog("\n[TX submitted] "+command+"\n");
            }); macros.addView(b,weight());
        }
        terminal.addView(macros);
        terminal.addView(label("Terminal shortcuts send once per tap. Motion behavior depends on your Arduino sketch. Send S to stop. Legacy firmware may not stop on disconnection.",10,MUTED,false));
        activeDialog=new AlertDialog.Builder(this).setTitle("Serial terminal").setView(terminal)
            .setPositiveButton("Close",(d,w)->{ engine.stop(false); terminalOutput=null; terminalSend=null; })
            .setNeutralButton("Clear log",null).create();
        activeDialog.setOnCancelListener(d -> { engine.stop(false); terminalOutput=null; terminalSend=null; });
        activeDialog.show();
        activeDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { terminalLog.setLength(0); terminalOutput.setText("Log cleared"); });
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus); if(!hasFocus && engine!=null) engine.stop(false);
    }
    @Override protected void onPause() { if(engine!=null) engine.stop(true); super.onPause(); }
    @Override protected void onStop() { if(engine!=null) { connecting=false; engine.disconnect(); bluetooth.disconnect(); } super.onStop(); }
    @Override protected void onDestroy() {
        destroyed=true; main.removeCallbacks(ticker);
        if(engine!=null) { engine.disconnect(); bluetooth.disconnect(); }
        super.onDestroy();
    }
    private void notice(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setMotionEventSplittingEnabled(true);return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setMotionEventSplittingEnabled(true);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
    private View blank() { return new View(this); }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(3),dp(4),dp(3),dp(4));return p; }
    private void space(LinearLayout parent,int height) { View v=new View(this);parent.addView(v,new LinearLayout.LayoutParams(1,dp(height))); }
    private TextView label(String text,int size,int color,boolean bold) {
        TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setPadding(0,dp(4),0,dp(4));v.setLineSpacing(dp(3),1);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private GradientDrawable background(int color,int radius) { GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g; }
    private LinearLayout panel() { LinearLayout v=column();v.setPadding(dp(18),dp(17),dp(18),dp(17));v.setBackground(background(PANEL,17));return v; }
    private Button button(String text,int color,int textColor) {
        Button v=new Button(this);v.setText(text);v.setTextColor(textColor);v.setTextSize(13);v.setAllCaps(false);v.setMinHeight(dp(50));v.setMinimumHeight(dp(50));v.setPadding(dp(10),dp(8),dp(10),dp(8));v.setBackground(background(color,11));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));v.setLayoutParams(p);return v;
    }
}
