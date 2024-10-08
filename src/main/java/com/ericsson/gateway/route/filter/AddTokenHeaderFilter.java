/*
 * Copyright Ericsson 2019
 *
 * The copyright to the computer program(s) herein is the property of
 * Ericsson Inc. The programs may be used and/or copied only with written
 * permission from Ericsson Inc. or in accordance with the terms and
 * conditions stipulated in the agreement/contract under which the
 * program(s) have been supplied.
 */
package com.ericsson.gateway.route.filter;

import com.ericsson.gateway.security.authorization.client.TokenAPI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

/**
 * Add Authorization bearer token header to the request. Refreshes the access token according to the
 * configured token refresh strategy (NEVER, ALWAYS, EXPIRING). For expiring refresh strategy, the
 * access token is refreshed if it is expired or expiring within 5 seconds.
 */
@Component
public class AddTokenHeaderFilter
    extends AbstractGatewayFilterFactory<AddTokenHeaderFilter.Config> {

  private static final String AUTHORIZATION_REFRESH_TOKEN = "Authorization-Refresh";

  @Autowired private ReactiveOAuth2AuthorizedClientService clientService;

  @Autowired private TokenAPI tokenAPI;

  public AddTokenHeaderFilter() {
    super(Config.class);
  }

  @Override
  public GatewayFilter apply(final Config config) {
    return (exchange, chain) ->
        getOrRefreshAccessToken(exchange.getSession(), config)
            .flatMap(
                oauth2AuthorizedClient -> {
                  final ServerHttpRequest request =
                      exchange
                          .getRequest()
                          .mutate()
                          .headers(
                              httpHeaders -> {
                                httpHeaders.setBearerAuth(
                                    oauth2AuthorizedClient.getAccessToken().getTokenValue());
                                if (config.isIncludeRefreshToken()
                                    && Objects.nonNull(oauth2AuthorizedClient.getRefreshToken())) {
                                  httpHeaders.add(
                                      AUTHORIZATION_REFRESH_TOKEN,
                                      oauth2AuthorizedClient.getRefreshToken().getTokenValue());
                                }
                              })
                          .build();
                  return chain.filter(exchange.mutate().request(request).build());
                });
  }

  private Mono<OAuth2AuthorizedClient> getOrRefreshAccessToken(
      final Mono<WebSession> session, final Config config) {
    return session
        .map(webSession -> webSession.getAttribute("SPRING_SECURITY_CONTEXT"))
        .cast(SecurityContext.class)
        .map(SecurityContext::getAuthentication)
        .cast(OAuth2AuthenticationToken.class)
        .flatMap(
            authentication ->
                clientService
                    .loadAuthorizedClient(
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getName())
                    .flatMap(
                        oAuth2AuthorizedClient ->
                            shouldRefreshAccessToken(config, oAuth2AuthorizedClient)
                                ? refreshAccessToken(oAuth2AuthorizedClient, authentication)
                                : Mono.just(oAuth2AuthorizedClient)));
  }

  private boolean shouldRefreshAccessToken(
      final Config config, final OAuth2AuthorizedClient authorizedClient) {
    final Config.REFRESH_STRATEGY refreshStrategy = config.getRefreshStrategy();
    boolean refreshToken = false;
    if (Config.REFRESH_STRATEGY.ALWAYS.equals(refreshStrategy)) {
      refreshToken = true;
    } else if (Config.REFRESH_STRATEGY.EXPIRING.equals(refreshStrategy)) {
      final Instant expiresAt = authorizedClient.getAccessToken().getExpiresAt();
      refreshToken = Instant.now().isAfter(expiresAt.minus(Duration.ofSeconds(5)));
    }
    return refreshToken;
  }

  private Mono<OAuth2AuthorizedClient> refreshAccessToken(
      final OAuth2AuthorizedClient oAuth2AuthorizedClient, final Authentication authentication) {
    return tokenAPI
        .refreshToken(oAuth2AuthorizedClient)
        .map(
            oAuth2AccessTokenResponse ->
                new OAuth2AuthorizedClient(
                    oAuth2AuthorizedClient.getClientRegistration(),
                    oAuth2AuthorizedClient.getPrincipalName(),
                    oAuth2AccessTokenResponse.getAccessToken(),
                    oAuth2AccessTokenResponse.getRefreshToken()))
        .flatMap(
            newOAuth2AuthorizedClient ->
                clientService
                    .saveAuthorizedClient(newOAuth2AuthorizedClient, authentication)
                    .thenReturn(newOAuth2AuthorizedClient));
  }

  public static class Config {

    private enum REFRESH_STRATEGY {
      NEVER,
      ALWAYS,
      EXPIRING
    }

    private REFRESH_STRATEGY refreshStrategy = REFRESH_STRATEGY.NEVER;
    private boolean includeRefreshToken = false;

    public REFRESH_STRATEGY getRefreshStrategy() {
      return refreshStrategy;
    }

    public void setRefreshStrategy(String refreshStrategy) {
      this.refreshStrategy = REFRESH_STRATEGY.valueOf(refreshStrategy.toUpperCase());
    }

    public boolean isIncludeRefreshToken() {
      return includeRefreshToken;
    }

    public void setIncludeRefreshToken(boolean includeRefreshToken) {
      this.includeRefreshToken = includeRefreshToken;
    }
  }
}
