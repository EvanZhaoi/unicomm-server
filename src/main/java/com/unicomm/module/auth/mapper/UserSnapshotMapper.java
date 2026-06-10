package com.unicomm.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.unicomm.module.auth.entity.UserSnapshotEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户快照 Mapper。
 *
 * <p>这里继承 MyBatis-Plus BaseMapper，后续查询和简单 CRUD 可以直接复用；
 * 当前认证流程需要 MySQL upsert，所以保留一条明确的自定义 SQL。</p>
 */
@Mapper
public interface UserSnapshotMapper extends BaseMapper<UserSnapshotEntity> {

    @Insert("""
            INSERT INTO uni_user_snapshot
                (username, employee_no, display_name, department_name, email, source_system,
                 status_snapshot, last_sync_time, create_time, update_time)
            VALUES
                (#{username}, #{employeeNo}, #{displayName}, #{departmentName}, #{email}, #{sourceSystem},
                 #{statusSnapshot}, NOW(), NOW(), NOW())
            ON DUPLICATE KEY UPDATE
                employee_no = VALUES(employee_no),
                display_name = VALUES(display_name),
                department_name = VALUES(department_name),
                email = VALUES(email),
                source_system = VALUES(source_system),
                status_snapshot = VALUES(status_snapshot),
                last_sync_time = NOW(),
                update_time = NOW()
            """)
    void upsertSnapshot(UserSnapshotEntity entity);
}
