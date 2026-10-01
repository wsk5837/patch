package com.gazellio.platform.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException error) {
        HttpStatus status=HttpStatus.valueOf(error.getStatusCode().value());
        Map<String, Object> body=new LinkedHashMap<>();
        body.put("timestamp",Instant.now().toString());
        body.put("status",status.value());
        body.put("error",status.getReasonPhrase());
        body.put("message",error.getReason()==null?status.getReasonPhrase():error.getReason());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> dataIntegrity(DataIntegrityViolationException error) {
        log.error("Database constraint rejected an API operation", error);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Data constraint conflict");
        body.put("message", "数据库结构或数据约束冲突，请部署最新版本并重新执行此操作");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
