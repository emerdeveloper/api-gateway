package com.emegonza.apigateway.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class TransactionData implements Serializable {
    private static final long serialVersionUID = 1L;
    private String transactionId;
    private String clientIp;
    private String servicePath;
    private JsonNode bodyRequest;
}