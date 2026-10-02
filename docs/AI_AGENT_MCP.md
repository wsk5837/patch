# 公司智能体与 ANOWX 对接

## 功能边界

智能体只用于查询系统内已有数据、跨模块检索和生成可视化报表。MCP 工具集不暴露新建、修改、删除、提交或审批工具，`MCP_WRITE_ENABLED` 生产环境保持为 `false`。

已实现的查询工具：

- 当前用户和有效权限
- 全局搜索
- 资产列表与详情
- 漏洞库、漏洞实例与详情
- 补丁库与补丁详情
- 待办审批
- 统计数据和柱状图、折线图、环形图报表规格

## 服务端环境变量

复制根目录 `.env.example` 后填写，不要将真实 Token 提交到 Git。核心配置为：

```dotenv
AI_MODE=live
AI_BASE_URL=https://<company-agent-host>
AI_AGENT_ID=<agent-id>
AI_API_TOKEN=<server-side-token>
AI_CHAT_PATH=/adk/run_stream
AI_TIMEOUT_SECONDS=120
MCP_API_TOKEN=<random-long-token>
MCP_WRITE_ENABLED=false

## 本次已创建的漏洞补丁智能体

平台地址：`https://adk.gazellio.com`

智能体 ID：`muyBxPTrK6OZH3Md`

ANOWX Render 后端地址：`https://gazellio.onrender.com`

对应生产配置：

```env
AI_MODE=live
AI_BASE_URL=https://adk.gazellio.com
AI_AGENT_ID=muyBxPTrK6OZH3Md
AI_CHAT_PATH=/adk/run_stream
AI_TIMEOUT_SECONDS=120
PUBLIC_BASE_URL=https://gazellio.onrender.com
```

`AI_API_TOKEN` 只填写智能体平台重新生成的 Agent API Key，不写入仓库；`MCP_API_TOKEN` 只填写 ANOWX 服务端环境变量中的 MCP 令牌，不要与 Agent API Key 混用。
MCP_SERVICE_USERNAME=ai-reader
PUBLIC_BASE_URL=https://<anowx-host>
```

`ai-reader` 由种子数据创建为只读服务账号，其有效权限仍以数据库 RBAC 配置为准。开启 `MCP_TRUSTED_USER_HEADER` 前必须确保反向代理会删除外部传入的 `X-ANOWX-User` 并仅由受信智能体网关重新写入。

## 公司智能体后台配置

```text
类型：HTTP Streamable
接口地址：https://<anowx-host>/mcp/
请求头：
{
  "Authorization": "Bearer <MCP_API_TOKEN>"
}
附加配置：{}
```

MCP 服务实现 `initialize`、`tools/list`和 `tools/call`，支持普通 JSON 及 `Accept: text/event-stream` 的流式响应。未携带 Token 或 Token 错误返回 401，账号禁用或权限不足返回 403。

## Nginx 反向代理

```nginx
location /mcp/ {
    proxy_pass http://127.0.0.1:8080/mcp/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 180s;
}

location /api/ {
    proxy_pass http://127.0.0.1:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 180s;
}
```

## 基础验收

```bash
curl -i -X POST https://<anowx-host>/mcp/ \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

curl -i -X POST https://<anowx-host>/mcp/ \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <MCP_API_TOKEN>' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
```
