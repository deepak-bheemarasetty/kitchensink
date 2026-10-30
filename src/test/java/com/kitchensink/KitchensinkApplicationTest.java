package com.kitchensink;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class KitchensinkApplicationTest {
    @Test
    void mainStartsApplicationContext() {
        assertThatCode(() -> KitchensinkApplication.main(
                new String[] {"--spring.main.web-application-type=none"}))
                .doesNotThrowAnyException();
    }
}
