package com.rinoimob.audit.listener;

import com.rinoimob.audit.annotation.Auditable;
import org.springframework.aop.AfterReturningAdvice;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@Component
public class AuditListener implements MethodBeforeAdvice, AfterReturningAdvice {

    private static final Logger logger = Logger.getLogger(AuditListener.class.getName());

    @Override
    public void before(Method method, Object[] args, Object target) throws Throwable {
        Auditable auditable = method.getAnnotation(Auditable.class);
        if (auditable != null) {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("method", method.getName());
            auditData.put("action", auditable.action());
            auditData.put("resource", auditable.resource());
            auditData.put("timestamp", System.currentTimeMillis());
            auditData.put("args", args);

            logger.fine("Audit Before: " + auditData);
        }
    }

    @Override
    public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
        Auditable auditable = method.getAnnotation(Auditable.class);
        if (auditable != null) {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("method", method.getName());
            auditData.put("action", auditable.action());
            auditData.put("resource", auditable.resource());
            auditData.put("timestamp", System.currentTimeMillis());
            auditData.put("result", returnValue);

            logger.fine("Audit After: " + auditData);
        }
    }
}
