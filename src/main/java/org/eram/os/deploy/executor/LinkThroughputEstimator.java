package org.eram.os.deploy.executor;

import android.util.Log;

import org.eram.common.Messages;
import org.eram.os.communication.stream.ERAMInputStream;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class LinkThroughputEstimator implements LinkEstimator {

    private static final String TAG = "Throughput-Estimator";


    @Override
    public void ping(ERAMInputStream input, ObjectOutputStream output) {

        try {
            Log.d(TAG, "Reply to PING from client.");
            output.write(Messages.PONG);
            output.flush();
        }catch (IOException e){
            Log.e(TAG,e.toString());
        }
    }
}
