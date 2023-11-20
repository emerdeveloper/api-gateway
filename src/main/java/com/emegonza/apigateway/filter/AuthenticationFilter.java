package com.emegonza.apigateway.filter;

import com.emegonza.apigateway.model.ErrorData;
import com.emegonza.apigateway.util.BussinessException;
import com.emegonza.apigateway.model.IntrospectUserResponse;
import com.emegonza.apigateway.model.TransactionData;
import com.emegonza.apigateway.service.IAuthorizationByIntrospection;
import com.emegonza.apigateway.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import com.emegonza.apigateway.model.Error;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Slf4j
//@RefreshScope
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final IAuthorizationByIntrospection authorizationByIntrospection;

    public AuthenticationFilter(IAuthorizationByIntrospection authorizationByIntrospection) {
        super(Config.class);
        this.authorizationByIntrospection = authorizationByIntrospection;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
           // log.info("Ejecutando filtro pre");
            ServerHttpRequest request = exchange.getRequest().mutate().build();
            if (isAuthMissing(request)) {
                return this.onError(exchange, Constants.MSG_ERROR_GET_AUTHORIZATION);
            }

            return authorizationByIntrospection.isValidTokenByIntrospection(exchange)
                    .flatMap(introspectUserResponse -> {
                        if (!introspectUserResponse.getActive()) {
                            return this.onError(exchange, Constants.MSG_ERROR_TOKEN_INVALID);
                        }

                        var updatedRequest = updateRequest(exchange, request, introspectUserResponse);
                        return chain.filter(updatedRequest);

                    })
                    .onErrorResume(throwable ->
                    {
                        if (throwable instanceof BussinessException) {
                            return this.onError(exchange, throwable.getMessage());
                        }
                        log.error("Error inesperado: ", throwable);
                        return this.onError(exchange, Constants.MSG_ERROR_GENERIC);
                    });
        };
    }

    private ServerWebExchange updateRequest(ServerWebExchange exchange,
                                            ServerHttpRequest request,
                                            IntrospectUserResponse introspectResponse) {

        request.mutate()
                .header("email", introspectResponse.getEmail())
                .header("document-number", introspectResponse.getDocumentNumber())
                .build();
        return exchange.mutate().request(request).build();
    }

    private boolean isAuthMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey(Constants.HTTP_AUTHORIZATION);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        String error = buildJsonError(exchange.getRequest(), null, message);
        byte[] bytes = error.getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().writeWith(Flux.just(buffer));
    }

    private String buildJsonError(ServerHttpRequest request, AuthenticationFilter.Config config, String message) {
        return new Gson().toJson(
                ErrorData.builder()
                        .errors(Collections.singletonList(
                                Error.builder()
                                        .title(Constants.TITLE)
                                        .source(request.getPath().pathWithinApplication().value())
                                        .message(message)
                                        .build()))
                        .build());
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Config {
        private TransactionData transactionData;
        private JsonNode jsonNodeRequest;
    }
}
