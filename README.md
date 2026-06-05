# UniComm Server

UniComm 统一通讯平台后端服务 (Spring Boot).

## 技术栈

- **Spring Boot 4.0.6** - 核心框架
- **Java 21** - 运行时
- **Maven** - 构建工具
- **MySQL 8+/9.x** - 数据库
- **Redis 8.x** - 缓存/会话存储（后续启用）
- **Sa-Token 1.45.0** - 认证/会话管理 (stateless token 模式)
- **Spring JDBC** - 数据访问
- **SpringDoc OpenAPI 3.0.3** - API 文档
- **WebSocket** - Memo 实时变更事件推送

## 项目结构

```
src/main/java/com/unicomm/
├── UniCommApplication.java          # 启动类
├── config/                          # 配置类
│   ├── SaTokenConfig.java           # Sa-Token 配置
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
    └── memo/                        # 备忘录模块
        ├── controller/MemoController.java
        ├── controller/MemoGroupController.java
        ├── dto/MemoDtos.java
        └── service/
            ├── MemoService.java
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

### 运行

MySQL 安装完成后，可以直接执行初始化脚本创建数据库和表：

```bash
mysql -h localhost -P 3306 -u root -p < src/main/resources/db/schema-mysql.sql
```

本地连接信息建议通过环境变量覆盖，避免把密码写入文档或提交历史：

```bash
export UNICOMM_DB_URL="jdbc:mysql://localhost:3306/unicomm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
export UNICOMM_DB_USERNAME="root"
export UNICOMM_DB_PASSWORD="<your-local-password>"
```

启动服务：

```bash
mvn spring-boot:run
```

服务启动后运行在 `http://localhost:28080`。

`application.yml` 会自动执行 `src/main/resources/db/schema-mysql.sql`。该文件是当前唯一的 MySQL 初始化脚本入口。

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
const list = await req('/memos?page=1&size=10', { headers: tokenHeaders });
console.log({ user: auth.username, groups: groups.length, created: created.id, status: updated.status, list: list.total });
NODE
```

### WebSocket Smoke Test

服务启动后可连接 `ws://localhost:28080/ws`。Memo 写操作会广播事件，例如：

```json
{
  "module": "memo",
  "type": "memo.created",
  "ownerUsername": "evan.zhao",
  "memoId": 1,
  "groupId": 1,
  "occurredAt": "2026-05-30T16:58:51"
}
```

当前事件类型包括 `memo.created`、`memo.updated`、`memo.related.updated`、`memo.deleted`、`group.created`、`group.updated`、`group.deleted`。

### 认证与当前用户

除 `/api/v1/auth/desktop/verify` 和 Swagger 文档外，`/api/v1/**` 接口都需要携带后端签发的 Sa-Token。桌面端会同时发送 `unicomm-token` 和 `Authorization: Bearer ...`，后端当前以 `unicomm-token` 作为 token-name。

Memo 模块不再使用开发期默认用户名兜底。创建者、相关人和权限判断都来自 Token 中的 loginId；未登录或 Token 失效时会返回 401。

### Memo 相关人权限

Memo 以创建人为 `owner`，相关人支持两种权限：

- `view`：只读，只能查看该 Memo。
- `edit`：可编辑标题、正文和状态。

只有 `owner` 可以调整分组、相关人权限、置顶、收藏和删除。接口仍兼容旧的 `relatedUsernames` 字段，新版前端会提交 `relatedUsers: [{ username, permission }]`。

Memo 列表支持 `isShared=true` 查询“与我相关”，即别人共享给当前用户且非当前用户创建的 Memo。

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

## 测试用户 (Phase 1 临时数据)

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
| unicomm.datasource.url | MySQL JDBC 地址 | `jdbc:mysql://localhost:3306/unicomm...` |
| unicomm.datasource.username | MySQL 用户名 | root |
| unicomm.datasource.password | MySQL 密码 | 通过 `UNICOMM_DB_PASSWORD` 覆盖 |
| sa-token.timeout | Token 有效期 (秒) | 259200 (3天) |

## License

Proprietary - UniComm Team
