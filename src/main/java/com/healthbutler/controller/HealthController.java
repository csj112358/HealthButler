package com.healthbutler.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @GetMapping("/health")
    public Map<String, Object> checkHealth() {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> components = new HashMap<>();

        // Check MySQL
        String mysqlStatus = "down";
        try (Connection conn = dataSource.getConnection()) {
            mysqlStatus = conn.isValid(3) ? "up" : "down";
        } catch (Exception e) {
            logger.warn("MySQL health check failed: {}", e.getMessage());
        }

        // Check Redis
        String redisStatus = "down";
        RedisConnection redisConn = null;
        try {
            redisConn = redisConnectionFactory.getConnection();
            redisConn.ping();
            redisStatus = "up";
        } catch (Exception e) {
            logger.warn("Redis health check failed: {}", e.getMessage());
        } finally {
            if (redisConn != null) {
                try {
                    redisConn.close();
                } catch (Exception ignored) {
                }
            }
        }

        components.put("mysql", mysqlStatus);
        components.put("redis", redisStatus);
        components.put("timestamp", System.currentTimeMillis());

        result.put("code", 200);
        result.put("message", "服务状态");
        result.put("data", components);

        return result;
    }
}
