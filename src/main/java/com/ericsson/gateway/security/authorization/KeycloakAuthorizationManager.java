/*
 * Copyright Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */
package com.ericsson.gateway.security.authorization;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * A reactive authorization manager which delegates to the Authorization server (Keycloak) to
 * perform evaluation of all permissions and authorization policies associated with the resources
 * being requested.
 *
 * <p>Resources which are not managed by the Authorization server will be ignored and as such access
 * will be granted to those resources.
 */
@Component
public class KeycloakAuthorizationManager
    implements ReactiveAuthorizationManager<AuthorizationContext> {

  @Autowired private PolicyEnforcer policyEnforcer;

  @Override
  public Mono<AuthorizationDecision> check(
      final Mono<Authentication> authentication, final AuthorizationContext object) {
    return authentication
        .filter(Authentication::isAuthenticated)
        .flatMap(auth -> policyEnforcer.enforce(auth, object.getExchange()))
        .defaultIfEmpty(new AuthorizationDecision(false));
  }
}
