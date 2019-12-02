package org.eram.os;

import androidx.appcompat.app.AppCompatActivity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import org.eram.os.deploy.ServiceHandler;

public class EramOS extends AppCompatActivity {

    private static final String TAG = EramOS.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eram_os);

        Log.d(TAG, "ERAM Server created!");

        ComponentName comp = new ComponentName(getPackageName(), ServiceHandler.class.getName());
        startService(new Intent().setComponent(comp));
    }
}
