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
public class IntrospectUserResponse implements Serializable {

    private static final long serialVersionUID = 1L;
    private String email;
    private String documentNumber;
    //private String kid;
    private String scope;
    private Boolean active;
    private String exp;

}
