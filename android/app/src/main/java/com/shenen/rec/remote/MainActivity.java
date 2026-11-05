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
    private TextView connectionTitle, connectionDetail, telemetry, speedText, timerText, modeText, logText;
    private Button connectButton, demoButton, resumeButton, speedUp, speedDown, stopButton, padStop, statusButton, clockButton;
    private boolean connecting, destroyed, reverseArm, reverseGrip;
    private AlertDialog activeDialog;
    private TextView terminalOutput;
    private Button terminalSend;
    private final StringBuilder terminalLog = new StringBuilder();
    private int duration = 180, remaining = 180;
    private boolean clockRunning;
    private long clockEnd;
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            engine.tick();
            if (clockRunning) {
                remaining = Math.max(0, (int)Math.ceil((clockEnd - SystemClock.elapsedRealtime()) / 1000.0));
                if (remaining == 0) { clockRunning = false; engine.stop(true); notice("计时结束，已停止；以裁判指令为准。"); }
                renderClock();
            }
            main.postDelayed(this, 120);
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        buildUI(); render(); renderClock(); main.post(ticker);
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
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = column(); content.setPadding(dp(20), dp(22), dp(20), dp(28)); scroll.addView(content);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1,-1)); setContentView(outer);

        TextView brand = label("R↗   REC REMOTE", 23, MINT, true); content.addView(brand);
        TextView sub = label("ANDROID  /  BLUETOOTH CLASSIC  /  HC-05", 9, MUTED, false); sub.setLetterSpacing(.1f); content.addView(sub);
        space(content, 25);
        content.addView(label("稳稳夹起，精准放下。", 25, TEXT, true));
        content.addView(label("方向、机械臂、夹爪，一屏掌控。", 12, MUTED, false)); space(content, 20);

        LinearLayout conn = panel(); connectionTitle = label("", 15, TEXT, true); conn.addView(connectionTitle);
        connectionDetail = label("", 11, MUTED, false); conn.addView(connectionDetail); space(conn, 12);
        connectButton = button("连接机器人", MINT, INK); connectButton.setOnClickListener(v -> {
            if (connecting || engine.mode == ControllerEngine.Mode.ROBOT || engine.mode == ControllerEngine.Mode.HANDSHAKE || engine.mode == ControllerEngine.Mode.TERMINAL) disconnect();
            else showDevices();
        }); conn.addView(connectButton); content.addView(conn); space(content, 16);

        LinearLayout drive = panel(); drive.addView(label("01 / MOVEMENT", 10, MINT, true));
        LinearLayout titleRow = row(); titleRow.addView(label("行驶控制",20,TEXT,true), weight());
        modeText=label("",11,MUTED,false); titleRow.addView(modeText); drive.addView(titleRow); space(drive,16);
        LinearLayout first = row(); first.addView(blank(), weight()); first.addView(move("↑\n前进", ControllerEngine.Group.DRIVE,'F'), weight()); first.addView(blank(),weight()); drive.addView(first);
        LinearLayout middle = row(); middle.addView(move("←\n左转",ControllerEngine.Group.DRIVE,'L'),weight());
        padStop=button("■\n停止",Color.rgb(64,43,47),Color.rgb(255,180,170)); padStop.setMinHeight(dp(76)); padStop.setOnClickListener(v -> emergency()); middle.addView(padStop,weight());
        middle.addView(move("→\n右转",ControllerEngine.Group.DRIVE,'R'),weight()); drive.addView(middle);
        LinearLayout last=row(); last.addView(blank(),weight()); last.addView(move("↓\n后退",ControllerEngine.Group.DRIVE,'B'),weight()); last.addView(blank(),weight()); drive.addView(last);
        drive.addView(label("按住移动 · 松开停止对应动作",11,MUTED,false)); space(drive,15);
        speedText=label("",17,MINT,true); drive.addView(speedText);
        LinearLayout speedRow=row(); speedDown=button("−  降低速度",LINE,TEXT); speedUp=button("+  提高速度",LINE,TEXT);
        speedDown.setOnClickListener(v -> engine.changeSpeed(false)); speedUp.setOnClickListener(v -> engine.changeSpeed(true));
        speedRow.addView(speedDown,weight()); speedRow.addView(speedUp,weight()); drive.addView(speedRow);
        drive.addView(label("PWM 80–255 · 每次调整 20",10,MUTED,false)); content.addView(drive); space(content,16);

        LinearLayout mechanism=panel(); mechanism.addView(label("02 / PICK & PLACE",10,MINT,true)); mechanism.addView(label("机械臂与夹爪",20,TEXT,true)); space(mechanism,14);
        LinearLayout armRow=row(); armRow.addView(move("↥  机械臂抬起",ControllerEngine.Group.ARM,'W'),weight()); armRow.addView(move("↧  机械臂放下",ControllerEngine.Group.ARM,'X'),weight()); mechanism.addView(armRow);
        LinearLayout gripRow=row(); gripRow.addView(move("↔  夹爪张开",ControllerEngine.Group.GRIP,'E'),weight()); gripRow.addView(move("⇥⇤  夹爪夹紧",ControllerEngine.Group.GRIP,'C'),weight()); mechanism.addView(gripRow);
        mechanism.addView(label("按住微调，松开保持位置。方向可在设置中反转。",10,MUTED,false)); content.addView(mechanism); space(content,16);
        stopButton=button("■  急停 / STOP ALL",Color.rgb(123,58,57),Color.rgb(255,225,218)); stopButton.setMinHeight(dp(70)); stopButton.setOnClickListener(v -> emergency()); content.addView(stopButton);
        resumeButton=button("已急停 · 点击恢复操作 →",MINT,INK); resumeButton.setOnClickListener(v -> engine.resume()); content.addView(resumeButton); space(content,16);

        LinearLayout feedback=panel(); feedback.addView(label("ROBOT FEEDBACK",10,MINT,true)); telemetry=label("",15,TEXT,true); feedback.addView(telemetry);
        logText=label("",11,MUTED,false); logText.setTypeface(Typeface.MONOSPACE); feedback.addView(logText);
        statusButton=button("刷新机器人状态 ↺",LINE,TEXT); statusButton.setOnClickListener(v -> engine.status()); feedback.addView(statusButton); content.addView(feedback); space(content,16);

        LinearLayout clock=panel(); clock.addView(label("MATCH CLOCK / 本地辅助",10,MINT,true));
        timerText=label("03:00",46,TEXT,false); timerText.setTypeface(Typeface.MONOSPACE); timerText.setGravity(Gravity.CENTER); clock.addView(timerText);
        clockButton=button("开始计时 ↗",MINT,INK); clockButton.setOnClickListener(v -> {
            if (clockRunning) { clockRunning=false; engine.stop(true); }
            else if (remaining>0) { clockEnd=SystemClock.elapsedRealtime()+remaining*1000L; clockRunning=true; notice("机器人只能在裁判信号后开始操作。"); }
            renderClock();
        }); clock.addView(clockButton);
        LinearLayout clockOptions=row(); Button match=button("比赛 3:00",LINE,TEXT), calibration=button("校准 1:00",LINE,TEXT);
        match.setOnClickListener(v -> resetClock(180)); calibration.setOnClickListener(v -> resetClock(60));
        clockOptions.addView(match,weight()); clockOptions.addView(calibration,weight()); clock.addView(clockOptions);
        Button retry=button("↶  停止并申请 Retry",LINE,TEXT); retry.setOnClickListener(v -> { engine.stop(true); notice("已停止。请向裁判申请 Retry；比赛计时不会重置。"); }); clock.addView(retry);
        clock.addView(label("听从裁判指令；计时到零会发送停止命令。",10,MUTED,false)); content.addView(clock); space(content,16);

        demoButton=button("进入演示 · 不连接机器人",LINE,MINT); demoButton.setOnClickListener(v -> {
            if(engine.mode==ControllerEngine.Mode.DEMO) disconnect();
            else { disconnect(); engine.demo(); notice("演示模式不会发送到任何真实机器人。"); }
        }); content.addView(demoButton);
        LinearLayout more=row(); Button settings=button("设置 / 接线",PANEL,TEXT), pairing=button("系统蓝牙配对 ↗",PANEL,TEXT);
        settings.setOnClickListener(v -> showSettings()); pairing.setOnClickListener(v -> { engine.stop(false); startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); });
        more.addView(settings,weight()); more.addView(pairing,weight()); content.addView(more);
        Button terminal=button("串口终端 · 收发原始指令 ↗",MINT,INK); terminal.setOnClickListener(v -> showTerminal()); content.addView(terminal); space(content,12);
        content.addView(label("REC / BUILD. PICK. PLACE.\n本软件不判断方块、得分或机器人场地位置。",10,MUTED,false));
    }

    @SuppressLint("ClickableViewAccessibility")
    private Button move(String name, ControllerEngine.Group group, char command) {
        Button button=button(name,LINE,MINT); button.setMinHeight(dp(group==ControllerEngine.Group.DRIVE?76:60));
        final String owner="button:"+command;
        button.setOnTouchListener((view,event) -> {
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if(!button.isEnabled()) return false;
                    view.getParent().requestDisallowInterceptTouchEvent(true);
                    char mapped=command;
                    if(group==ControllerEngine.Group.ARM && reverseArm) mapped=command=='W'?'X':'W';
                    if(group==ControllerEngine.Group.GRIP && reverseGrip) mapped=command=='E'?'C':'E';
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
            char mapped=command;
            if(group==ControllerEngine.Group.ARM && reverseArm) mapped=command=='W'?'X':'W';
            if(group==ControllerEngine.Group.GRIP && reverseGrip) mapped=command=='E'?'C':'E';
            engine.start(group,owner,mapped); main.postDelayed(() -> engine.release(group,owner),160);
        });
        motion.add(button); return button;
    }
    private void render() {
        boolean demo=engine.mode==ControllerEngine.Mode.DEMO;
        connectionTitle.setText(demo?"演示模式 · 未连接机器人":engine.ready?engine.name:connecting?"正在连接蓝牙…":engine.mode==ControllerEngine.Mode.HANDSHAKE?"正在确认机器人固件…":"尚未连接机器人");
        connectionDetail.setText(demo?"所有数据仅为模拟，不会发到机器人":engine.mode==ControllerEngine.Mode.TERMINAL?"SPP 透传已连接；指令按 Arduino 现有程序执行。":engine.ready?"HC-05 / SPP 已连接，配套固件已确认":"先在系统设置配对 HC-05，再在这里连接。");
        connectButton.setText(connecting || engine.mode==ControllerEngine.Mode.ROBOT || engine.mode==ControllerEngine.Mode.HANDSHAKE || engine.mode==ControllerEngine.Mode.TERMINAL?"断开连接":"连接机器人");
        modeText.setText(demo?"演示":engine.locked?"已急停":engine.mode==ControllerEngine.Mode.TERMINAL?"串口透传":engine.ready?"已连接":"未连接");
        for(Button b:motion) { b.setEnabled(engine.ready&&!engine.locked); b.setAlpha(b.isEnabled()?1f:.35f); }
        for(Button b:new Button[]{speedUp,speedDown,stopButton,padStop,statusButton}) { b.setEnabled(engine.ready); b.setAlpha(engine.ready?1f:.4f); }
        resumeButton.setVisibility(engine.locked?View.VISIBLE:View.GONE);
        speedText.setText("行驶速度  "+(engine.speed<0?"—":engine.speed)+" / 255");
        telemetry.setText("机械臂 "+angle(engine.arm)+"     夹爪 "+angle(engine.grip)+"\n行驶："+(engine.mode==ControllerEngine.Mode.TERMINAL?"串口透传，等待固件反馈":driveLabel(engine.drive)));
        logText.setText("最后命令："+engine.lastCommand+"\n"+engine.feedback+"\n角度为固件指令值，不是传感器测量。");
        demoButton.setText(demo?"退出演示":"进入演示 · 不连接机器人");
        if(terminalSend!=null) terminalSend.setEnabled(bluetooth.isConnected());
        if(engine.ready) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private static String angle(int n) { return n<0?"—":n+"°"; }
    private static String driveLabel(String s) {
        switch(s) { case "IDLE":return "待连接"; case "STOPPED":return "已停止"; case "STARTING":return "准备行驶"; case "MOVING":return "行驶中"; case "F":return "前进中（演示）";case "B":return "后退中（演示）";case "L":return "左转中（演示）";case "R":return "右转中（演示）";default:return s; }
    }
    private void renderClock() {
        timerText.setText(String.format(Locale.US,"%02d:%02d",remaining/60,remaining%60));
        clockButton.setText(clockRunning?"暂停并停止 Ⅱ":remaining==0?"计时结束":"开始计时 ↗"); clockButton.setEnabled(remaining>0);
    }
    private void resetClock(int seconds) { engine.stop(false); duration=remaining=seconds; clockRunning=false; renderClock(); }
    private void emergency() { engine.stop(true); notice("已发送停止命令；继续前点击「恢复操作」。实体开关才能切断电源。"); }
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
        CheckBox arm=new CheckBox(this); arm.setText("反转机械臂方向"); arm.setChecked(reverseArm); arm.setTextColor(TEXT); arm.setOnCheckedChangeListener((v,c)->reverseArm=c); settings.addView(arm);
        CheckBox grip=new CheckBox(this); grip.setText("反转夹爪方向"); grip.setChecked(reverseGrip); grip.setTextColor(TEXT); grip.setOnCheckedChangeListener((v,c)->reverseGrip=c); settings.addView(grip);
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
