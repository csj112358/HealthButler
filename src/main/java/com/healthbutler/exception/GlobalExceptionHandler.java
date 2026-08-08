package com.healthbutler.exception;

import com.healthbutler.vo.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        logger.error("业务异常：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidationException(MethodArgumentNotValidException e) {
        logger.error("参数校验异常：{}", e.getMessage());
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(400, "参数校验失败", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        logger.error("约束违反异常：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(400, e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result<Void>> handleBadCredentialsException(BadCredentialsException e) {
        logger.error("认证异常：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.fail(401, "用户名或密码错误"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(AccessDeniedException e) {
        logger.error("授权异常：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.fail(403, "没有访问权限"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logger.error("文件上传大小超限：{}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.fail(400, "文件大小超过限制"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        logger.error("系统异常：{}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.fail(500, "系统内部错误"));
    }
}
