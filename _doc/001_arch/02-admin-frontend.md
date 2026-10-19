# Z-RPC Admin Frontend

React + Ant Design + Vite + ECharts 的 Z-RPC 服务治理控制台。

## 技术栈

- **React 18.3** + **TypeScript 5.5**
- **Vite 5.4** 构建
- **Ant Design 5.21** UI 组件库
- **React Router 6** 路由
- **Axios** HTTP 客户端
- **ECharts 5.5** 图表（QPS / RT / 错误率）
- **Zustand** 轻量状态管理

## 开发

```bash
# 安装依赖
pnpm install   # 或 npm install

# 启动开发服务器
pnpm dev       # http://localhost:5173

# 构建生产包
pnpm build

# 预览生产包
pnpm preview
```

## 后端

前端需要 z-rpc-admin 后端运行在 `http://localhost:9090`。
可通过 `.env` 文件修改：

```bash
VITE_API_BASE_URL=http://localhost:9090
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
