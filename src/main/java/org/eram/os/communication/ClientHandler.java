package org.eram.os.communication;

import android.content.Context;

import org.eram.os.deploy.executor.LinkEstimator;
import org.eram.os.deploy.executor.OSProtocol;

import java.util.concurrent.ExecutorService;

public abstract class ClientHandler implements Runnable {

    protected final String TAG = "ClientHandler";
    protected Context context;
    protected ExecutorService threadPool;
    protected OSProtocol protocol;
    protected LinkEstimator linkEstimator;

    public ClientHandler(Context context, ExecutorService threadlPool, OSProtocol protocol, LinkEstimator linkEstimator)
    {
        this.context = context;
        this.threadPool = threadlPool;
        this.protocol = protocol;

        this.linkEstimator = linkEstimator;
    }

}
