package com.emegonza.apigateway.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class BussinessException extends Exception {
        private static final long serialVersionUID = 1L;
        private final String message;
}
