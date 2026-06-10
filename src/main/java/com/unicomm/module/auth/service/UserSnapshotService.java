package com.unicomm.module.auth.service;

import com.unicomm.module.auth.entity.UserSnapshotEntity;
import com.unicomm.module.auth.integration.EmployeeInfo;
import com.unicomm.module.auth.mapper.UserSnapshotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserSnapshotService {

    private static final String SOURCE_SYSTEM_MOCK = "mock";

    private final UserSnapshotMapper userSnapshotMapper;

    /**
     * 保存或更新当前认证用户的人员快照。
     *
     * <p>这是第一处迁移到 MyBatis-Plus 的低风险写入逻辑。复杂 Memo 查询暂时继续使用
     * JdbcTemplate，等权限和分页有更多自动化测试后再拆 Repository。</p>
     */
    public void saveOrUpdate(EmployeeInfo employee) {
        UserSnapshotEntity entity = new UserSnapshotEntity();
        entity.setUsername(employee.username());
        entity.setEmployeeNo(employee.employeeNo());
        entity.setDisplayName(employee.displayName());
        entity.setDepartmentName(employee.departmentName());
        entity.setEmail(employee.email());
        entity.setSourceSystem(SOURCE_SYSTEM_MOCK);
        entity.setStatusSnapshot(employee.status());
        userSnapshotMapper.upsertSnapshot(entity);
    }
}
