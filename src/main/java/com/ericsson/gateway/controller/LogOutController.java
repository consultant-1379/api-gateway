/*
 * Copyright Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */
package com.ericsson.gateway.controller;

import com.ericsson.gateway.security.authorization.client.TokenAPI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/")
public class LogOutController {

  private static final String KEYCLOAK_AUTH_ENDPOINT = "/oauth2/authorization/keycloak";

  @Autowired private TokenAPI tokenAPI;

  @Autowired private ServerOAuth2AuthorizedClientRepository oAuth2AuthorizedClientRepository;

  private WebSessionServerSecurityContextRepository webSessionServerSecurityContextRepository =
      new WebSessionServerSecurityContextRepository();

  private Logger logger = LoggerFactory.getLogger(LogOutController.class);

  @RequestMapping(
      path = {"logout", "/auth/v1/logout"},
      method = {RequestMethod.GET, RequestMethod.POST})
  public Mono<ResponseEntity> logout(ServerWebExchange exchange) {
    return webSessionServerSecurityContextRepository
        .load(exchange)
        .map(SecurityContext::getAuthentication)
        .cast(OAuth2AuthenticationToken.class)
        .flatMap(auth -> loadAuthorizedClient(auth, exchange))
        .flatMap(this::logoutKeycloakSession)
        .then(exchange.getSession().doOnNext(WebSession::invalidate))
        .thenReturn(
            ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", getRequestReferer(exchange).orElse(KEYCLOAK_AUTH_ENDPOINT))
                .header("Set-Cookie", "JSESSIONID=null")
                .build());
  }

  private Mono<OAuth2AuthorizedClient> loadAuthorizedClient(
      final OAuth2AuthenticationToken auth, final ServerWebExchange exchange) {
    return oAuth2AuthorizedClientRepository.loadAuthorizedClient(
        auth.getAuthorizedClientRegistrationId(), auth, exchange);
  }

  private Mono<Void> logoutKeycloakSession(OAuth2AuthorizedClient authorizedClient) {
    return tokenAPI
        .logoutToken(
            authorizedClient.getClientRegistration(),
            authorizedClient.getRefreshToken().getTokenValue())
        .doOnError(e -> logger.error("Logout failed", e));
  }

  private Optional<String> getRequestReferer(final ServerWebExchange exchange) {
    return Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("Referer"));
  }
}
