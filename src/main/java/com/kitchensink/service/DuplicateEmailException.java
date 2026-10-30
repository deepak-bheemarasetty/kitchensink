package com.kitchensink.service;

/**
 * Indicates that registration used an email address already assigned to a member.
 *
 * @author Deepak Bheemarasetty
 */
public class DuplicateEmailException extends RuntimeException {
  public DuplicateEmailException(String message) {
    super(message);
  }
}
