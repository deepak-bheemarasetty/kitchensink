package com.kitchensink.config;

import com.kitchensink.service.RolePermissionService;
import com.kitchensink.model.UserAccount;
import java.util.Locale;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

/** Loads active application accounts and their role-derived permissions from MongoDB. */
@Service
public class MongoUserDetailsService implements UserDetailsService {
  private final MongoTemplate mongoTemplate;
  private final RolePermissionService rolePermissionService;

  public MongoUserDetailsService(MongoTemplate mongoTemplate, RolePermissionService rolePermissionService) {
    this.mongoTemplate = mongoTemplate;
    this.rolePermissionService = rolePermissionService;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    String normalized = username.trim().toLowerCase(Locale.ROOT);
    UserAccount account = mongoTemplate.findOne(
        Query.query(Criteria.where("usernameLower").is(normalized)), UserAccount.class);
    if (account == null || !account.isActive()) {
      throw new UsernameNotFoundException("User not found");
    }
    account.validate();
    return User.withUsername(account.getUsername())
        .password(account.getPasswordHash())
        .authorities(rolePermissionService.permissionsFor(account.getRoles()).stream()
            .map(SimpleGrantedAuthority::new).toList())
        .build();
  }
}
