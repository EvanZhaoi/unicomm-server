package com.unicomm.module.auth.service;

import com.unicomm.module.auth.dto.DesktopVerifyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void record(String username, String action, String result, DesktopVerifyRequest request, String message) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("action", action)
                .addValue("result", result)
                .addValue("deviceId", request == null ? null : request.getDeviceId())
                .addValue("computerName", request == null ? null : request.getComputerName())
                .addValue("message", message);

        jdbcTemplate.update("""
                INSERT INTO uni_auth_audit
                    (username, action, result, device_id, computer_name, message, create_time)
                VALUES
                    (:username, :action, :result, :deviceId, :computerName, :message, NOW())
                """, params);
    }
}
