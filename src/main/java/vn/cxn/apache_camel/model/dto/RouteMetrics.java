package vn.cxn.apache_camel.model.dto;

/**
 * Per-route runtime metrics sourced from Camel's management layer (ManagedRouteMBean). All time
 * values are in milliseconds.
 */
public record RouteMetrics(
        String routeId,
        long exchangesTotal,
        long exchangesSucceeded,
        long exchangesFailed,
        long exchangesInflight,
        long meanProcessingTimeMs,
        long minProcessingTimeMs,
        long maxProcessingTimeMs,
        long lastProcessingTimeMs,
        String throughput,
        String load01,
        String load05,
        String load15) {}
