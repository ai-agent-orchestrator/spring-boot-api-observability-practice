package com.ohgiraffers.handlermethod.interceptor;

import com.ohgiraffers.handlermethod.support.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class TraceIdInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TraceIdInterceptor.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String START_TIME_ATTRIBUTE = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = resolveTraceId(request);
        long startTime = System.currentTimeMillis();

        TraceContext.setTraceId(traceId);
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);
        response.setHeader(TRACE_ID_HEADER, traceId);

        log.info("request start traceId={} method={} uri={} thread={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                Thread.currentThread());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long elapsedMs = elapsedMs(request);
        String traceId = TraceContext.currentTraceId();

        log.info("request end traceId={} method={} uri={} status={} elapsedMs={} thread={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                elapsedMs,
                Thread.currentThread());

        TraceContext.clear();
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        return traceId;
    }

    private long elapsedMs(HttpServletRequest request) {
        Object startTime = request.getAttribute(START_TIME_ATTRIBUTE);

        if (startTime instanceof Long value) {
            return System.currentTimeMillis() - value;
        }

        return -1;
    }
}
