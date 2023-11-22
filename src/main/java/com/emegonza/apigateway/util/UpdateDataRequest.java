package com.emegonza.apigateway.util;

import com.emegonza.apigateway.filter.AuthenticationFilter;
import com.emegonza.apigateway.model.IntrospectUserResponse;
import com.emegonza.apigateway.model.TransactionData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class UpdateDataRequest {
    private static final JsonMapper jsonMapper = new JsonMapper();

    public ServerWebExchange updateRequest(AuthenticationFilter.Config config,
                                            ServerWebExchange exchange,
                                            ServerHttpRequest request,
                                            IntrospectUserResponse introspectResponse) {

        AtomicReference<ServerHttpRequest.Builder> requestUpdated = new AtomicReference<>(request.mutate());
        updateDataRequest(config, exchange, request, introspectResponse)
                .subscribe(serverHttpRequest -> {
                    log.info("Request actualizado: {}", serverHttpRequest);
                    requestUpdated.set(serverHttpRequest.mutate());
                });

        return exchange.mutate().request(requestUpdated.get().build()).build();
    }

    private Flux<ServerHttpRequest> updateDataRequest(AuthenticationFilter.Config config,
                                                      ServerWebExchange exchange,
                                                      ServerHttpRequest request,
                                                      IntrospectUserResponse introspectResponse) {
        return request.getBody()
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    try {
                        return jsonMapper.readTree(bytes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .map(jsonNode -> {
                    setTransactionData(config, exchange, jsonNode);
                    ObjectNode modifiedBody = (ObjectNode) jsonNode;
                    modifiedBody.put("add", "successful");
                    modifiedBody.put("ip", config.getTransactionData().getClientIp());
                    log.info("Body actualizado: {}", modifiedBody);
                    return modifiedBody;
                })
                .flatMap(modifiedBody -> {
                    HttpHeaders httpHeaders = getHttpHeaders(exchange);
                    updateHeadersRequest(httpHeaders, introspectResponse, config);
                    ServerHttpRequest mutatedRequest = createUpdatedRequest(request, exchange, modifiedBody, httpHeaders);
                    return Mono.just(mutatedRequest.mutate().build());
                });
    }

    private void updateHeadersRequest(HttpHeaders httpHeaders,
                                      IntrospectUserResponse introspectResponse,
                                      AuthenticationFilter.Config config) {
        httpHeaders.add("email", introspectResponse.getEmail());
        httpHeaders.add("document-number", introspectResponse.getDocumentNumber());
        httpHeaders.add("id", config.getTransactionData().getTransactionId());
    }

    public void setTransactionData(AuthenticationFilter.Config config,
                                   ServerWebExchange exchange,
                                   JsonNode jsonNode) {
        config.setTransactionData(
                TransactionData
                        .builder()
                        .servicePath(exchange.getRequest().getPath().pathWithinApplication().toString())
                        .transactionId(getTransactionIdFromBody(jsonNode))
                        .clientIp(getClientIp(exchange.getRequest()))
                        .build()
        );
    }

    private String getClientIp(ServerHttpRequest request) {

        String xForwardedFor = request.getHeaders().getFirst(Constants.HTTP_REQUEST_HEADER);
        String clientIp = "";

        // Si X-Forwarded-For está presente, usa la primera dirección de la lista (que debería ser la IP del cliente)
        if (StringUtils.hasText(xForwardedFor)) {
            String[] ips = xForwardedFor.split(",");
            clientIp = ips[0].trim();
            log.info("IP del cliente desde X-Forwarded-For: {}", clientIp);
        } else {
            // Si X-Forwarded-For no está presente, obtén la IP directamente del objeto ServerWebExchange
            InetSocketAddress remoteAddress = request.getRemoteAddress();
            clientIp = (remoteAddress != null) ? remoteAddress.getAddress().getHostAddress() : "Desconocida";
            log.info("IP del cliente directamente: {}", clientIp);
        }

        return clientIp;
    }

    public static String getTransactionIdFromBody(JsonNode jsonNode) {
        String transactionId = "";
        if (jsonNode.findParent(Constants.PATH_HEADER) != null) {
            transactionId = jsonNode.findPath(Constants.PATH_TRANSACTION_ID).asText();
        } else if (jsonNode.findParents(Constants.PATH_DATA).size() == 1) {
            transactionId = jsonNode.findParents(Constants.PATH_DATA).get(0)
                    .findPath(Constants.PATH_TRANSACTION_ID).asText();
        }
        return transactionId;
    }

    private ServerHttpRequest createUpdatedRequest(ServerHttpRequest request,
                                                   ServerWebExchange exchange,
                                                   JsonNode updatedBody,
                                                   HttpHeaders updatedHttpHeaders) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                // Aquí puedes retornar el cuerpo actualizado
                return Flux.just(dataBuffer(updatedBody.toString()));
            }

            @Override
            public HttpHeaders getHeaders() {
                Long contentLength = updatedHttpHeaders.getContentLength();
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.putAll(updatedHttpHeaders);
                if (contentLength > 0) {
                    httpHeaders.setContentLength(contentLength);
                } else {
                    httpHeaders.set(HttpHeaders.TRANSFER_ENCODING, Constants.HTTP_CHUNKED);
                }
                return httpHeaders;
            }

            private DataBuffer dataBuffer(String value) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                return exchange.getResponse().bufferFactory().wrap(bytes);
            }
        };
    }

    public HttpHeaders getHttpHeaders(ServerWebExchange exchange) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(exchange.getRequest().getHeaders());
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        return headers;
    }
}
