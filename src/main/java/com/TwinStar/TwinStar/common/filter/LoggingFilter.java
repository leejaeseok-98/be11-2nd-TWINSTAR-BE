package com.TwinStar.TwinStar.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class LoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper((HttpServletRequest) request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper((HttpServletResponse) response);

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            logRequest(requestWrapper, duration);
            logResponse(responseWrapper, duration);

            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, long duration) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUri = queryString != null ? uri + "?" + queryString : uri;

        Map<String, String> headers = getHeaders(request);

        log.info("[Request] {} {} | Duration: {}ms | Headers: {}",
                method, fullUri, duration, headers);

        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            String body = new String(content, request.getCharacterEncoding());
            log.debug("[Request Body] {}", body);
        }
    }

    private void logResponse(ContentCachingResponseWrapper response, long duration) {
        int status = response.getStatus();

        log.info("[Response] Status: {} | Duration: {}ms", status, duration);

        byte[] content = response.getContentAsByteArray();
        if (content.length > 0 && log.isDebugEnabled()) {
            String body = new String(content, response.getCharacterEncoding());
            log.debug("[Response Body] {}", body);
        }
    }

    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);

            if (!headerName.equalsIgnoreCase("authorization") &&
                !headerName.equalsIgnoreCase("cookie")) {
                headers.put(headerName, headerValue);
            } else {
                headers.put(headerName, "***");
            }
        }

        return headers;
    }
}
