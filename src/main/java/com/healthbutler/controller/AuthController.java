package com.healthbutler.controller;

import com.healthbutler.dto.LoginRequest;
import com.healthbutler.dto.RegisterRequest;
import com.healthbutler.service.AuthService;
import com.healthbutler.vo.LoginResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = authService.register(request);
            result.put("code", 200);
            result.put("message", "注册成功");
            result.put("data", userId);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "注册失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            LoginResult loginResult = authService.login(request);
            result.put("code", 200);
            result.put("message", "登录成功");
            result.put("data", loginResult);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "登录失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Map<String, Object> refreshToken(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> result = new HashMap<>();
        try {
            String token = authorization.replace("Bearer ", "");
            String newToken = authService.refreshToken(token);
            result.put("code", 200);
            result.put("message", "刷新成功");
            result.put("data", newToken);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "刷新失败：" + e.getMessage());
        }
        return result;
    }
}