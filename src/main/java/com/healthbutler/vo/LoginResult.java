package com.healthbutler.vo;

import lombok.Data;

@Data
public class LoginResult {

    private Long userId;

    private String username;

    private String token;
}