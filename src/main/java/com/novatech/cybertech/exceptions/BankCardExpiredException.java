package com.novatech.cybertech.exceptions;

public class BankCardExpiredException extends RuntimeException {
  public BankCardExpiredException(String message) {
    super(message);
  }
}
