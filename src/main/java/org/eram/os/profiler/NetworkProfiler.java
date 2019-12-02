package org.eram.os.profiler;

import android.util.Log;

import org.eram.common.settings.ConnectionSettings;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Listen for phone connections for measuring the data rate. The phone will send/receive some data
 * for 3 seconds.
 */
public class NetworkProfiler implements Runnable {

    private static final String TAG = "NetworkProfiler1";

    private ServerSocket serverSocket;

    public NetworkProfiler() {

    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(ConnectionSettings.REMOTE_PROFILER_PORT);
            while (true) {
                Log.i(TAG, "Listening for client connections ...");
                Socket clientSocket = serverSocket.accept();
                new Thread(new NetworkClientProfiler(clientSocket)).start();
            }
        } catch (IOException e) {
            Log.e(TAG,
                    "Could not start server on port " + ConnectionSettings.REMOTE_PROFILER_PORT + " (" + e + ")");
        }
    }

}