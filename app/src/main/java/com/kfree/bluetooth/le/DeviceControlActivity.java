/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kfree.bluetooth.le;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.KeyguardManager.KeyguardLock;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements OnClickListener, OnCheckedChangeListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    
    public static final String KEY_RING = "ring_check";
    public static final String KEY_VIBRATE = "vibrate_check";

    private static final long VIBRATE_DURATION = 200L;
    
    private static final boolean DEFAULT_VIBRATE = true;
    private static final boolean DEFAULT_RING = true;
    
    private static final int MSG_ENABLE_NOTIFY = 10010;
    private static final int MSG_DEVICE_CONNECT = 10011;
    private static final int MSG_DEVICE_DISCONNECT = 10012;
    
    private static final long START_TIME = 3*1000; //ms
    private static final long DELAY_TIME = 3*1000; //ms
    private static final long DELAY_CONNECT_TIME_OUT = 4*1000; // ms
 
    private static final int DEFAULT_BATTERY_LEVEL = 6;

    private TextView titleView;
    
    // private WindowManager mWindowManager;
    // private View mView;

//    private SoundPool soundPool;
    private MediaPlayer mPlayer;
    private Vibrator vibrator;
    private long[] pattern = {300, 200 ,300, 200}; // OFF/ON/OFF/ON
    
//    private TextView mConnectionState;
//    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
	
    private FrameLayout connect_layout;
    
    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private SharedPreferences mPreferences;
    
    boolean isRing = DEFAULT_RING;
    boolean isVibrate = DEFAULT_VIBRATE;


    private Chronometer timer;
    private Button btnEnd;
    private TextView btStatus;
    private TextView infusionStatus;
    private ImageView battery;
    private CheckBox ringCheck;
    private CheckBox vibrateCheck;
	
    //写数据
    private BluetoothGattCharacteristic characteristic;
    private BluetoothGattService mnotyGattService;;
    //读数据
    private BluetoothGattCharacteristic readCharacteristic;
    private BluetoothGattService readMnotyGattService;

    private boolean isConnect = false;
    private boolean isOnPause = false;
    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            btStatus.setText(R.string.bt_status_3);
            registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            btStatus.setText(R.string.bt_status_2);
            mBluetoothLeService = null;
        }
    };

    private Timer mTimer = new Timer(true);

    class MyTimerTask extends TimerTask {
	@Override
	public void run() {
	    // TODO Auto-generated method stub
	    Log.i(TAG, "run...");
	    Message msg = mHandler.obtainMessage(MSG_DEVICE_CONNECT);
	    msg.sendToTarget();
	}

    }
    // 任务
    private TimerTask mTask = new MyTimerTask();
  
    private void stopTimerTask(){
	if(mTimer != null && mTask != null){
	    mTask.cancel();
	}
    }
    
    private void startTimerTask(){
	stopTimerTask();
	
	mTask = new MyTimerTask();
	mTimer.schedule(mTask, START_TIME, DELAY_TIME);
    }
    
    private Handler mHandler  = new Handler(){
	@Override
	public void handleMessage(Message msg) {
	    // TODO Auto-generated method stub
	    switch (msg.what) {
	    case MSG_ENABLE_NOTIFY:{
                final int charaProp = readCharacteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    mNotifyCharacteristic = readCharacteristic;
                    mBluetoothLeService.setCharacteristicNotification(
                    	readCharacteristic, true);
                    if(false == isEnd){
                	infusionStatus.setText(R.string.infusion_status_ing);
                    }
                }
	    }
		break;
	    case MSG_DEVICE_CONNECT:
		connect();
		break;
	    case MSG_DEVICE_DISCONNECT:
		disconnect();
		break;
	    default:
		break;
	    }
	}
    };
    
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                updateConnectionState(R.string.bt_status_1);
                connect_layout.setVisibility(View.GONE);
                if(false == isEnd && false == isConnect){
                    //停止定时器
                    stopTimerTask();
                    
                    if(mToastDialog != null && mToastDialog.isShowing())
                	mToastDialog.dismiss();
                    
                    timer.setBase(SystemClock.elapsedRealtime());//计时器清零
                    timer.start();
                    infusionStatus.setText(R.string.infusion_status_ing);
                }
                isConnect = true;
                
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                updateConnectionState(R.string.bt_status_2);
                timer.stop();
                stopAlarm();
                if(false == isEnd)
                    infusionStatus.setText(R.string.infusion_status_stop);
                
                isConnect = false;
                //启动定时器
                startTimerTask();
                
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
//                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            	//写数据的服务和characteristic
//            	mnotyGattService = mBluetoothLeService.getSupportedGattServices(UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"));
            	mnotyGattService = mBluetoothLeService.getSupportedGattServices(UUID.fromString("d973f2e0-b19e-11e2-9e96-0800200c9a66"));
            	if(mnotyGattService != null){
            	    Log.d(TAG, SampleGattAttributes.lookup(mnotyGattService.getUuid().toString(), "---"));
//            	    characteristic = mnotyGattService.getCharacteristic(UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb"));
            	    characteristic = mnotyGattService.getCharacteristic(UUID.fromString("d973f2e2-b19e-11e2-9e96-0800200c9a66"));
        	    if(characteristic != null)
            		Log.d(TAG, SampleGattAttributes.lookup(characteristic.getUuid().toString(), "---"));
            	}
                //读数据的服务和characteristic
                readMnotyGattService = mBluetoothLeService.getSupportedGattServices(UUID.fromString("d973f2e0-b19e-11e2-9e96-0800200c9a66"));
                if(readMnotyGattService != null){
                    Log.d(TAG, SampleGattAttributes.lookup(readMnotyGattService.getUuid().toString(), "---"));
                    readCharacteristic = readMnotyGattService.getCharacteristic(UUID.fromString("d973f2e1-b19e-11e2-9e96-0800200c9a66"));
                }
                if(readCharacteristic != null){
                    Log.d(TAG, SampleGattAttributes.lookup(readCharacteristic.getUuid().toString(), "---"));
                    mHandler.sendEmptyMessage(MSG_ENABLE_NOTIFY);
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                mHandler.removeMessages(MSG_DEVICE_DISCONNECT);
                mHandler.sendEmptyMessageDelayed(MSG_DEVICE_DISCONNECT, DELAY_CONNECT_TIME_OUT);
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    @Override
    public void onBackPressed() {
	if (isConnect)
	    return;
	// TODO Auto-generated method stub
	super.onBackPressed();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ActionBar actionBar = getActionBar();//.setTitle(R.string.title_devices);
        if(actionBar != null){
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.custom_titlebar);
        }
        
        setContentView(R.layout.gatt_services_characteristics);

	// mWindowManager = (WindowManager)
	// this.getSystemService(Context.WINDOW_SERVICE);
	//
	// LayoutInflater inflater = LayoutInflater.from(this);
	// mView = inflater.inflate(R.layout.bt_content_dialog, null);

        mPreferences = this.getSharedPreferences("profiles", Context.MODE_PRIVATE);
        
        titleView = (TextView) findViewById(R.id.title);
        
        connect_layout = (FrameLayout)this.findViewById(R.id.connect_layout);
        
        timer = (Chronometer)this.findViewById(R.id.chronometer);
        btnEnd = (Button)this.findViewById(R.id.btn_end);
        btStatus = (TextView) this.findViewById(R.id.bt_status);
        battery = (ImageView) this.findViewById(R.id.battery);
        ringCheck = (CheckBox) this.findViewById(R.id.ring);
        vibrateCheck = (CheckBox) this.findViewById(R.id.vibrate);
        infusionStatus = (TextView) this.findViewById(R.id.infusion_status);
        btnEnd.setOnClickListener(this);
        battery.setImageLevel(DEFAULT_BATTERY_LEVEL);
        ringCheck.setOnCheckedChangeListener(this);
        vibrateCheck.setOnCheckedChangeListener(this);
        
        isRing = mPreferences.getBoolean(KEY_RING, DEFAULT_RING);
        isVibrate = mPreferences.getBoolean(KEY_VIBRATE, DEFAULT_VIBRATE);
        
        ringCheck.setChecked(isRing);
        vibrateCheck.setChecked(isVibrate);
        
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

//        soundPool= new SoundPool(10,AudioManager.STREAM_SYSTEM,5);
//        soundPool.load(this,R.raw.ring,1);
        
//        try {
//            mp = MediaPlayer.create(this, R.raw.ring);
//	    mp.prepare();
//	    mp.setLooping(true);
//	} catch (IllegalStateException e) {
//	    // TODO Auto-generated catch block
//	    e.printStackTrace();
//	}
	    
	vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);
        
        titleView.setText(mDeviceName);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private void playRing() {
	
	if(mPlayer != null){
	    mPlayer.release();
	    mPlayer = null;
	}
	
	mPlayer = MediaPlayer.create(this, R.raw.ring);
	// mp.prepare();
	mPlayer.setLooping(true);
	mPlayer.start();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        connect();
        isOnPause = false;
    }
    
    private void connect(){
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }	
    }

    private void disconnect(){
        if (mBluetoothLeService != null) {
            mBluetoothLeService.disconnect();
        }	
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isOnPause = true;
//        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
	timer.stop();
	if(mPlayer != null)
	    mPlayer.stop();
	
	vibrator.cancel();
	stopTimerTask();
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
	getMenuInflater().inflate(R.menu.gatt_services, menu);
	return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_rename:
        	showDialog(this);
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showDialog(Context context){
	View layout = LayoutInflater.from(context).inflate(R.layout.bt_rename_dialog, null);
	Builder builder = new Builder(context);
	builder.setTitle(R.string.menu_rename).setView(layout);
	final EditText btName = (EditText) layout.findViewById(R.id.bt_name);
	builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

	    @Override
	    public void onClick(DialogInterface dialog, int which) {
		dialog.dismiss();
	    }
	});
	builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

	    @Override
	    public void onClick(DialogInterface dialog, int which) {
		String newName = btName.getText().toString();
		if(mBluetoothLeService != null && characteristic != null){
		    if(newName!= null && !newName.isEmpty()){
			mHandler.removeMessages(MSG_DEVICE_DISCONNECT);
			mBluetoothLeService.writeCharacteristic(characteristic, newName);
			titleView.setText(newName);
			mDeviceName = newName;
		    }
//		    mBluetoothLeService.readCharacteristic(characteristic);
		}
		dialog.dismiss();
	    }
	}).show();
    }

    private AlertDialog mToastDialog;

    private void showToastDialog(Context context) {

	// WindowManager.LayoutParams para = new WindowManager.LayoutParams();
	// para.flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
	// WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
	// para.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
	// para.height = WindowManager.LayoutParams.MATCH_PARENT;
	// para.width = WindowManager.LayoutParams.MATCH_PARENT;
	// para.format = PixelFormat.TRANSLUCENT;
	// mWindowManager.addView(mView, para);
	//
	// Button ok = (Button) mView.findViewById(R.id.btn_ok);
	// ok.setOnClickListener(new View.OnClickListener() {
	//
	// @Override
	// public void onClick(View v) {
	// // TODO Auto-generated method stub
	// android.util.Log.d("ManagementToolsReceiver", "btn_dismiss");
	// if (mWindowManager != null && mView != null) {
	// mWindowManager.removeViewImmediate(mView);
	// }
	// // mView = null;
	// }
	// });

	// KeyguardManager km = (KeyguardManager)
	// context.getSystemService(Context.KEYGUARD_SERVICE);
	// if (km.inKeyguardRestrictedInputMode()) {
	//
	// Intent alarmIntent = new Intent(context, Messagedialog.class);
	// alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	// context.startActivity(alarmIntent);
	//
	// } else {
	if (mToastDialog == null) {//
	    // View layout =
	    // LayoutInflater.from(context).inflate(R.layout.bt_content_dialog,
	    // null);
	    mToastDialog = new AlertDialog.Builder(context).setTitle(getResources()
		    .getString(R.string.error_title))
		    .setIcon(android.R.drawable.ic_dialog_alert).setCancelable(false)
		    .setMessage(mDeviceName + " " + getResources().getString(R.string.infusion_status_end))
		    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
			    stopAlarm();
			    mBluetoothLeService.disconnect();
			    dialog.dismiss();
			}
		    }).create();
	    mToastDialog.setCanceledOnTouchOutside(false);
	} else {
	    mToastDialog.setMessage(mDeviceName + " " + getResources().getString(R.string.infusion_status_end));
	}
	mToastDialog.show();

	PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
	if (!pm.isScreenOn()) {
	    PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP
		    | PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "ble");
	    wl.acquire();
	    
	    KeyguardManager km= (KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
	    KeyguardLock  kl = km.newKeyguardLock("ble");
	    kl.disableKeyguard();
	    //kl.reenableKeyguard();
	    wl.release();

	}
	
	if(isOnPause){
	    Intent intent = new Intent(Intent.ACTION_MAIN)
            .setClass(this, this.getClass())
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
	    startActivity(intent);	    
	}
	// }
    }
    
    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                mConnectionState.setText(resourceId);
        	btStatus.setText(resourceId);
            }
        });
    }

    private void startAlarm(){
	if (isRing) {
	    playRing();
	}
        if(isVibrate){
  	  vibrator.vibrate(VIBRATE_DURATION);
  	  vibrator.vibrate(pattern,0);
        }
//        if(mBluetoothLeService != null)	
//            mBluetoothLeService.disconnect();
    }
    
    private void pauseAlarm(){
	timer.stop();
	infusionStatus.setText(R.string.infusion_status_pause);
    }
    
    private void stopAlarm(){
	isConnect = false;
	if (isRing && mPlayer != null) {
	    mPlayer.stop();
	}
	if (isVibrate) {
	    vibrator.cancel();
	}	
    }
    
    private boolean isEnd = false;
    private int currentStatus = 0;
    private void displayData(String data) {
        if (data != null) {

            Log.d(TAG, "data = " + data);
            int status = str2Int(data);
            Log.d(TAG, "status = " + status);
            if(status == 11){
//              soundPool.play(1,1, 1, 0, -1, 1);
		if (false == isEnd) {
		    isEnd = true;
		    infusionStatus.setText(R.string.infusion_status_end);
		    timer.stop();
		    startAlarm();
		    showToastDialog(this);
		}
            } else if(status == 10){
        	if(isEnd){
        	    isEnd = false;
                    stopAlarm();
        	}
            } else if(status < 10){
        	
        	if(currentStatus != status){
        	    battery.setImageLevel(status);
//		    int level = 0;
//		    switch (status) {
//		    case 0:
//			level = 0;
//			break;
//		    case 1:
//			level = 10;
//			break;
//		    case 2:
//			level = 20;
//			break;
//		    case 3:
//			level = 40;
//			break;
//		    case 4:
//			level = 60;
//			break;
//		    case 5:
//			level = 80;
//			break;
//		    case 6:
//			level = 100;
//			break;
//		    default:
//			break;
//		    }
//		    battery.setText(level + "%");
        	}
        	currentStatus = status;
            }
        }
    }
    
    public static int str2Int(String str){
	int i = 0;
        byte[] bytes = str.getBytes();
        for (int j = 0; ; j++)
        {
          if (j >= bytes.length)
          {
            break;
          }
          i = i * 256 + (0xFF & bytes[(-1 + bytes.length - j)]);
        }
        return i;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void onClick(View v) {
	// TODO Auto-generated method stub
	timer.stop();
	stopAlarm();
	finish();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
	// TODO Auto-generated method stub
	switch (buttonView.getId()) {
	case R.id.ring:
	    mPreferences.edit().putBoolean(KEY_RING, isChecked).apply();
	    isRing = isChecked;
	    break;

	case R.id.vibrate:
	    mPreferences.edit().putBoolean(KEY_VIBRATE, isChecked).apply();
	    isVibrate = isChecked;
	    break;

	default:
	    break;
	}
    }
}
