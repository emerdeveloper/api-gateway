package com.emegonza.apigateway.service;

import com.emegonza.apigateway.util.BussinessException;
import com.emegonza.apigateway.model.IntrospectUserResponse;
import com.emegonza.apigateway.util.Constants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthorizationByIntrospection implements IAuthorizationByIntrospection {

    @Value("${paths.auth.basePath}")
    private String baseUrl;
    @Value("${paths.auth.introspect}")
    private String pathIntrospect;


    private WebClient webClient;

    @PostConstruct
    public void init(){
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public Mono<IntrospectUserResponse> isValidTokenByIntrospection(ServerWebExchange exchange) {
        return getOauth2Introspect(exchange);
    }

    private Mono<IntrospectUserResponse> getOauth2Introspect(ServerWebExchange exchange) {
        try {
            return webClient
                    .post()
                    .uri(pathIntrospect)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    /*.header(Constants.HTTP_AUTHORIZATION,
                            headerHelper.getHeaderAuthorization(
                                    exchange.getRequest().getHeaders().getFirst("scope")
                            ))*/
                    .body(BodyInserters
                            .fromFormData(Constants.BODY_TOKEN, getToken(exchange.getRequest())
                            ))
                    .exchange()
                    .flatMap(clientResponse -> {
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.bodyToMono(IntrospectUserResponse.class)
                                    .flatMap(this::getTokenStatusValidation);
                        } else {
                            return clientResponse.bodyToMono(String.class)
                                    .flatMap(this::processIntrospectUserError);
                        }
                    }).onErrorResume(Mono::error);
        } catch (Exception exception) {
            log.error("Error procesando el llamado a instrospect: ", exception);
            return Mono.error(new BussinessException(Constants.MSG_ERROR_GENERIC));
        }
    }

    private Mono<IntrospectUserResponse> getTokenStatusValidation(IntrospectUserResponse response) {
        if (response.getActive()) {
            return Mono.just(response);
        }

        log.error("El token no está activo");
        return Mono.error(new BussinessException(Constants.MSG_ERROR_TOKEN_INVALID));
    }

    private Mono<IntrospectUserResponse> processIntrospectUserError(String responseError) {
        log.error("Error en la respuesta del servicio de introspect: {}", responseError);
        return Mono.error(new BussinessException(Constants.MSG_ERROR_GENERIC));
    }

    private String getToken(ServerHttpRequest request) {
        String authorization = getAuthHeader(request);
        return Arrays.stream(authorization.split(Constants.ENCODE_BEARER))
                .map(String::trim)
                .collect(Collectors.joining());
    }

    private String getAuthHeader(ServerHttpRequest request) {
        return request.getHeaders().getOrEmpty(Constants.HTTP_AUTHORIZATION).get(0);
    }
}
