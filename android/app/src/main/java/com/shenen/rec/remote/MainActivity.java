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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
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
    private TextView connectionTitle, connectionDetail;
    private Button connectButton, padStop;
    private boolean connecting, destroyed, reverseArm, reverseGrip = true, reverseDrive = true;
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
                connecting = false; engine.disconnect(); notice("连接中断：" + reason); render();
            }
        });
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        reverseDrive=prefs.getBoolean("reverseDrive",true);
        reverseGrip=prefs.getBoolean("reverseGrip",true);
        reverseArm=prefs.getBoolean("reverseArm",false);
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
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        content=column(); content.setPadding(dp(14),dp(8),dp(14),dp(8)); scroll.addView(content);
        outer.addView(scroll,new LinearLayout.LayoutParams(-1,-1)); setContentView(outer);

        LinearLayout header=row();
        LinearLayout status=column(); connectionTitle=label("REC REMOTE PAD",16,MINT,true); status.addView(connectionTitle);
        connectionDetail=label("",10,MUTED,false); status.addView(connectionDetail); header.addView(status,weight());
        connectButton=button("连接蓝牙",MINT,INK); connectButton.setOnClickListener(v->{
            if(connecting || bluetooth.isConnected() || engine.mode==ControllerEngine.Mode.HANDSHAKE) disconnect(); else showDevices();
        }); header.addView(connectButton,new LinearLayout.LayoutParams(dp(112),dp(48)));
        content.addView(header);space(content,8);

        LinearLayout controls=row(); controls.setGravity(Gravity.TOP);
        LinearLayout drive=panel();drive.setPadding(dp(12),dp(8),dp(12),dp(8));
        LinearLayout first=row();first.addView(blank(),weight());first.addView(move("↑\n前进",ControllerEngine.Group.DRIVE,'F'),weight());first.addView(blank(),weight());drive.addView(first);
        LinearLayout middle=row();middle.addView(move("←\n左转",ControllerEngine.Group.DRIVE,'L'),weight());
        padStop=button("■\n停止",Color.rgb(64,43,47),Color.rgb(255,180,170));padStop.setMinHeight(dp(56));
        padStop.setOnClickListener(v->{if(engine.locked)engine.resume();else engine.stop(false);});middle.addView(padStop,weight());
        middle.addView(move("→\n右转",ControllerEngine.Group.DRIVE,'R'),weight());drive.addView(middle);
        LinearLayout last=row();last.addView(blank(),weight());last.addView(move("↓\n后退",ControllerEngine.Group.DRIVE,'B'),weight());last.addView(blank(),weight());drive.addView(last);
        LinearLayout.LayoutParams leftParams=new LinearLayout.LayoutParams(0,-2,1.15f);leftParams.setMargins(0,0,dp(10),0);controls.addView(drive,leftParams);

        LinearLayout clamps=panel();clamps.setPadding(dp(12),dp(8),dp(12),dp(8));
        clamps.addView(label("左边夹子",14,TEXT,true));
        LinearLayout leftClamp=row();leftClamp.addView(move("开",ControllerEngine.Group.ARM,'W'),weight());leftClamp.addView(move("关",ControllerEngine.Group.ARM,'X'),weight());clamps.addView(leftClamp);
        space(clamps,8);clamps.addView(label("右边夹子",14,TEXT,true));
        LinearLayout rightClamp=row();rightClamp.addView(move("开",ControllerEngine.Group.GRIP,'E'),weight());rightClamp.addView(move("关",ControllerEngine.Group.GRIP,'C'),weight());clamps.addView(rightClamp);
        clamps.addView(label("按住微调 · 松开保持",10,MUTED,false));
        controls.addView(clamps,new LinearLayout.LayoutParams(0,-2,1f));content.addView(controls);
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
        connectionTitle.setText(connecting?"正在连接…":connected?engine.name:"REC REMOTE PAD · 未连接");
        connectionDetail.setText(engine.locked?"动作已停止，点中间「恢复」继续":connected?"HC-05 / SPP · 按住操作，松开停止":"先在系统蓝牙设置配对 HC-05");
        connectButton.setText(connecting||connected?"断开蓝牙":"连接蓝牙");
        for(Button b:motion){b.setEnabled(enabled);b.setAlpha(enabled?1f:.35f);}
        padStop.setEnabled(engine.ready);padStop.setAlpha(engine.ready?1f:.4f);
        padStop.setText(engine.locked?"▶  恢复":"■  停止");
        if(terminalSend!=null)terminalSend.setEnabled(connected);
        if(engine.ready)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private static String angle(int n) { return n<0?"—":n+"°"; }
    private static String driveLabel(String s) {
        switch(s) { case "IDLE":return "待连接"; case "STOPPED":return "已停止"; case "STARTING":return "准备行驶"; case "MOVING":return "行驶中"; case "F":return "前进中（演示）";case "B":return "后退中（演示）";case "L":return "左转中（演示）";case "R":return "右转中（演示）";default:return s; }
    }
    private void disconnect() { connecting=false; engine.disconnect(); bluetooth.disconnect(); render(); }

    @SuppressLint("MissingPermission")
    private void showDevices() {
        engine.stop(false);
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},101); return;
        }
        BluetoothManager manager=getSystemService(BluetoothManager.class); BluetoothAdapter adapter=manager==null?null:manager.getAdapter();
        if(adapter==null) { notice("此设备没有可用蓝牙；仍可使用演示模式。"); return; }
        if(!adapter.isEnabled()) { notice("请在系统蓝牙设置开启蓝牙并配对 HC-05。"); startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return; }
        List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
        devices.sort(Comparator.comparing(d -> d.getName()==null?"":d.getName()));
        if(devices.isEmpty()) {
            activeDialog=new AlertDialog.Builder(this).setTitle("还没有已配对设备").setMessage("先到系统蓝牙设置配对 HC-05 / HC-06，然后回到这里连接。")
                .setPositiveButton("打开设置",(d,w)->startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))).setNegativeButton("取消",null).create(); activeDialog.show(); return;
        }
        String[] names=new String[devices.size()];
        for(int i=0;i<devices.size();i++) names[i]=(devices.get(i).getName()==null?"未命名设备":devices.get(i).getName())+"\n"+devices.get(i).getAddress();
        activeDialog=new AlertDialog.Builder(this).setTitle("选择已配对的 HC-05 / HC-06").setItems(names,(d,index)->{
            if(engine.mode==ControllerEngine.Mode.DEMO) engine.disconnect();
            connecting=true; render(); bluetooth.connect(devices.get(index));
        }).setNegativeButton("取消",null).create(); activeDialog.show();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==101) {
            if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) showDevices();
            else notice("需要「附近的设备 / 蓝牙连接」权限。可在系统应用设置中允许。");
        }
    }
    private void showSettings() {
        engine.stop(false);
        LinearLayout settings=column(); settings.setPadding(dp(20),dp(8),dp(20),dp(12));
        CheckBox drive=new CheckBox(this); drive.setText("反转前进 / 后退"); drive.setChecked(reverseDrive); drive.setTextColor(TEXT); drive.setOnCheckedChangeListener((v,c)->{reverseDrive=c;getPreferences(MODE_PRIVATE).edit().putBoolean("reverseDrive",c).apply();}); settings.addView(drive);
        CheckBox arm=new CheckBox(this); arm.setText("反转左边夹子开关"); arm.setChecked(reverseArm); arm.setTextColor(TEXT); arm.setOnCheckedChangeListener((v,c)->{reverseArm=c;getPreferences(MODE_PRIVATE).edit().putBoolean("reverseArm",c).apply();}); settings.addView(arm);
        CheckBox grip=new CheckBox(this); grip.setText("反转右边夹子开关"); grip.setChecked(reverseGrip); grip.setTextColor(TEXT); grip.setOnCheckedChangeListener((v,c)->{reverseGrip=c;getPreferences(MODE_PRIVATE).edit().putBoolean("reverseGrip",c).apply();}); settings.addView(grip);
        settings.addView(label("Bluetooth Classic / SPP：适用于 HC-05 / HC-06；不是 BLE。先在系统设置配对，再在本 App 连接，不能同时被另一终端占用。\n\n串口终端连接后可直接收发，不要求 REC1 握手。旧 PWM-only 程序只支持它本身已有的指令，机械臂与夹爪不会因出现按钮就自动获得支持。\n\nNano / Uno + L298N\nIN1 D2 · IN2 D3 · IN3 D4 · IN4 D7\nENA D5 · ENB D6 · Arm D9 · Grip D10\n\nHC-05 TX → D0；D1 → HC-05 RX 须匹配电平；全部共地。上传程序前拔开 D0 / D1 的模块线。\n\n全部功能及固件断线停止需上传仓库的 REC_BLE_Robot 配套程序。UART_BAUD 默认 9600，须与 HC-05 的 UART 波特率一致；App 中没有 UART 波特率设置。\n\n旧程序不具备固件超时保护，蓝牙断线后未必能停止。按住快捷方向键每 120 ms 重发，松开发 S；急停发 S!。配套固件约 600 ms 无该执行器命令即停止。后台会断开。软件急停不切断电源。\n\n角度和方向须空载校准，默认逻辑角度 90°，上电舵机可能移动。\n\nHandbook：全程尺寸 ≤20×20×20 cm；电池标称 ≤9 V；须有实体断电开关；最多携带两块方块，不可在地面推拉。先完成己方目标放置才能进入 Center Zone。开始、结束、Retry 以裁判指令为准。",12,MUTED,false));
        ScrollView scroll=new ScrollView(this); scroll.addView(settings);
        activeDialog=new AlertDialog.Builder(this).setTitle("设置与连接说明").setView(scroll).setPositiveButton("完成",null)
            .setNeutralButton("固件与安装指南",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/shenenleow10521/RemoteControl/blob/main/android/README.md")))).create(); activeDialog.show();
    }
    private void appendLog(String text) {
        terminalLog.append(text);
        if(terminalLog.length()>12000) terminalLog.delete(0,terminalLog.length()-8000);
        if(terminalOutput!=null) terminalOutput.setText(terminalLog.toString());
    }
    private void showTerminal() {
        engine.stop(false);
        LinearLayout terminal=column(); terminal.setPadding(dp(16),0,dp(16),dp(12));
        terminal.addView(label("Bluetooth Classic / SPP 透传\n连接后可直接发送；不要求特定 Arduino 握手。",11,MUTED,false));
        terminalOutput=label(terminalLog.length()==0?"等待接收…":terminalLog.toString(),11,MINT,false);
        terminalOutput.setTypeface(Typeface.MONOSPACE); terminalOutput.setTextIsSelectable(true);
        ScrollView logs=new ScrollView(this); logs.setBackground(background(BG,8)); logs.setPadding(dp(9),dp(9),dp(9),dp(9)); logs.addView(terminalOutput);
        terminal.addView(logs,new LinearLayout.LayoutParams(-1,dp(200)));
        EditText input=new EditText(this); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setHint("输入指令，如 F、S、90…"); input.setSingleLine(true); input.setMaxLines(1); terminal.addView(input);
        Spinner ending=new Spinner(this);
        String[] endings={"不加换行 (None)","换行 (LF)","回车 (CR)","回车换行 (CR+LF)"};
        ending.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,endings)); terminal.addView(ending);
        terminalSend=button("发送 →",MINT,INK); terminalSend.setEnabled(bluetooth.isConnected());
        terminalSend.setOnClickListener(v -> {
            if(!bluetooth.isConnected()) { notice("请先连接 HC-05。"); return; }
            String raw=input.getText().toString();
            if(raw.isEmpty()) return;
            if(raw.length()>256) { notice("单次输入最多 256 个字符。"); return; }
            String[] suffix={"","\n","\r","\r\n"};
            bluetooth.sendText(raw+suffix[ending.getSelectedItemPosition()],false);
            appendLog("\n[TX 已提交] "+raw+"\n"); input.setText("");
        }); terminal.addView(terminalSend);
        LinearLayout macros=row();
        for(String command:new String[]{"F","B","L","R","S"}) {
            Button b=button(command,LINE,MINT);
            b.setOnClickListener(v -> {
                if(!bluetooth.isConnected()) { notice("请先连接 HC-05。"); return; }
                bluetooth.sendText(command,command.equals("S")); appendLog("\n[TX 已提交] "+command+"\n");
            }); macros.addView(b,weight());
        }
        terminal.addView(macros);
        terminal.addView(label("终端快捷键按一次发送一次。F 等是否持续运行由 Arduino 程序决定；需要停止时发送 S。旧固件断线不保证停机。",10,MUTED,false));
        activeDialog=new AlertDialog.Builder(this).setTitle("串口终端").setView(terminal)
            .setPositiveButton("关闭",(d,w)->{ engine.stop(false); terminalOutput=null; terminalSend=null; })
            .setNeutralButton("清空日志",null).create();
        activeDialog.setOnCancelListener(d -> { engine.stop(false); terminalOutput=null; terminalSend=null; });
        activeDialog.show();
        activeDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { terminalLog.setLength(0); terminalOutput.setText("日志已清空"); });
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
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout row() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v; }
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
