package com.kfree.bluetooth.le;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class Messagedialog extends Activity{  
    protected void onCreate(Bundle savedInstanceState) {    
        super.onCreate(savedInstanceState);   
        final Window win = getWindow();    
        win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED    
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD    
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON    
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);    
//        setContentView(R.layout.dilog_xml); // 调用接口来实现显示的数据
        
        DialogInterface dialog = new AlertDialog.Builder(this).setTitle(
                getResources().getString(R.string.error_title))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage("" + getResources().getString(R.string.infusion_status_end))
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

        	    @Override
        	    public void onClick(DialogInterface dialog, int which) {
//        		stopAlarm();
//        		mBluetoothLeService.disconnect();
//        		dialog.dismiss();
//        		mToastDialog = null;
        		finish();
        	    }
        	}).show();
    }
}