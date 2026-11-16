package com.example.document.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "Content Too Large", "Dung lượng file tối đa là 10 MB.");
    }

    // Bắt lỗi NoSuchElementException (Bao gồm lỗi "STUDENT_NOT_FOUND" từ
    // ProfileClient)
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return error(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    // BỔ SUNG: Bắt các lỗi trạng thái tích hợp (TIMEOUT, UNAVAILABLE) từ
    // ProfileClient
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        String message = ex.getMessage();
        if (message != null) {
            if (message.startsWith("TIMEOUT:")) {
                // Trả về 504 Gateway Timeout khi dịch vụ kia phản hồi chậm
                return error(HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout", message);
            } else if (message.contains("UNAVAILABLE")) {
                // Trả về 503 Service Unavailable khi dịch vụ kia bị sập
                return error(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", message);
            }
        }
        return error(HttpStatus.BAD_REQUEST, "Bad Request", message);
    }

    // BỔ SUNG: Bắt các lỗi Runtime liên quan đến tích hợp hoặc giả lập lỗi
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();
        if (message != null) {
            if (message.startsWith("INTEGRATION_ERROR:")) {
                // Trả về 502 Bad Gateway khi dịch vụ kia trả về lỗi hệ thống
                return error(HttpStatus.BAD_GATEWAY, "Bad Gateway", message);
            } else if (message.startsWith("ROLLBACK_DEMO:")) {
                // Phục vụ riêng cho mục đích giả lập kiểm thử giao dịch
                return error(HttpStatus.INTERNAL_SERVER_ERROR, "Rollback Demo", message);
            }
        }
        // Fallback: Không lộ stack trace cho client nếu là lỗi Runtime bình thường
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Dịch vụ tạm thời gián đoạn. Vui lòng thử lại sau.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        // Không trả stack trace, đường dẫn máy chủ hoặc thông tin nội bộ cho client.
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "Dịch vụ tạm thời gián đoạn. Vui lòng thử lại sau.");
    }

    // ĐÃ SỬA LỖI Ở ĐÂY: Bổ sung >
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String name, String message) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", status.value());
        body.put("error", name);
        body.put("message", message == null ? "Yêu cầu không hợp lệ." : message);

        return ResponseEntity.status(status).body(body);
    }
}