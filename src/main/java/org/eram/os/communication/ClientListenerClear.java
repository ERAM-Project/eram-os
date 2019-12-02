package org.eram.os.communication;

import android.content.Context;
import android.util.Log;

import org.eram.common.settings.ConnectionSettings;
import org.eram.os.deploy.TaskPerformer;
import org.eram.os.deploy.executor.LinkEstimator;
import org.eram.os.deploy.executor.OSProtocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

/**
 * The thread that listens for new clients (phones or other clones) to connect in clear.
 */
public class ClientListenerClear extends ClientHandler{

    public ClientListenerClear(Context context, ExecutorService threadlPool, OSProtocol protocol, LinkEstimator linkEstimator) {
        super(context, threadlPool, protocol, linkEstimator);
    }

    @Override
    public void run() {

        ServerSocket serverSocket = null;
        try {

            serverSocket = new ServerSocket(ConnectionSettings.REMOTE_ERAM_PORT);
            Log.i(TAG, "ClientListenerClear started on port " + ConnectionSettings.REMOTE_ERAM_PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                Log.i(TAG, "New client connected in clear");

                threadPool.execute(new TaskPerformer(clientSocket, context, this.protocol, linkEstimator));
            }
        } catch (IOException e) {
            Log.e(TAG, "IOException: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                    Log.i(TAG, "Close the Socket connection." );
                } catch (IOException e) {
                    Log.e(TAG, "Error while closing server socket: " + e);
                }
            }
        }
    }
}