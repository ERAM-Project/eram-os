package org.eram.os.deploy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Listens to android.intent.action.BOOT_COMPLETED (defined in AndroidManifest.xml) and starts the
 * execution server when the system has finished booting
 */
public class ServiceDeployStarter extends BroadcastReceiver {
    private static final String TAG = ServiceDeployStarter.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceiveIntent: Start Execution Service");

        Intent serviceIntent = new Intent(context, ServiceHandler.class);
        context.startService(serviceIntent);
    }
}
