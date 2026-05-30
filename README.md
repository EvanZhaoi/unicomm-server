# UniComm Server

UniComm 统一通讯平台后端服务 (Spring Boot).

## 技术栈

- **Spring Boot 4.0.6** - 核心框架
- **Java 21** - 运行时
- **Maven** - 构建工具
- **MySQL 8+/9.x** - 数据库（`mysql` 模式启用）
- **Redis 8.x** - 缓存/会话存储（后续启用）
- **Sa-Token 1.45.0** - 认证/会话管理 (stateless token 模式)
- **MyBatis Plus 3.5.16** - ORM 框架
- **SpringDoc OpenAPI 3.0.3** - API 文档

## 项目结构

```
src/main/java/com/unicomm/
├── UniCommApplication.java          # 启动类
├── config/                          # 配置类
│   ├── SaTokenConfig.java           # Sa-Token 配置
│   ├── MybatisPlusConfig.java       # MyBatis Plus 配置
│   ├── CorsConfig.java              # 跨域配置
│   └── OpenApiConfig.java           # OpenAPI 文档配置
├── common/                          # 通用模块
│   ├── Result.java                  # 统一响应结构
│   ├── ResultCode.java              # 响应码枚举
│   ├── BusinessException.java       # 业务异常
│   └── GlobalExceptionHandler.java  # 全局异常处理
└── module/
    ├── auth/                        # 认证模块
    │   ├── controller/AuthController.java
    │   ├── service/AuthService.java
    │   ├── service/impl/AuthServiceImpl.java
    │   ├── dto/DesktopVerifyRequest.java
    │   ├── dto/DesktopVerifyResponse.java
    │   └── UserSnapshot.java
    └── memo/                        # 备忘录模块
        ├── controller/MemoController.java
        ├── controller/MemoGroupController.java
        ├── dto/MemoDtos.java
        └── service/
            ├── MemoService.java
            ├── InMemoryMemoService.java
            └── JdbcMemoService.java
```

## 快速开始

### 前置要求

- JDK 21+
- Maven 3.8+

### 编译

```bash
cd ~/Project/unicomm-server
mvn clean compile
```

### 运行 (Phase 1: 内存模式)

```bash
mvn spring-boot:run
```

服务启动后运行在 `http://localhost:28080`

### 运行 (MySQL 持久化模式)

MySQL 安装完成后执行：

```sql
CREATE DATABASE IF NOT EXISTS unicomm
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

本地连接信息建议通过环境变量覆盖，避免把密码写入文档或提交历史：

```bash
export UNICOMM_DB_URL="jdbc:mysql://localhost:3306/unicomm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export UNICOMM_DB_USERNAME="root"
export UNICOMM_DB_PASSWORD="<your-local-password>"
```

启动 MySQL 模式：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

`application-mysql.yml` 会自动执行 `src/main/resources/db/schema-mysql.sql` 建表脚本。

### Memo API Smoke Test

服务启动后可以用下面脚本快速验证认证、分组、创建、更新、列表流程：

```bash
node - <<'NODE'
const base = 'http://localhost:28080/api/v1';
const headers = { 'content-type': 'application/json' };
async function req(path, options = {}) {
  const res = await fetch(base + path, { ...options, headers: { ...headers, ...(options.headers || {}) } });
  const body = await res.json().catch(() => null);
  if (!res.ok || body?.code !== 200) throw new Error(`${path} -> ${res.status} ${JSON.stringify(body)}`);
  return body.data;
}
const auth = await req('/auth/desktop/verify', {
  method: 'POST',
  body: JSON.stringify({ username: 'evanzhao', domain: '', computerName: 'Mac', deviceId: 'dev-device' }),
});
const tokenHeaders = { 'unicomm-token': auth.accessToken, Authorization: `Bearer ${auth.accessToken}` };
const groups = await req('/memo-groups', { headers: tokenHeaders });
const created = await req('/memos', {
  method: 'POST',
  headers: tokenHeaders,
  body: JSON.stringify({ title: 'Smoke Test Memo', content: 'hello', groupId: groups[0].id, status: 'normal' }),
});
const updated = await req(`/memos/${created.id}`, {
  method: 'PUT',
  headers: tokenHeaders,
  body: JSON.stringify({ title: 'Smoke Test Memo Updated', content: 'updated', groupId: groups[0].id, status: 'todo' }),
});
const list = await req('/memos?page=1&size=10&isArchived=false', { headers: tokenHeaders });
console.log({ user: auth.username, groups: groups.length, created: created.id, status: updated.status, list: list.total });
NODE
```

## API 文档

启动服务后访问:

- **Swagger UI**: http://localhost:28080/swagger-ui.html
- **API Docs**: http://localhost:28080/v3/api-docs

## 主要接口

### 桌面端认证

验证 Windows 用户身份，获取访问令牌。

**请求**

```http
POST http://localhost:28080/api/v1/auth/desktop/verify
Content-Type: application/json

{
  "username": "evan.zhao",
  "domain": "COMPANY",
  "computerName": "CN-SH-001",
  "deviceId": "client-device-id",
  "os": "Windows",
  "osVersion": "Windows 11",
  "appVersion": "0.1.0"
}
```

**响应 (成功)**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "username": "evan.zhao",
    "employeeNo": "E10001",
    "displayName": "Evan Zhao",
    "departmentName": "IT Department",
    "permissions": ["memo:read", "memo:write"],
    "accessToken": "xxx-xxx-xxx"
  }
}
```

**响应 (用户未授权)**

```json
{
  "code": 401,
  "message": "当前 Windows 用户未开通 UniComm 权限",
  "data": null
}
```

## 测试用户 (Phase 1 内存数据)

| 员工编号 | 显示名称    | Windows 用户   | 域      | 状态 |
|---------|------------|----------------|---------|------|
| E10001  | Evan Zhao  | evan.zhao      | COMPANY | 启用 |
| E10002  | Alice Wang | alice.wang     | COMPANY | 启用 |
| E10003  | Bob Li     | bob.li         | COMPANY | 禁用 |

## 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {...}
}
```

| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 / 用户未开通权限 |
| 403 | 禁止访问 / 权限不足 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

## 配置说明

### application.yml 关键配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| server.port | 服务端口 | 28080 |
| unicomm.data-mode | 数据模式 (dev=内存, mysql=MySQL 持久化) | dev |
| unicomm.datasource.url | MySQL JDBC 地址，仅 mysql 模式使用 | application-mysql.yml |
| unicomm.datasource.username | MySQL 用户名，仅 mysql 模式使用 | root |
| unicomm.datasource.password | MySQL 密码，仅 mysql 模式使用 | 通过 `UNICOMM_DB_PASSWORD` 覆盖 |
| sa-token.timeout | Token 有效期 (秒) | 259200 (3天) |

## License

Proprietary - UniComm Team
