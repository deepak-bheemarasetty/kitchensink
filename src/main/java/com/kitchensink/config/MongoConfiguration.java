package com.kitchensink.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Configures MongoDB mapping, transactions, and authenticated actor auditing. */
@Configuration
@EnableMongoAuditing
public class MongoConfiguration {
  @Bean
  MappingMongoConverter mappingMongoConverter(
      MongoDatabaseFactory databaseFactory,
      MongoMappingContext mappingContext,
      MongoCustomConversions customConversions) {
    MappingMongoConverter converter =
        new MappingMongoConverter(new DefaultDbRefResolver(databaseFactory), mappingContext);
    converter.setCustomConversions(customConversions);
    // The strict collection schemas reject fields that are not part of each model.
    converter.setTypeMapper(new DefaultMongoTypeMapper(null));
    return converter;
  }

  @Bean
  MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
    return new MongoTransactionManager(databaseFactory);
  }

  @Bean
  AuditorAware<String> auditorAware() {
    return () ->
        Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
            .filter(Authentication::isAuthenticated)
            .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
            .map(Authentication::getName);
  }
}
