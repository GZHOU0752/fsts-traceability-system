# FSTS Frontend

冷冻海产品溯源系统前端，覆盖管理员端、流通节点企业端和消费者公开溯源页。

## 本地运行

要求 Node.js 20+。后端默认运行在 `http://localhost:8080`，开发服务器会把 `/api` 请求代理到后端。

```powershell
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`。管理端默认账号为后端演示数据中的 `admin / 123456`；企业账号以数据库演示数据为准。

## 配置

复制 `.env.example` 为 `.env.local`，按部署环境设置 `VITE_API_BASE_URL`。开发环境保持 `/api` 即可使用 Vite 代理，生产环境可以填写完整的后端 API 地址。

## 校验

```powershell
npm run test:unit
npm run typecheck
npm run build
```

前端路由按角色划分：`/admin/login`、`/enterprise/login` 和公开的 `/trace/:traceCode`。后端返回的统一 `code/message/data` 响应由请求层解包，401 会自动清理本地会话。
