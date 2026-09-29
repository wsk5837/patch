# Gazellio 漏洞与补丁管理平台

Gazellio 已从单文件 HTML 原型调整为可部署的前后端分离工程：

- `frontend/`：React + Vite，负责产品 UI、路由和中英文切换。
- `backend/`：Java 21 + Spring Boot，负责认证、业务流程、扫描任务、漏洞、CMDB、补丁、审批、自动化编排和审计。
- PostgreSQL：保存业务数据和流程状态。
- `render.yaml`：Render Blueprint，一次创建全栈 Web Service 和 PostgreSQL。

## 直接部署到 Render

1. 把本目录全部上传到一个 GitHub 仓库的根目录。
2. 在 Render 选择 **New > Blueprint**，连接该 GitHub 仓库。
3. Render 会读取根目录的 `render.yaml`，创建：
   - `gazellio`（React 页面与 Java API 合并部署）
   - `gazellio-db`
4. 首次创建时输入 `ADMIN_INITIAL_PASSWORD`。
5. 等待两个资源部署完成，打开 `gazellio` 的公开地址。

默认管理员账号：`admin`。密码为你在 Render 中填写的 `ADMIN_INITIAL_PASSWORD`。

> 根目录多阶段 Dockerfile 先构建 React，再把产物打入 Spring Boot 静态资源目录。浏览器页面和 `/api` 使用同一个 Render 域名，不需要额外配置跨域地址。

### Render 登录与页面检查

- 必须通过 **Blueprint** 首次创建，或者在 `gazellio` 服务的 Environment 中手动设置 `ADMIN_INITIAL_PASSWORD`。
- `ADMIN_INITIAL_PASSWORD` 只用于管理员引导登录，不要提交到 Git 仓库。
- `/actuator/health` 正常但首页是 403，通常表示部署的仍是旧版本；重新部署最新提交。
- 页面刷新后应继续返回 React 页面，包括 `/login`、`/tasks/{id}` 和 `/automation/runs/{id}`。
- CISA KEV 不在应用启动时自动全量同步，避免免费实例首次启动超时；登录后可在漏洞库手动同步，定时同步仍保留。

## 本地运行

有 Docker Compose 时：

```bash
docker compose up --build
```

浏览器打开：`http://localhost:10000`

本地默认账号：

```text
admin
Gazellio@2026
```

## 当前产品能力

- 漏洞库：内置 75+ 条基础漏洞数据；可在漏洞库中手动同步 CISA KEV，避免部署启动阶段执行外部全量同步。
- 漏洞发现：以 `资产 + CVE` 去重，同一资产重复扫描不会重复创建相同实例；已关闭漏洞再次命中会重新打开。
- 风险处置：漏洞可确认修复、标记误报或设置有截止日期的风险豁免。
- 漏洞扫描：扫描任务、Agent、认证扫描、网络扫描和测试/预生产/生产定向复测。
- CMDB：资产、环境、业务系统、责任人、重要度、Agent 状态和补丁基线。
- 补丁中心：补丁库、CVE 映射、补丁服务器和部署记录。
- 处置任务：测试补丁、应用验证、漏洞复测、发布审批、预生产验证、生产执行、生产复测与关闭。
- 发布审批：紧急 / 重大 / 常规 / 标准四类流程；审批通过后进入实施，生产复测通过后变更关闭。
- 自动化编排：模板详情可查看流程图；执行轨迹保存到数据库并持续推进，支持暂停、继续和回滚。
- 双语：界面所有产品文案、状态、按钮、弹窗和流程节点共用同一套中英文资源；CI 会检查中英文 key 是否缺失。
- 审计：扫描、漏洞确认、豁免、任务、审批、补丁执行、回滚和关闭均写入审计日志。

## 交互原则

普通业务页面不展示“从扫描到关闭”的说明性流程条，也不放用于解释系统如何工作的提示卡。流程只在真正需要查看流程的地方出现：

- 自动化编排模板 / 执行记录
- 发布审批详情

列表进入详情页；创建、验证、审批等短操作使用聚焦弹窗；扫描和补丁执行等长操作进入可持续查看的任务 / 执行记录。

## Scanner Agent / 外部漏扫接入

Java API 提供 `/api/agent/*` 接口用于 Agent 或第三方漏扫适配器注册、心跳和上传扫描结果。Render 上的内置扫描执行器用于安全演示完整业务闭环，不会主动扫描任意互联网或内网地址。实际落地时，Agent 应部署在目标网络或终端侧，并通过这些 API 回传结果。

注册 Agent 时使用 Render 自动生成的 `AGENT_REGISTRATION_TOKEN`。

## 性能说明

- 列表和详情使用批量关联查询，避免逐行访问数据库；仪表盘统计由数据库聚合并使用 5–15 秒短缓存。
- 前端轮询不会重叠执行，浏览器标签页进入后台后会暂停轮询；接口请求带 15 秒超时，不会永久停留在加载状态。
- JSON、HTML、CSS 和 JavaScript 响应启用压缩；常用查询字段会由 Hibernate 在 PostgreSQL 中补建索引。
- 自动化测试会用完整演示数据检查常用暖机接口是否在 1 秒内完成。

Render 免费 Web Service 会在空闲时休眠。休眠后的第一次访问需要等待实例重新启动，这段时间不属于应用接口耗时，无法仅靠代码保证 1 秒；如需任何时间打开都快速响应，应使用 Render 常驻实例。

## 生产上线前建议

当前工程可以直接用于 Render 演示和 POC。正式生产前建议关闭演示数据、接入企业 SSO/LDAP、替换真实漏扫引擎与补丁源、限制 API 网络入口、配置数据库备份，并按组织实际角色调整审批模板。
