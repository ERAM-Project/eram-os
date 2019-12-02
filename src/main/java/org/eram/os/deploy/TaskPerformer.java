package org.eram.os.deploy;

import android.content.Context;
import android.util.Log;

import org.eram.common.Messages;
import org.eram.os.communication.stream.ERAMInputStream;
import org.eram.os.deploy.executor.LinkEstimator;
import org.eram.os.deploy.executor.OSProtocol;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

import dalvik.system.DexClassLoader;

public class TaskPerformer implements Runnable{

    private static AtomicInteger id = new AtomicInteger();
    private String TAG;
    private final Socket clientSocket;
    private final Context context;

    private ERAMInputStream input;
    private ObjectOutputStream output;

    private LinkEstimator linkRTT;
    private OSProtocol protocol;

    // Classloaders needed by the dynamicObjectInputStream
    private ClassLoader currentLoader = ClassLoader.getSystemClassLoader();
    private DexClassLoader dexClassLoader;


    public TaskPerformer(Socket client, final Context clientContext, OSProtocol protocol, LinkEstimator linkRTT) {

        TAG = TaskPerformer.class.getName() + "-" + id.getAndIncrement();
        Log.d(TAG, "New Client connected");
        this.clientSocket = client;
        this.context = clientContext;
        this.protocol = protocol;
        this.linkRTT = linkRTT;

    }

    @Override
    public void run() {
        Log.e(TAG," HHH -HHH ");
        try {
            this.output = new ObjectOutputStream(clientSocket.getOutputStream());
            this.input  = new ERAMInputStream(clientSocket.getInputStream());

            this.input.setClassLoaders(currentLoader, dexClassLoader);

            int request = 0;
            while (request != -1) {
                request = input.read();
                Log.d(TAG, "Get Request - " + request);

                switch (request) {
                    case Messages.I_OFFLOAD_A_TASK:
                        Log.d(TAG," Here 1");
                        protocol.execute(input, output);
                        break;

                    case Messages.PING:
                        this.linkRTT.ping(input, output);
                        break;
                    case Messages.DO_YOU_NEED_SOURCE_CODE:
                        this.protocol.recieveCodeSource(input, output);
                        Log.d(TAG," Here 2");
                        break;
                }
            }
        }catch (IOException e){
            Log.e(TAG, e.getMessage()+" closed.");
        }finally {
            this.protocol.finishConnection(input, output, clientSocket);
        }
    }
}
