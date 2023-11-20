package com.emegonza.apigateway.service;

import com.emegonza.apigateway.model.IntrospectUserResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface IAuthorizationByIntrospection {

    Mono<IntrospectUserResponse> isValidTokenByIntrospection(ServerWebExchange exchange);
}
