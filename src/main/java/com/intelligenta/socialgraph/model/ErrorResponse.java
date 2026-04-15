package com.intelligenta.socialgraph.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response DTO.
 */
public class ErrorResponse {

    private String error;
    
    @JsonProperty("error_description")
    private String errorDescription;

    public ErrorResponse() {}

    public ErrorResponse(String error, String errorDescription) {
        this.error = error;
        this.errorDescription = errorDescription;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }
}
