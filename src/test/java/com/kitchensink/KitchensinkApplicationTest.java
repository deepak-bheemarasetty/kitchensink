package com.kitchensink;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * Tests startup of the Kitchen Sink application.
 *
 * @author Deepak Bheemarasetty
 */
class KitchensinkApplicationTest {
  @Test
  void mainStartsApplicationContext() {
    assertThatCode(
            () ->
                KitchensinkApplication.main(
                    new String[] {
                      "--spring.main.web-application-type=none",
                      "--spring.data.mongodb.auto-index-creation=false"
                    }))
        .doesNotThrowAnyException();
  }
}
