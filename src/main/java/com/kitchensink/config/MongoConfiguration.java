package com.kitchensink.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Provides authenticated actor metadata for MongoDB auditing. */
@Configuration
@EnableMongoAuditing
public class MongoConfiguration {
  @Bean
  AuditorAware<String> auditorAware() {
    return () ->
        Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
            .map(Authentication::getName);
  }
}
