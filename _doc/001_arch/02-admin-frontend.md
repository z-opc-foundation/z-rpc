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

### 「没有发布通道」的逐条证据（2026-10-23 复核）

结论要成立，必须四条同时为真 —— 少一条就可能存在别的出口：

1. **不在 Maven reactor**：根 `pom.xml` 的 `<modules>` 里 `z-rpc-admin` 那行是**注释掉的**
   （`<!-- 临时排除 z-rpc-admin（依赖未发布的 z-config-*，等 z-config 上 Central 后再加） -->`），
   同段还注明「它自己那份 pom 按 repo1 的 z-rpc:1.0.3 解析」，所以它连本仓的 `${project.version}` 都接不上。
2. **admin 模块不打包前端产物**：`z-rpc-admin/pom.xml` 里**没有** `frontend-maven-plugin`
   （对比 `z-ops_frontend/pom.xml` 是有的），也没有任何指向 `z-rpc-admin-frontend/dist` 的
   `resources`/`copy` 配置 ⇒ 跑 `mvn package` 不会把 dist 带进 jar。
3. **集群里没有它的 Deployment**：全工作区（`z-opc/deploy/kubernetes/*.yaml`）搜 `rpc-admin`
   **零命中** ⇒ 没有可滚动更新的目标。
4. **前端仓自身也没有发布脚本**：`z-rpc-admin-frontend/` 下只有 `dist/`、`index.html`、
   `package.json`、`vite.config.ts` 等，`vite.config.ts` 的 `build.outDir` 是 `'dist'`（相对本地目录）。

⚠ **因此前端改动不会随本仓的常规发版上线**：改了 `z-rpc-admin-frontend` 的代码，
线上不会有任何变化（既没有 CI 打包进镜像，也没有静态站点托管它）。
要真正上线，得先补一条通道，三选一：
① 给 `z-rpc-admin` 加 `frontend-maven-plugin` 并把 `z-rpc-admin` 放回 reactor，
   然后把它也纳入 z-ops/z-opc 的镜像与派发清单；
② 把 dist 作为静态资源交给既有边缘 nginx 托管（配一条 location）；
③ 明确宣布该控制台**冻结**，只保留本地开发/预览用途。

**当前口径：按 ③ 处理**（不发布、不进发版清单），改动只保证代码可构建、不保证线上可见。

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
