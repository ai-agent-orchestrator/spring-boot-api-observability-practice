package com.ohgiraffers.handlermethod.support;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String currentTraceId() {
        String traceId = TRACE_ID.get();

        if (traceId == null || traceId.isBlank()) {
            return "unknown";
        }

        return traceId;
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}
