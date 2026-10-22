# Z-RPC Admin Frontend

React + Ant Design + Vite + ECharts 的 Z-RPC 服务治理控制台。

## 技术栈

- **React 19.3** + **TypeScript 5.9**
- **Vite 5.4** 构建
- **Ant Design 6.6** UI 组件库
- **React Router 7.18** 路由
- **Axios** HTTP 客户端
- **ECharts 5.6** 图表（QPS / RT / 错误率）
- **Zustand** 轻量状态管理
- **@yuku123/z-frontend-common 1.1.2** 共享 UI 基座：空/加载/错误态、搜索框、工具条、渐变数据卡、图表容器统一走基座，
  本仓不再手写这些状态

## 开发

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev       # http://localhost:5173

# 构建生产包（含 tsc -b 类型检查）
npm run build

# 预览生产包
npm run preview
```

## 产物去向

`npm run build` 的产物落在 `z-rpc-admin-frontend/dist/`（静态文件）。
**本仓没有消费这个 dist 的通道**：不在 Maven reactor 内、无 `deploy/` / `k8s/` / `Dockerfile`，
`z-rpc-admin` 也不把它打进 jar（后端只提供 `/api/admin/**` 的 REST 接口）。
所以 dist 目前只是本地预览产物；真要上线需要另建静态站点托管或给 admin 加静态资源映射。

## 后端

前端需要 z-rpc-admin 后端运行在 `http://localhost:19090`（端口以 `z-rpc-admin/src/main/resources/application.yml` 的
`server.port` 为准，旧的 9090 是过期读数）。
可通过 `.env` 文件修改：

```bash
VITE_API_BASE_URL=http://localhost:19090
```

## 功能页面

1. **概览（Dashboard）** - 服务总数、Provider 数、QPS、健康度；实时趋势图
2. **服务管理** - 列表、搜索、Provider 详情、RT 趋势
3. **监控面板** - QPS / P50-P90-P99 / 错误率 时序
4. **服务拓扑** - 服务依赖关系图
5. **链路追踪** - Trace ID / RT / 状态
6. **动态配置** - 路由规则、Mock 规则、权重调整

## 目录

```
src/
├── api/         # Axios 客户端 + REST API
├── layouts/     # 基础布局（侧边栏 + 顶栏 + 内容区）
├── pages/       # 页面（dashboard / service / metrics / trace / topology / config）
├── styles/      # 全局样式
├── types/       # TypeScript 类型
├── App.tsx      # 路由配置
└── main.tsx     # 入口
```
