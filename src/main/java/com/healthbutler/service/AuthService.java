package com.healthbutler.service;

import com.healthbutler.dto.LoginRequest;
import com.healthbutler.dto.RegisterRequest;
import com.healthbutler.vo.LoginResult;

public interface AuthService {

    /**
     * 用户注册
     * @param request 注册请求
     * @return 用户ID
     */
    Long register(RegisterRequest request);

    /**
     * 用户登录
     * @param request 登录请求
     * @return 登录结果（包含Token）
     */
    LoginResult login(LoginRequest request);

    /**
     * 刷新Token
     * @param token 旧Token
     * @return 新Token
     */
    String refreshToken(String token);
}