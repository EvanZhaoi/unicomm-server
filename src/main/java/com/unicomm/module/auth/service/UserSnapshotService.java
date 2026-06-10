package com.unicomm.module.auth.service;

import com.unicomm.module.auth.integration.EmployeeInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSnapshotService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void saveOrUpdate(EmployeeInfo employee) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", employee.username())
                .addValue("employeeNo", employee.employeeNo())
                .addValue("displayName", employee.displayName())
                .addValue("departmentName", employee.departmentName())
                .addValue("email", employee.email())
                .addValue("status", employee.status());

        jdbcTemplate.update("""
                INSERT INTO uni_user_snapshot
                    (username, employee_no, display_name, department_name, email, source_system,
                     status_snapshot, last_sync_time, create_time, update_time)
                VALUES
                    (:username, :employeeNo, :displayName, :departmentName, :email, 'mock',
                     :status, NOW(), NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    employee_no = VALUES(employee_no),
                    display_name = VALUES(display_name),
                    department_name = VALUES(department_name),
                    email = VALUES(email),
                    status_snapshot = VALUES(status_snapshot),
                    last_sync_time = NOW(),
                    update_time = NOW()
                """, params);
    }
}
