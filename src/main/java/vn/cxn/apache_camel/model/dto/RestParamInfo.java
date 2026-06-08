package vn.cxn.apache_camel.model.dto;

public record RestParamInfo(
        String name,
        String type, // "query", "header", "body", etc.
        String dataType, // "string", "integer", etc.
        boolean required,
        String description) {}
