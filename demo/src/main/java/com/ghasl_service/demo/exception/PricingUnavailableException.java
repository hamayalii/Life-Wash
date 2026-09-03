package com.ghasl_service.demo.exception;

public class PricingUnavailableException extends RuntimeException {
    public PricingUnavailableException(String message) {
        super(message);
    }
}
