package com.kitchensink.service;

import java.util.Locale;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves normalized user identities and ObjectId references across authentication types. */
@Component
public class IdentityResolver {
  private final MongoTemplate mongoTemplate;

  public IdentityResolver(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  /**
   * Resolves the canonical identity string (email or subject fallback) for the authenticated actor.
   *
   * @param authentication current authentication token
   * @return normalized identity string
   */
  public String resolveIdentity(Authentication authentication) {
    if (authentication == null
        || !authentication.isAuthenticated()
        || authentication.getName() == null) {
      throw new AccessDeniedException("Authentication required");
    }
    if (authentication instanceof OAuth2AuthenticationToken oauth) {
      String email = oauth.getPrincipal().getAttribute("email");
      if (email != null && !email.isBlank()) {
        return email.trim().toLowerCase(Locale.ROOT);
      }
    } else if (authentication instanceof JwtAuthenticationToken jwt) {
      String email = jwt.getToken().getClaimAsString("email");
      if (email != null && !email.isBlank()) {
        return email.trim().toLowerCase(Locale.ROOT);
      }
    }
    return authentication.getName().trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Resolves the local user account document in MongoDB for the authenticated actor.
   *
   * @param authentication current authentication token
   * @return user account document, or null if not found
   */
  public Document resolveUser(Authentication authentication) {
    String usernameLower = resolveIdentity(authentication);
    return mongoTemplate.findOne(
        Query.query(Criteria.where("usernameLower").is(usernameLower)), Document.class, "users");
  }

  /**
   * Resolves the ObjectId of the local user account in MongoDB, failing if not registered.
   *
   * @param authentication current authentication token
   * @return local user ObjectId
   */
  public ObjectId requireActorId(Authentication authentication) {
    Document user = resolveUser(authentication);
    if (user == null) {
      throw new AccessDeniedException("Authenticated user is not registered in the application");
    }
    return user.getObjectId("_id");
  }
}
