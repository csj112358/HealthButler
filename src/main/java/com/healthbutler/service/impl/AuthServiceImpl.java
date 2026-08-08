package com.healthbutler.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.healthbutler.dto.LoginRequest;
import com.healthbutler.dto.RegisterRequest;
import com.healthbutler.entity.User;
import com.healthbutler.mapper.UserMapper;
import com.healthbutler.exception.BusinessException;
import com.healthbutler.service.AuthService;
import com.healthbutler.util.JwtUtil;
import com.healthbutler.vo.LoginResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Long register(RegisterRequest request) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (existingUser != null) {
            throw new BusinessException(400, "用户名已存在");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPhone(request.getPhone());
        user.setGender(request.getGender());
        user.setBirthDate(request.getBirthDate());
        user.setAvatarUrl(request.getAvatarUrl());

        // 这里简化处理，实际应该保存加密后的密码
        // user.setPassword(passwordEncoder.encode(request.getPassword()));

        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public LoginResult login(LoginRequest request) {
        // 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }

        // 验证密码（简化处理，实际应该验证加密密码）
        // if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        //     throw new RuntimeException("密码错误");
        // }

        // 生成Token
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 构建返回结果
        LoginResult result = new LoginResult();
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setToken(token);

        return result;
    }

    @Override
    public String refreshToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new BusinessException(401, "Token无效或已过期");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);

        return jwtUtil.generateToken(userId, username);
    }
}