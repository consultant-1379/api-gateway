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

import static org.junit.jupiter.api.Assertions.*;

import com.ericsson.gateway.config.WireMockIntegrationTestConfig;
import com.ericsson.gateway.util.StubbedAuthorizationServerRequests;
import java.time.Instant;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.AuthenticatedPrincipalServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

@ExtendWith(SpringExtension.class)
@TestPropertySource(locations = "classpath:application-test.properties")
@ContextConfiguration(
    classes = {
      AuthorizationTest.AuthorizationTestConfiguration.class,
      WebClientAutoConfiguration.class,
      WireMockIntegrationTestConfig.class
    })
class AuthorizationTest {

  @Autowired private KeycloakAuthorizationManager keycloakAuthManager;

  @Autowired private ReactiveOAuth2AuthorizedClientService oAuth2AuthorizedClientService;

  @Autowired private ReactiveClientRegistrationRepository clientRegistrationRepository;

  @Autowired private StubbedAuthorizationServerRequests stubbedRequests;

  private static final String PRINCIPAL_NAME = "user1";

  @AfterEach
  void afterEach() {
    stubbedRequests.resetMappingsAndRequests();
  }

  @Test
  void shouldGrantAccessWhenRequestedResourceNotManagedByAuthServer() {
    final OAuth2AuthenticationToken authenticatedUserToken = createAuthenticatedUserToken();
    createAuthorizedClient(authenticatedUserToken);

    stubbedRequests.stubGetProtectionApiToken().stubGetProtectedResources().stubTokenLogout();

    final AuthorizationContext authContext =
        new AuthorizationContext(
            MockServerWebExchange.from(MockServerHttpRequest.get("/unmanaged_resource")));

    final AuthorizationDecision authDecision =
        keycloakAuthManager.check(Mono.just(authenticatedUserToken), authContext).block();
    assertNotNull(authDecision);
    assertTrue(authDecision.isGranted());
  }

  @Test
  void shouldGrantAccessWhenAuthServerReturnsOK() {
    final OAuth2AuthenticationToken authenticatedUserToken = createAuthenticatedUserToken();
    createAuthorizedClient(authenticatedUserToken);

    stubbedRequests
        .stubGetProtectionApiToken()
        .stubGetProtectedResources()
        .stubAuthorizationSuccessWithGetScope()
        .stubTokenLogout();

    final AuthorizationContext authContext =
        new AuthorizationContext(
            MockServerWebExchange.from(MockServerHttpRequest.get("/resource1")));

    final AuthorizationDecision authDecision =
        keycloakAuthManager.check(Mono.just(authenticatedUserToken), authContext).block();
    assertNotNull(authDecision);
    assertTrue(authDecision.isGranted());
  }

  @Test
  void shouldDenyAccessWhenAuthServerReturnsError() {
    final OAuth2AuthenticationToken authenticatedUserToken = createAuthenticatedUserToken();
    createAuthorizedClient(authenticatedUserToken);

    stubbedRequests
        .stubGetProtectionApiToken()
        .stubGetProtectedResources()
        .stubAuthorizationDenied()
        .stubTokenLogout();

    final AuthorizationContext authContext =
        new AuthorizationContext(
            MockServerWebExchange.from(MockServerHttpRequest.get("/resource1")));

    final AuthorizationDecision authDecision =
        keycloakAuthManager.check(Mono.just(authenticatedUserToken), authContext).block();
    assertNotNull(authDecision);
    assertFalse(authDecision.isGranted());
  }

  @Test
  void shouldDenyAccessWhenMultipleResourcesMatchingRequestedURI() {
    final OAuth2AuthenticationToken authenticatedUserToken = createAuthenticatedUserToken();
    createAuthorizedClient(authenticatedUserToken);

    stubbedRequests
        .stubGetProtectionApiToken()
        .stubGetDuplicateProtectedResources()
        .stubTokenLogout();

    final AuthorizationContext authContext =
        new AuthorizationContext(
            MockServerWebExchange.from(MockServerHttpRequest.get("/resource1")));

    final AuthorizationDecision authDecision =
        keycloakAuthManager.check(Mono.just(authenticatedUserToken), authContext).block();
    assertNotNull(authDecision);
    assertFalse(authDecision.isGranted());
  }

  @Test
  void shouldExcludeScopeFromAuthRequestWhenNoResourceScopeMatchingHttpRequestMethod() {
    final OAuth2AuthenticationToken authenticatedUserToken = createAuthenticatedUserToken();
    createAuthorizedClient(authenticatedUserToken);

    stubbedRequests
        .stubGetProtectionApiToken()
        .stubGetProtectedResources()
        .stubAuthorizationSuccessWithoutScope()
        .stubTokenLogout();

    final AuthorizationContext authContext =
        new AuthorizationContext(
            MockServerWebExchange.from(MockServerHttpRequest.post("/resource1")));

    final AuthorizationDecision authDecision =
        keycloakAuthManager.check(Mono.just(authenticatedUserToken), authContext).block();
    assertNotNull(authDecision);
    assertTrue(authDecision.isGranted());
  }

  private OAuth2AuthenticationToken createAuthenticatedUserToken() {
    final OAuth2User user =
        new DefaultOAuth2User(
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")),
            Collections.singletonMap("sub", PRINCIPAL_NAME),
            "sub");
    return new OAuth2AuthenticationToken(
        user, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")), "keycloak");
  }

  private void createAuthorizedClient(final OAuth2AuthenticationToken authenticatedUserToken) {
    final OAuth2AccessToken token =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "abc123",
            Instant.now(),
            Instant.now().plusSeconds(60));
    final ClientRegistration clientRegistration =
        clientRegistrationRepository.findByRegistrationId("keycloak").block();
    if (clientRegistration != null) {
      final OAuth2AuthorizedClient oAuth2AuthorizedClient =
          new OAuth2AuthorizedClient(clientRegistration, PRINCIPAL_NAME, token);
      oAuth2AuthorizedClientService
          .saveAuthorizedClient(oAuth2AuthorizedClient, authenticatedUserToken)
          .block();
    }
  }

  @TestConfiguration
  @EnableConfigurationProperties(OAuth2ClientProperties.class)
  @ComponentScan(basePackages = "com.ericsson.gateway.security.authorization")
  public static class AuthorizationTestConfiguration {

    @Bean
    public ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService(
        ReactiveClientRegistrationRepository clientRegistrationRepository) {
      return new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    public ServerOAuth2AuthorizedClientRepository authorizedClientRepository(
        ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService) {
      return new AuthenticatedPrincipalServerOAuth2AuthorizedClientRepository(
          reactiveOAuth2AuthorizedClientService);
    }
  }
}
