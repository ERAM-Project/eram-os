package org.eram.os.deploy.executor;

import org.eram.os.communication.stream.ERAMInputStream;

import java.io.ObjectOutputStream;
import java.net.Socket;

public interface OSProtocol {

    void recieveCodeSource(ERAMInputStream input, ObjectOutputStream output);
    void execute(ERAMInputStream input, ObjectOutputStream output);
    void finishConnection(ERAMInputStream input, ObjectOutputStream output, Socket client);

}
