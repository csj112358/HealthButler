package com.healthbutler.controller;

import com.healthbutler.dto.LoginRequest;
import com.healthbutler.dto.RegisterRequest;
import com.healthbutler.service.AuthService;
import com.healthbutler.vo.LoginResult;
import com.healthbutler.vo.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = authService.register(request);
        return Result.ok("注册成功", userId);
    }

    @PostMapping("/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        LoginResult loginResult = authService.login(request);
        return Result.ok("登录成功", loginResult);
    }

    @PostMapping("/refresh")
    public Result<String> refreshToken(@RequestHeader("Authorization") String authorization) {
        String token = authorization.replace("Bearer ", "");
        String newToken = authService.refreshToken(token);
        return Result.ok("刷新成功", newToken);
    }
}
