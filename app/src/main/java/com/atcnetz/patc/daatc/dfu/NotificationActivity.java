package com.atcnetz.patc.daatc.dfu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.atcnetz.patc.daatc.DFUActivity;

public class NotificationActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        if (isTaskRoot()) {
            final Intent intent = new Intent(this, DFUActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtras(getIntent().getExtras()); // copy all extras
            startActivity(intent);
        }
		finish();
	}
}