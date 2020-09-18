package com.wurmonline.womconverter;

public class ConversionFailedException extends Exception {
    public ConversionFailedException(String message) {
        super(message);
    }

    public ConversionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
