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
3. 进入解压得到的 `Gazellio-Render-Split` 目录，全选其中内容，复制并覆盖到原仓库根目录。不要把 `Gazellio-Render-Split` 这一层目录嵌套进仓库。
4. 打开 GitHub Desktop，确认变更列表中没有 `node_modules`、`dist` 或 `target`。
5. Summary 填写 `Split Render frontend and backend deployments`，点击 **Commit to main**。
6. 点击 **Push origin**。Git 只会上传真正变化的文件，即使你刚才覆盖了整个目录。

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
Gazellio-Render-Split/
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

以后仅修改 `frontend/` 时，Render 只构建静态站点；仅修改 `backend/` 时，Render 只构建 Java API。
