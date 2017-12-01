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

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
@SuppressLint("NewApi")
public class DeviceScanActivity extends Activity implements OnClickListener, OnItemClickListener {
	
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    // 5.0+版本
    private BluetoothLeScanner mBluetoothScaner;
    private boolean mScanning;
    private Handler mHandler;
    private ListView listView;
    private Button searchBtn;
    private View searchBar;

    private static final int REQUEST_ENABLE_BT = 1;
    // 10秒后停止查找搜索.
    private static final long SCAN_PERIOD = 10000;
    
    private boolean version_5_plus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();//.setTitle(R.string.title_devices);
        if(actionBar != null){
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
            actionBar.setCustomView(R.layout.custom_titlebar);
        }
            
        setContentView(R.layout.devices);
        
        listView = (ListView) findViewById(R.id.list);
        searchBtn = (Button) findViewById(R.id.bth_search);
        searchBar = findViewById(R.id.search_bar);

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        listView.setAdapter(mLeDeviceListAdapter);
        listView.setOnItemClickListener(this);
        
        searchBtn.setOnClickListener(this);
        
        mHandler = new Handler();

        version_5_plus = false;//android.os.Build.VERSION.SDK_INT >= 22;
//        Toast.makeText(this, "SDK_INT = " + android.os.Build.VERSION.SDK_INT, Toast.LENGTH_LONG).show();
        
        // 检查当前手机是否支持ble 蓝牙,如果不支持退出程序
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // 初始化 Bluetooth adapter, 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        
        if(version_5_plus){
            if(false == mBluetoothAdapter.isEnabled()){
//        	mBluetoothAdapter.enable();
//        	registerReceiver(mBluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        	Toast.makeText(this, R.string.error_bluetooth_not_enabled, Toast.LENGTH_SHORT).show();
                finish();
        	return;
            }
            mBluetoothScaner = mBluetoothAdapter.getBluetoothLeScanner();
//            mBluetoothScaner.stopScan(mScanCallback);
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }

        // 检查设备上是否支持蓝牙
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        scanLeDevice(true);
    }

    private BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver(){

	@Override
	public void onReceive(Context context, Intent intent) {
	    // TODO Auto-generated method stub
	    int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
	            BluetoothAdapter.ERROR);
	    if (state == BluetoothAdapter.STATE_ON){
	            mBluetoothScaner = mBluetoothAdapter.getBluetoothLeScanner();
//	            mBluetoothScaner.stopScan(mScanCallback);
	            scanLeDevice(true);
	            unregisterReceiver(this);
	    }	    
	}
    
    };
    
    @Override
    protected void onResume() {
        super.onResume();

        // 为了确保设备上蓝牙能使用, 如果当前蓝牙设备没启用,弹出对话框向用户要求授予权限来启用
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            scanLeDevice(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mScanning)
            scanLeDevice(false);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mLeDeviceListAdapter.clear();
            mBluetoothAdapter.startDiscovery();
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(mBluetoothAdapter.isDiscovering()){
                	mBluetoothAdapter.cancelDiscovery();
                    }
                    mScanning = false;
                    if(version_5_plus){
//                	mBluetoothScaner.stopScan(mScanCallback);
                    } else {
                	mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    }
                    searchBar.setVisibility(View.GONE);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            if(version_5_plus){
//        	mBluetoothScaner.startScan(mScanCallback);
            } else {
        	mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
            searchBar.setVisibility(View.VISIBLE);
        } else {
            if(mBluetoothAdapter.isDiscovering()){
        	mBluetoothAdapter.cancelDiscovery();
            }
            mScanning = false;
            if(version_5_plus){
//        	mBluetoothScaner.stopScan(mScanCallback);
            } else {
        	mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
            searchBar.setVisibility(View.GONE);
        }
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if(!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText(R.string.unknown_device);
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }
    
//    private ScanCallback mScanCallback = new ScanCallback() {
//	    @Override
//	    public void onScanResult(int callbackType, ScanResult result) {
//	        super.onScanResult(callbackType, result);
//	        // callbackType：确定这个回调是如何触发的
//	        // result：包括4.3版本的蓝牙信息，信号强度rssi，和广播数据scanRecord
//	        BluetoothDevice device = result.getDevice();
//	        mLeDeviceListAdapter.addDevice(device);
//                mLeDeviceListAdapter.notifyDataSetChanged();
//	    }
//	    @Override
//	    public void onBatchScanResults(List<ScanResult> results) {
//	        super.onBatchScanResults(results);
//	        // 批量回调，一般不推荐使用，使用上面那个会更灵活
//	    }
//	    @Override
//	    public void onScanFailed(int errorCode) {
//	        super.onScanFailed(errorCode);
//	        // 扫描失败，并且失败原因
//	    }
//	};
	
    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mLeDeviceListAdapter.addDevice(device);
                    mLeDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }

    @Override
    public void onClick(View v) {
	// TODO Auto-generated method stub
	switch (v.getId()) {
	case R.id.bth_search:
	    //mLeDeviceListAdapter.clear();
	    if(false == mScanning){
		scanLeDevice(true);
	    }
	    break;

	default:
	    break;
	}
    }

   @Override
    public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
	// TODO Auto-generated method stub
        final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        if (device == null) return;
        final Intent intent = new Intent(this, DeviceControlActivity.class);
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        if (mScanning) {
            if(version_5_plus){
//        	mBluetoothScaner.stopScan(mScanCallback);
            } else {
        	mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
            mScanning = false;
        }
        startActivity(intent);	
    }
}