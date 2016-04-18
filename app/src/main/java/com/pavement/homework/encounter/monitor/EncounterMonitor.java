package com.pavement.homework.encounter.monitor;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class EncounterMonitor extends Service {
    private static final String TAG = "EncounterMonitor";
    static final public String UI_UPDATE = "com.pavement.homework.encounter.monitor.ui.update";
    static final public String UI_UPDATE_CONTENT = "com.pavement.homework.encounter.monitor.ui.update.content";

    LocalBroadcastManager broadcastManager;
    Intent intentUI;

    PowerManager.WakeLock wakeLock;
    BluetoothAdapter mBTAdapter;
    String btName;
    String userName;
    ArrayList<String> listDevices;
    Date foundDate;
    Date lostDate;
    boolean isEncountering = false;

    Timer timer = new Timer();
    TimerTask timerTask = null;

    Vibrator vib;

    // BT 검색과 관련한 broadcast 를 받을 BroadcastReceiver 객체 정의
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String output;
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA);

            switch (action) {
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    // discovery 시작됨
                    // 아래는 toast 메시지 표시하는 코드
                    Toast.makeText(getApplicationContext(), "Bluetooth scan started..", Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    Toast.makeText(getApplicationContext(), "Bluetooth scan finished", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "the number of found devices: " + listDevices.size());
                    for(String device: listDevices) {
                        Log.d(TAG, "device name: " + device);
                    }
                    if( isEncountering && !listDevices.contains(btName) ) {
                        long differences;
                        isEncountering = false;

                        lostDate = new Date();
                        output = formatter.format(lostDate);
                        Log.i("TEST", "Encounter 종료 시점: " + output);  //Encounter 종료 시점


                        //Encounter 시작 시점과 종료 시점 간의 시간차를 분으로 환산
                        differences = lostDate.getTime() - foundDate.getTime() ;
                        long minutes = TimeUnit.MILLISECONDS.toMinutes(differences);
                        Log.d(TAG, lostDate.getTime() + " " + foundDate.getTime() );
                        Log.d(TAG, "지속시간: " + minutes + " 분");
                        String message = formatter.format(foundDate) + " (" + minutes + "분)";

                        //Encounter 에 관한 정보를 액티비티로 전달하기 위한 방송
                        intentUI.putExtra(UI_UPDATE_CONTENT, message);
                        broadcastManager.sendBroadcast(intentUI);

                        //
                        lostDate = null;
                        foundDate = null;
                    }

                    listDevices.clear();
                    break;
                case BluetoothDevice.ACTION_FOUND:
                    // Bluetooth device 가 검색 됨
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    String deviceName = device.getName();
                    //int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                    listDevices.add(deviceName);

                    if (btName != null && btName.equals( deviceName )) {
                        // 검색된 디바이스 이름이 등록된 디바이스 이름과 같으면
                        // 진동과 Toast 메시지 표시
                        isEncountering = true;

                        if(foundDate == null) {
                            foundDate = new Date();
                            output = formatter.format(foundDate);
                            Log.i("TEST", "Encounter 시작 시점 " + output);  //Encounter 시작 시점String message = formatter.format(foundDate) + " (" + minutes + "분)";


                        }

                                vib.vibrate(200);

                        Toast.makeText(getApplicationContext(), " You encounter " + userName,
                                Toast.LENGTH_LONG).show();
                    }
                    break;
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");

        broadcastManager = LocalBroadcastManager.getInstance(EncounterMonitor.this);
        intentUI = new Intent(UI_UPDATE);

        //서비스가 실행중인 동안 CPU 를 항상 켜놓도록 WakeLock 을 설정한다.
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyWakelockTag");
        wakeLock.acquire();

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        vib = (Vibrator)getSystemService(VIBRATOR_SERVICE);

        listDevices = new ArrayList<>();

        // BT 디바이스 검색 관련하여 어떤 종류의 broadcast 를 받을 것인지 IntentFilter 로 설정
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        // BroadcastReceiver 등록
        registerReceiver(mReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // intent: startService() 호출 시 넘기는 intent 객체
        // flags: service start 요청에 대한 부가 정보. 0, START_FLAG_REDELIVERY, START_FLAG_RETRY
        // startId: start 요청을 나타내는 unique integer id
        //Activity 에서 정상적으로 Intent 를 가져왔다면 BluetoothDevice 를 찾는 타이머를 작동시키고
        //Intent 를 가져오는데 실패하면 서비스를 종료시킨다.

        Toast.makeText(this, "EncounterMonitor 시작", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onStartCommand()");


        if(intent != null) {
            // MainActivity 에서 Service 를 시작할 때 사용한 intent 에 담겨진 BT 디바이스와 사용자 이름 얻음
            btName = intent.getStringExtra("BTName");
            userName = intent.getStringExtra("UserName");

            // 주기적으로 BT discovery 수행하기 위한 timer 가동
            startTimerTask();
        }
        else {
            Toast.makeText(this, "EncounterMonitor 서비스를 수행할 수 없습니다.", Toast.LENGTH_SHORT).show();
            stopSelf();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Toast.makeText(this, "EncounterMonitor 중지", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "onDestroy()");

        wakeLock.release(); //서비스가 종료될 때 WakeLock 을 해제한다.

        stopTimerTask();
        unregisterReceiver(mReceiver);
    }

    private void startTimerTask() {
        String output;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA);
        Date startDate = new Date();
        output = formatter.format(startDate);
        Log.i("TEST", output);  //모니터링 시작 시간
        //모니터링 시작 시간을 액티비티로 전달하기 위한 방송
        intentUI.putExtra(UI_UPDATE_CONTENT, "모니터링 시작: " + output);
        broadcastManager.sendBroadcast(intentUI);

        // TimerTask 생성한다
        timerTask = new TimerTask() {
            @Override
            public void run() {
                Log.d(TAG, "TimerTask 의 run() 호출");
                //블루투스 장치 탐색을 수행한다
                if(!mBTAdapter.isDiscovering())
                    mBTAdapter.startDiscovery();
            }
        };

        // TimerTask 를 Timer 를 통해 실행시킨다
        // 1초 후에 타이머를 구동하고 60초마다 반복한다
        timer.schedule(timerTask, 1000, 10000); //60000
        //*** Timer 클래스 메소드 이용법 참고 ***//
        // 	schedule(TimerTask task, long delay, long period)
        // http://developer.android.com/intl/ko/reference/java/util/Timer.html
        //***********************************//
    }

    private void stopTimerTask() {
        String output;
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.KOREA);
        Date stopDate = new Date();
        output = formatter.format(stopDate);
        Log.i("TEST", output);  //모니터링 종료 시간
        // 모니터링 종료 시간을 액티비티로 전달하기 위한 방송
        intentUI.putExtra(UI_UPDATE_CONTENT, "모니터링 종료: " + output);
        broadcastManager.sendBroadcast(intentUI);

        // 1. 모든 태스크를 중단한다
        if(timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        //블루투스 장치 탐색을 중단한다
        if(mBTAdapter != null && mBTAdapter.isDiscovering())
            mBTAdapter.cancelDiscovery();

    }
}
