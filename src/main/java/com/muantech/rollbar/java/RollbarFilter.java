package com.muantech.rollbar.java;

import org.apache.logging.log4j.ThreadContext;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class RollbarFilter implements Filter {

    @Override
    public void init(FilterConfig config) throws ServletException {}

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        try {
            if(servletRequest instanceof HttpServletRequest)
            {
                HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
                ThreadContext.put("url", httpRequest.getRequestURI());
                ThreadContext.put("method", httpRequest.getMethod());
                ThreadContext.put("query", httpRequest.getQueryString());
                ThreadContext.put("user-ip", httpRequest.getRemoteAddr());
                ThreadContext.put("sessionId", httpRequest.getSession().getId());
                ThreadContext.put("requestId", httpRequest.getHeader("X-Request-Id"));
                
                // Headers
                Map<String, String> headers = new HashMap<String, String>();
                
                Enumeration<String> headerNames = httpRequest.getHeaderNames();
                while (headerNames.hasMoreElements()) {
                    String headerName = headerNames.nextElement();
                    headers.put(headerName, httpRequest.getHeader(headerName));
                }
                
                if (headers != null) {
                    JSONObject headersData = new JSONObject();
                    for (Entry<String, String> entry : headers.entrySet()) {
                        headersData.put(entry.getKey(), entry.getValue());
                    }
                    if (headersData.length() > 0) ThreadContext.put("headers", headersData.toString());
                }
                
                // Request Params
                Map<String, String[]> params = httpRequest.getParameterMap();
                
                if (params != null) {
                    JSONObject paramsData = new JSONObject();
                    for (Entry<String, String[]> entry : params.entrySet()) {
                        for(String entryValue : entry.getValue())
                        {
                            paramsData.put(entry.getKey(), entryValue);    
                        }
                    }
                    if (paramsData.length() > 0) {
                        ThreadContext.put("params", paramsData.toString());
                    }
                }
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            RollbarAppender.setCurrentRequest(null);
        }

    }

    @Override
    public void destroy() {}

}
