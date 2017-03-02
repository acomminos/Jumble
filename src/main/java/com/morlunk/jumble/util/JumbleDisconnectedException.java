package com.morlunk.jumble.util;

/**
 * Called when a
 * Created by andrew on 01/03/17.
 */

public class JumbleDisconnectedException extends RuntimeException {
    public JumbleDisconnectedException() {
        super("Caller attempted to use the protocol while disconnected.");
    }

    public JumbleDisconnectedException(String reason) {
        super(reason);
    }
}
