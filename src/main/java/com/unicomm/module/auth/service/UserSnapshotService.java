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
     * <p>认证与 Memo 数据访问已统一进入 MyBatis-Plus Mapper 边界。
     * 这里保留独立服务，是为了后续接入真实人员 API 时集中处理快照同步策略。</p>
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
