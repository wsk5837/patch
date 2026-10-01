# GitHub 覆盖与 Render 升级指南

## 一、本次是否需要手工删除代码文件

不需要。本版本没有移除或改名旧文件，可以直接用新工程覆盖旧工程。

覆盖时务必包含下列隐藏文件：

- `.gitignore`
- `.dockerignore`
- `backend/.dockerignore`
- `frontend/.dockerignore`

## 二、推荐上传方式（GitHub Desktop）

1. 保留电脑上原有的 Git 仓库文件夹，不要删除其中的 `.git` 目录。
2. 解压新工程包。
3. 进入解压得到的 `patch-repo` 目录，全选其中内容，复制并覆盖到原仓库根目录。不要把 `patch-repo` 这一层目录嵌套进仓库。
   macOS 询问文件冲突时必须选择“替换”，不要选择“保留两者”；“保留两者”会生成 `Foo 2.java`，导致 Java 重复类编译失败。
4. 打开 GitHub Desktop，确认变更列表中没有 `node_modules`、`dist` 或 `target`。
5. Summary 填写 `Integrate read-only company CMDB assets`，点击 **Commit to main**。
6. 点击 **Push origin**。Git 只会上传真正变化的文件，即使你刚才覆盖了整个目录。

如果 GitHub Desktop 的 Changes 中出现 `GazellioApplication 2.java`、`Asset 2.java` 等带空格和数字的文件，请不要提交；删除这些副本，仅保留没有数字后缀的原文件。本工程的 Git/Docker 忽略规则也会拦截常见的 ` 2`～` 5` 冲突副本。

## 三、如果只使用 GitHub 网页

可以上传全部文件，但 GitHub 网页对隐藏文件和大量文件的处理不如 GitHub Desktop 稳定。如使用网页，必须检查仓库根目录最终直接包含：

```text
render.yaml
Dockerfile
docker-compose.yml
frontend/
backend/
```

不能变成：

```text
patch-main/
  render.yaml
  frontend/
  backend/
```

## 四、Render 上的一次性操作

1. 进入 Render Dashboard 的 Blueprint 页面。
2. 对当前 Gazellio Blueprint 点击 **Manual Sync / Sync Blueprint**。仅点击原 Web Service 的 Manual Deploy 不会创建新静态站点。
3. 确认 Render 将管理三个资源：`gazellio-web`、`gazellio` 和 `gazellio-db`。
4. 不要删除 `gazellio-db`。原有 `gazellio` 服务保留为 Java API。
5. 部署完成后，打开 `gazellio-web` 的 URL，不再把 `gazellio` API URL 当作页面地址。
6. 在后端 `gazellio` 服务的 Environment 中设置 `CMDB_CLIENT_ID` 和 `CMDB_CLIENT_SECRET` 为 Secret；不要把值提交到 GitHub。`CMDB_ENABLED`、CMDB 地址和启动同步开关已由 Blueprint 配置。

本次升级会在后端启动时自动升级旧 PostgreSQL 数据库：

- 补齐并规范化 `assets.internet_exposed`，先填充历史空值，再恢复默认值和非空约束。
- 补齐 CMDB 配置项 ID、类型、状态和同步时间等资产字段，并安全回填历史资产来源。
- 升级漏洞严重程度约束，允许暂无 CVSS 评分的漏洞使用 `UNKNOWN`。
- 将旧演示数据中的支付节点、门户节点等资产自动迁移为客户的 20 类产品资产，并保留原有漏洞、任务和工单关联。
- 批量补丁页的网段、资产类型、操作系统和服务分类均从当前资产目录动态读取，不再要求手工填写 CIDR。
- 新增用户、角色、角色权限、补丁排程数据表；Hibernate 会自动创建，不需要手工执行 SQL。
- 现有五类演示人员会迁移为数据库用户并绑定系统角色，初始密码统一为 `Gazellio@123`；后续启动不会覆盖已修改的密码。
- CMDB 同步成功后，公司配置项成为在管资产；旧演示资产仅停用、不删除，确保历史工单和审计关系不会断裂。扫描、补丁验证和执行结果保存在 Gazellio，不会写回 CMDB。

不要删除 `gazellio-db`，也不需要手工执行 SQL。

以后仅修改 `frontend/` 时，Render 只构建静态站点；仅修改 `backend/` 时，Render 只构建 Java API。
