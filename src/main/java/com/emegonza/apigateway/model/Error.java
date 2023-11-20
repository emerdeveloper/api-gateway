package com.emegonza.apigateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Error implements Serializable {
    private static final long serialVersionUID = 1L;
    private String code;
    private String source;
    private String title;
    private String message;
}