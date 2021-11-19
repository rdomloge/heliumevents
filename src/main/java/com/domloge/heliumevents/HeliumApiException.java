package com.domloge.heliumevents;

public class HeliumApiException extends Exception {

    public HeliumApiException(String message) {
        super(message);
    }

    public HeliumApiException(Throwable cause) {
        super(cause);
    }

    public HeliumApiException(String message, Throwable cause) {
        super(message, cause);
    }

    
}
