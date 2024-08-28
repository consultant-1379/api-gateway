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

import com.ericsson.gateway.security.authorization.client.ProtectedResource;
import com.ericsson.gateway.security.authorization.client.ProtectionAPI;
import com.ericsson.gateway.security.authorization.client.TokenAPI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive time-to-live cache of all protected resources configured in the Authorization server.
 * The cache is refreshed at an interval defined by the property
 * keycloak.authorization.resources.refresh-interval.
 */
@Component
public class ProtectedResourcesTtlCache {

  private final Flux<ProtectedResource> protectedResources;

  public ProtectedResourcesTtlCache(
      final ReactiveClientRegistrationRepository clientRegistrationRepo,
      final TokenAPI tokenAPI,
      final ProtectionAPI protectionAPI,
      @Value("${keycloak.authorization.resources.refresh-interval}") final long refreshInterval) {

    this.protectedResources =
        Flux.defer(
                () ->
                    clientRegistrationRepo
                        .findByRegistrationId("keycloak")
                        .flatMap(
                            clientRegistration ->
                                tokenAPI
                                    .getProtectionApiToken(clientRegistration)
                                    .zipWhen(
                                        tokenResponse ->
                                            protectionAPI
                                                .getAllResourceIds(tokenResponse.getAccessToken())
                                                .flatMapMany(Flux::fromIterable)
                                                .flatMap(
                                                    id ->
                                                        protectionAPI.getResource(
                                                            tokenResponse.getAccessToken(), id))
                                                .collectList())
                                    .flatMap(
                                        tuple ->
                                            tokenAPI
                                                .logoutToken(
                                                    clientRegistration,
                                                    tuple.getT1().getRefreshToken().getTokenValue())
                                                .onErrorResume(throwable -> Mono.empty())
                                                .thenReturn(tuple.getT2())))
                        .flatMapMany(Flux::fromIterable))
            .cache(Duration.ofMillis(refreshInterval));
  }

  /**
   * Get reactive protected resources cache.
   *
   * @return ProtectedResource Flux
   */
  public Flux<ProtectedResource> readAllProtectedResources() {
    return this.protectedResources;
  }
}
