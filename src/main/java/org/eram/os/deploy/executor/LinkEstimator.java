package org.eram.os.deploy.executor;

import org.eram.os.communication.stream.ERAMInputStream;

import java.io.ObjectOutputStream;

public interface LinkEstimator {

    void ping(ERAMInputStream input, ObjectOutputStream output);
}
