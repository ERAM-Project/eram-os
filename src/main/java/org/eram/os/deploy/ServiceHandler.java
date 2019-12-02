package org.eram.os.deploy;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import org.eram.common.Utils;
import org.eram.common.settings.ConnectionSettings;
import org.eram.os.communication.ClientListenerClear;
import org.eram.os.deploy.executor.ExecutionProtocol;
import org.eram.os.deploy.executor.LinkEstimator;
import org.eram.os.deploy.executor.LinkThroughputEstimator;
import org.eram.os.deploy.executor.OSProtocol;
import org.eram.os.profiler.NetworkProfiler;

import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Execution server which waits for incoming connections and starts a separate thread for each of
 * them, leaving the AppHandler to actually deal with the clients
 */
public class ServiceHandler extends Service {
    private static final String TAG = "AccelerationServer";

    private Context context;
    private String vmIp;

    private ExecutorService threadPool = Executors.newFixedThreadPool(1000);
    // The OS orchitecture.
    public static String arch = System.getProperty("os.arch");


    /**
     * Called when the service is first created.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (arch == null || !arch.startsWith("x86")) {
            arch = "x86";
        }
        Log.d(TAG, "Server created, running on arch: " + arch);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Server destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Create server socket
        Log.d(TAG, "Start server socket");
        context = this.getApplicationContext();
        if (context == null) {
            Log.e(TAG, "Context is null!!!");
            stopSelf();
        }

        // Connect to the manager to register and get the configuration details
        Thread t = new Thread(new RegistrationManager());
        t.start();

        return START_STICKY;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Read the config file to get the IP and port for the Manager.
     */
    private class RegistrationManager implements Runnable {

        @Override
        public void run() {

            // Before proceeding wait until the network interface is up and correctly configured
            vmIp = waitForNetworkToBeUp();
            Log.i(TAG, "My IP: " + vmIp);

            Log.i(TAG, "Build.hardware=" + Build.HARDWARE);
            if (isEmulator()) {
                Log.i(TAG, "Running on VM on the cloud under VPN, trying to connect to VMM and DS...");

                Log.e(TAG, "######################## FIXME: Starting the listeners for testing anyway ########################");
                startClientListeners();
                Log.e(TAG, "######################## END FIXME ########################");

            } else {
                Log.i(TAG, "Running on a phone for D2D offloading, starting the listeners");
                startClientListeners();


            }
        }

        private String waitForNetworkToBeUp() {

            InetAddress vmIpAddress;
            do {
                vmIpAddress = Utils.getIpAddress();
                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            while (vmIpAddress == null || !Utils.validateIpAddress(vmIpAddress.getHostAddress()));
            Log.i(TAG, "I have an IP: " + vmIpAddress.getHostAddress());

            return vmIpAddress.getHostAddress();
        }

    }



    private void startClientListeners() {


        Log.d(TAG, "Starting NetworkProfilerServer on port " + ConnectionSettings.REMOTE_PROFILER_PORT);
        new Thread(new NetworkProfiler()).start();

        Log.d(TAG, "Starting ClientListenerClear on port " + ConnectionSettings.REMOTE_ERAM_PORT);

        OSProtocol protocol = new ExecutionProtocol(context);
        LinkEstimator linkEstimator = new LinkThroughputEstimator();

        new Thread(new ClientListenerClear(context, this.threadPool, protocol, linkEstimator)).start();

    }

    public static boolean isEmulator() {
        return Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk".equals(Build.PRODUCT)
                || Build.HARDWARE.contains("android_x86");
    }
}
