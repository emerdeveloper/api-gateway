package com.emegonza.apigateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class ErrorData implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<Error> errors;
}
