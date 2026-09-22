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

## 扫码溯源（手机扫前端二维码）

二维码链路：企业端「产品批号 → 溯源码」生成图形化二维码，消费者端 `/trace` 查询成功后展示同一张码；手机相机或微信扫描后打开 `/trace/<溯源码>`，直接看到该产品的全链路记录。

二维码内容必须是**手机能访问到的地址**，因此链接按以下优先级生成：

1. `VITE_TRACE_BASE_URL`（已配置时直接使用，适合正式部署）；
2. 当前页面访问地址（用域名或内网 IP 打开页面时）；
3. 页面在 `localhost` 时，向后端 `/api/public/access-hosts` 探测本机内网 IP，改写成 `http://<内网IP>:<5173>/trace/<溯源码>`。

本机联调手机扫码的完整条件：

1. 手机与电脑连接同一 Wi-Fi；
2. 开发服务器已监听局域网（本项目 `vite.config.ts` 已开启 `server.host`），启动后控制台会打印 `Network: http://192.168.x.x:5173/`；
3. Windows 首次启动会弹出防火墙提示，需要允许 Node.js 通过专用网络，否则手机连不上；
4. 企业端/消费者端展示的二维码下方会显示实际编码的链接，核对是内网地址即为正确。

页面内的「扫一扫」是给电脑用户兜底的：浏览器只在 `https` 或 `localhost` 下允许调用摄像头，手机通过 `http://192.168.x.x` 访问页面时该按钮不可用，但手机系统相机、微信扫码不受此限制（它们只是打开二维码里的链接）。
