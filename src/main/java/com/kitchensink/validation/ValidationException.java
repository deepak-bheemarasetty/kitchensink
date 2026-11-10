package com.kitchensink.validation;

/**
 * A field-specific validation failure used for consistent API error responses.
 *
 * @author Deepak Bheemarasetty
 */
public class ValidationException extends RuntimeException {
  private final String field;

  public ValidationException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
