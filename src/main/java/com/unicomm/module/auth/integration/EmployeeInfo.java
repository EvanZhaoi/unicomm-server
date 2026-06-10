package com.unicomm.module.auth.integration;

/**
 * 员工身份信息.
 *
 * <p>该对象是 UniComm 内部统一的人员信息模型。测试阶段由 mock 数据构造，
 * 生产阶段由 HR、OA、LDAP、AD 或统一身份平台返回的数据映射得到。</p>
 *
 * <p>注意：该对象不是用户主数据，只代表认证或成员搜索时拿到的一次人员快照。</p>
 */
public record EmployeeInfo(
        String username,
        String employeeNo,
        String displayName,
        String departmentName,
        String email,
        String status) {

    /**
     * 判断员工是否处于可登录、可被搜索和可被添加为相关人的状态.
     */
    public boolean active() {
        return "active".equalsIgnoreCase(status);
    }
}
