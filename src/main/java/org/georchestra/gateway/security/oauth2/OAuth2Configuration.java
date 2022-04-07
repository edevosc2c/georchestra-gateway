/*
 * Copyright (C) 2021 by the geOrchestra PSC
 *
 * This file is part of geOrchestra.
 *
 * geOrchestra is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * geOrchestra is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * geOrchestra.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.georchestra.gateway.security.oauth2;

import org.georchestra.gateway.security.ServerHttpSecurityCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity.OAuth2LoginSpec;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.ReactiveOAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.userinfo.DefaultReactiveOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.ReactiveOAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
@EnableConfigurationProperties(OAuth2ProxyConfigProperties.class)
@Slf4j(topic = "org.georchestra.gateway.security.oauth2")
public class OAuth2Configuration {

    private final class OAuth2AuthenticationCustomizer implements ServerHttpSecurityCustomizer {

        public @Override void customize(ServerHttpSecurity http) {
            log.info("Enabling authentication support using an OAuth 2.0 and/or OpenID Connect 1.0 Provider");
            http.oauth2Login();
        }
    }

    @Bean
    ServerHttpSecurityCustomizer oau2EnablingCustomizer() {
        return new OAuth2AuthenticationCustomizer();
    }

    /**
     * Configures the OAuth2 client to use the HTTP proxy if enabled, by means of
     * {@linkplain #oauth2WebClient}
     * <p>
     * {@link OAuth2LoginSpec ServerHttpSecurity$OAuth2LoginSpec#createDefault()}
     * will return a {@link ReactiveAuthenticationManager} by first looking up a
     * {@link ReactiveOAuth2AccessTokenResponseClient
     * ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest>}
     * in the application context, and creating a default one if none is found.
     * <p>
     * We provide such bean here to have it configured with an {@link WebClient HTTP
     * client} that will use the {@link OAuth2ProxyConfigProperties configured} HTTP
     * proxy.
     */
    @Bean
    public ReactiveOAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> reactiveOAuth2AccessTokenResponseClient(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {

        WebClientReactiveAuthorizationCodeTokenResponseClient client = new WebClientReactiveAuthorizationCodeTokenResponseClient();
        client.setWebClient(oauth2WebClient);
        return client;
    }

    @Bean
    public ReactiveOAuth2UserService<OAuth2UserRequest, OAuth2User> reactiveOAuth2UserService(
            @Qualifier("oauth2WebClient") WebClient oauth2WebClient) {

        DefaultReactiveOAuth2UserService service = new DefaultReactiveOAuth2UserService();
        service.setWebClient(oauth2WebClient);
        return service;
    };

    /**
     * {@link WebClient} to use when performing HTTP POST requests to the OAuth2
     * service providers, that can be configured to use an HTTP proxy through the
     * {@link OAuth2ProxyConfigProperties} configuration properties.
     *
     * @param proxyConfig defines the HTTP proxy settings specific for the OAuth2
     *                    client. If not
     *                    {@link OAuth2ProxyConfigProperties#isEnabled() enabled},
     *                    the {@code WebClient} will use the proxy configured
     *                    through System properties ({@literal http(s).proxyHost}
     *                    and {@literal http(s).proxyPort}), if any.
     */
    @Bean("oauth2WebClient")
    public WebClient oauth2WebClient(OAuth2ProxyConfigProperties proxyConfig) {
        final String proxyHost = proxyConfig.getHost();
        final Integer proxyPort = proxyConfig.getPort();
        final String proxyUser = proxyConfig.getUsername();
        final String proxyPassword = proxyConfig.getPassword();

        HttpClient httpClient = HttpClient.create();
        if (proxyConfig.isEnabled()) {
            if (proxyHost == null || proxyPort == null) {
                throw new IllegalStateException("OAuth2 client HTTP proxy is enabled, but host and port not provided");
            }
            log.info("Oauth2 client will use HTTP proxy {}:{}", proxyHost, proxyPort);
            httpClient = httpClient.proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP).host(proxyHost).port(proxyPort)
                    .username(proxyUser).password(user -> {
                        return proxyPassword;
                    }));
        } else {
            log.info("Oauth2 client will use HTTP proxy from System properties if provided");
            httpClient = httpClient.proxyWithSystemProperties();
        }
        ReactorClientHttpConnector conn = new ReactorClientHttpConnector(httpClient);

        WebClient webClient = WebClient.builder().clientConnector(conn).build();
        return webClient;
    }

}