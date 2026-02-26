package com.sea.api_gateway.security;

import org.jspecify.annotations.NonNull;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class UserIdHeaderFilter implements WebFilter {

    public static final String USER_ID_HEADER = "X-User-ID";

    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange,
                                      @NonNull WebFilterChain chain) {

        return exchange.getPrincipal()
                .ofType(JwtAuthenticationToken.class)
                .map(auth -> auth.getToken().getSubject()) // Keycloak sub
                .map(keycloakId -> {
                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(h -> h.remove(USER_ID_HEADER))
                            .header(USER_ID_HEADER, keycloakId)
                            .build();
                    return exchange.mutate().request(mutatedRequest).build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }
}
