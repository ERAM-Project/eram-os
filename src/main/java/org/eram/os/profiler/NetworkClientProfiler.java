package org.eram.os.profiler;

import android.util.Log;

import org.eram.common.Utils;
import org.eram.common.Messages;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;

public class NetworkClientProfiler implements Runnable {

    private static final String TAG ="Network Profiler";
    private Socket clientSocket;
    private InputStream is = null;
    OutputStream os = null;
    private DataOutputStream dos = null;

    private long totalBytesRead;
    private long totalTimeBytesRead;

    private static final int BUFFER_SIZE = 10 * 1024;
    private byte[] buffer;

    public NetworkClientProfiler(Socket clientSocket) {
        Log.i(TAG, "New client connected for network test");
        this.clientSocket = clientSocket;
        buffer = new byte[BUFFER_SIZE];
    }

    @Override
    public void run() {
        int request = 0;

        try {
            is = clientSocket.getInputStream();
            os = clientSocket.getOutputStream();
            dos = new DataOutputStream(os);

            while (request != -1) {
                request = is.read();

                switch (request) {

                    case Messages.PING:
                        os.write(Messages.PONG);
                        break;

                    case Messages.UPLOAD_FILE:
                        new Thread(new Runnable() {

                            @Override
                            public void run() {
                                long t0 = System.nanoTime();
                                long elapsed = 0;
                                while (elapsed < 3000) {
                                    try {
                                        Thread.sleep(3000 - elapsed);
                                    } catch (InterruptedException e1) {
                                    } finally {
                                        elapsed = (System.nanoTime() - t0) / 1000000;
                                    }
                                }
                                Utils.close(is);
                                Utils.close(dos);
                                Utils.close(clientSocket);
                            }

                        }).start();

                        totalTimeBytesRead = System.nanoTime();
                        totalBytesRead = 0;
                        while (true) {
                            totalBytesRead += is.read(buffer);
                        }

                    case Messages.UPLOAD_FILE_RESULT:
                        dos.writeLong(totalBytesRead);
                        dos.writeLong(totalTimeBytesRead);
                        dos.flush();
                        break;

                    case Messages.DOWNLOAD_FILE:
                        new Random().nextBytes(buffer);
                        // Used for measuring the dlRate on the phone
                        while (true) {
                            os.write(buffer);
                            is.read();
                        }
                }
            }

        } catch (IOException e) {
            Log.i(TAG, "IOException because 3 seconds have passed (this is the expected behavior)");
        } finally {
            Log.i(TAG, "Client finished bandwidth measurement: " + request);

            if (request == Messages.UPLOAD_FILE) {
                totalTimeBytesRead = System.nanoTime() - totalTimeBytesRead;
            }

            Utils.close(is);
            Utils.close(dos);
            Utils.close(clientSocket);
        }
    }
}

