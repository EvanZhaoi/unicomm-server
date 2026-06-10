package com.unicomm.module.auth.integration;

import java.util.List;
import java.util.Optional;

/**
 * 企业人员信息适配器.
 *
 * <p>认证、成员搜索和后续权限扩展都通过该接口读取人员信息，避免业务代码直接依赖
 * mock 数据、HTTP 人员接口或 LDAP/AD 等具体实现。</p>
 */
public interface PersonnelProvider {

    /**
     * 根据 Windows 域和用户名查询员工.
     *
     * <p>测试阶段从 mock 数据查询；生产阶段应映射到真实人员接口。返回 inactive 员工时，
     * 调用方需要继续判断状态并拒绝登录。</p>
     *
     * @param domain Windows 域，可为空
     * @param username Windows 用户名
     * @return 员工信息；不存在时返回 empty
     */
    Optional<EmployeeInfo> findByWindowsAccount(String domain, String username);

    /**
     * 按姓名、用户名、工号或邮箱模糊搜索可用员工.
     *
     * @param keyword 搜索关键词，可为空
     * @param limit 最大返回数量
     * @return 可被添加为相关人的员工列表
     */
    List<EmployeeInfo> searchMembers(String keyword, int limit);
}
