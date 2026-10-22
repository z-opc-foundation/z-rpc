# z-rpc-component

z-rpc（服务发现 / 注册中心）跨仓唯一前端面：把「服务注册表 / 服务实例明细」这一组注册中心视图
收敛成一个可复用的 React 组件包，由 `z-rpc-suit` 独立壳、z-team 的 OpsWorkbench（`/rpc/*` 通配挂载）
或 z-opc 主壳挂载。

- **npm 包名**：`@yuku123/z-rpc-component`
- **宿主接入**：`@yuku123/z-rpc-component/pages`（manifest + 请求层），主壳可只取 `ComponentDocPage`
- **后端**：z-config 的 **naming 模块**（服务注册发现），路径 `/naming/**`，前缀由
  `configureRpc({apiBase})` 注入
- **技术栈**：React 19 + antd 6 + react-router-dom 7 + vite（library mode，双入口 `.` / `./pages`）

---

## 一、能力概览

包里有**两套**入口，路由前缀不同，别混：

### 1.1 manifest 面（`/z-rpc/*`，`src/pages-manifest.jsx`）

| 页面 | 路由（相对 `/z-rpc/*`） | 源文件 | 能力 |
|------|--------------------------|--------|------|
| 首页 | `home` | `pages/HomePage.jsx` | 读 `userInfo` 显示登录人，按 `menuItems` 渲染功能入口卡片 |
| 服务注册表 | `services` | `pages/ServiceTable.jsx` | 服务清单，可展开看每个服务下的实例 |
| 实例 | `instances` | `pages/InstanceTable.jsx` | 按服务名查实例明细 |
| 登录 | （不在菜单内） | `pages/LoginPage.jsx` | mock 登录，写 `token` / `userInfo` 后跳 `/z-rpc/home` |
| 组件文档 | `docs` | `pages/ComponentDoc.jsx` | 本文件渲染页（左侧 240px TOC + 右侧正文） |

### 1.2 RpcApp 面（`/rpc/*`，`src/pages/RpcApp.jsx`）

`RpcApp` 是给 OpsWorkbench 通配挂载用的**子路由集合**，入口重定向到 `service`：

| 子路由 | 源文件 | 能力 |
|--------|--------|------|
| `service` | `pages/ServiceTable.jsx` | 同上（复用同一个组件） |
| `instance` | `pages/InstanceTable.jsx` | 同上（复用同一个组件） |

`src/pages/ServiceTable.test.tsx` 是列绑定测试（vitest + @testing-library），
**不是页面**，也不参与打包。

## 二、导出面

```js
// 主入口（. 桶）：请求层 + 域挂载点
import {rpcApi, configureRpc, parseClusterMap, flattenInstances, RpcApp, InstanceTable} from '@yuku123/z-rpc-component'

// 宿主组装面（./pages 桶）：manifest + 页面组件
import {menuItems, routes, HomePage, LoginPage, ServiceTable, ComponentDocPage} from '@yuku123/z-rpc-component/pages'

// 组件文档页（主壳接线用，名字全仓统一）
import {ComponentDocPage} from '@yuku123/z-rpc-component'
```

## 三、API 端点

请求实例由基座 `createRequest` 工厂创建（`src/services/api.js`），**`baseURL` 默认为空串**
⇒ 下面的路径就是浏览器实际请求的路径（不带 `/api` 前缀），token 走 `Authorization: Bearer`，
token 存在 `localStorage.zrpc_token`。
下表路径是 `src/services/api.js` 里 `rpcApi` 定义的**实际路径**。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/naming/listServices` | 服务列表（**当前唯一被页面调用的接口**） |
| GET | `/naming/getAllInstances` | 全部实例（query：`serviceName` / `groupName`）⚠ 当前无页面调用 |
| GET | `/naming/selectInstances/healthy` | 健康实例（query：`serviceName` / `healthy` / `groupName`）⚠ 当前无页面调用 |
| GET | `/naming/selectOneHealthyInstance` | 单个健康实例（query：`serviceName` / `groupName`）⚠ 当前无页面调用 |

## 四、页面说明

### 4.1 首页

登录后的着陆页。读 `localStorage.userInfo` 显示欢迎语与角色标签，再把 `menuItems`（去掉首页自身）
渲染成入口卡片 —— 菜单加一项，首页卡片自动多一张，不用改页面代码。

### 4.2 服务注册表（`pages/ServiceTable.jsx`）

调 `/naming/listServices` 拿全量服务，每行三列：服务名（等宽字体）、分组、实例数。
行可展开，展开后是第二个表格，按 `clusterMap` 扁平化出的实例行（集群 / 地址 / 健康 / 权重）。

⚠ `clusterMap` 解析失败时实例数列显示红色标签「clusterMap 解析失败」（值 `-1`），
不把解析失败当成「0 个实例」。

### 4.3 实例（`pages/InstanceTable.jsx`）

一个服务名输入框（格式 `group@@name`）+ 实例表。
实现方式是**先调 `/naming/listServices` 找到那个服务，再自己解析它的 `clusterMap`** ——
没有走 `rpcApi.getAllInstances`。

服务名也会从 URL query `?serviceName=` 预填。

### 4.4 登录

当前是 **mock 模式**：比对一对写死在 `src/pages/LoginPage.jsx` 里的管理员凭据
（明文口令不在本文复述，见源码），成功后写 `localStorage.token` 与 `localStorage.userInfo`，
再 `window.location.href = '/z-rpc/home'`。CTC/SSO 尚未接入。

### 4.5 组件文档

本文件。内容以 `?raw` 在编译期内联，零额外请求；左侧 240px TOC 随滚动联动。

## 五、配置

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `configureRpc({apiBase})` | `''` | 改写请求实例的 `baseURL`，用于宿主换挂载前缀 |
| token 存储键 | `zrpc_token` | `createRequest` 的 `tokenKey`，与登录页写入的键一致 |
| 请求超时 | `10000` | `createRequest` 默认值 |

## 六、已知限制

- ⚠ **测试与实现脱节**：`src/pages/ServiceTable.test.tsx` 期望 `ServiceTable` 有 **8 列**
  （服务名 / 分组 / 命名空间 / 集群数 / 实例数 / 健康实例 / 状态 / 操作），读的是后端实发的键
  `clusters` / `healthyInstanceCount` / `totalInstanceCount` / `status`；
  而当前 `pages/ServiceTable.jsx` 只有 **3 列**（服务名 / 分组 / 实例数），读的是
  `clusterMap` / `groupName`。⇒ 这支测试对着当前实现跑必然红。
  `package.json` 里也没有 `test` 脚本、devDependencies 里没有 vitest / @testing-library，
  所以 CI 不会跑到它 —— 缺陷因此长期没被发现。
- **分组列可能恒空**：后端 `ZNamingController#listServices` 实发的键是 `group`，
  页面读的是 `s.groupName`（同上根因）。
- **实例表的 IP 列恒空**：`pages/InstanceTable.jsx` 的列是 `dataIndex: 'ip'`，
  但 `flattenInstances` 产出的字段名是 `host`（`host: ins.host || ins.ip || ''`）
  ⇒ IP 列取不到值；「临时实例」列读 `ephemeral`，`flattenInstances` 也不产出该字段，同样恒空。
  该表的 `rowKey` 是 `${r.ip}:${r.port}:${r.cluster}`，其中 `ip` 段为 `undefined`。
- `rpcApi` 的 4 个接口里只有 `listServices` 被页面用到；
  `getAllInstances` / `selectInstances/healthy` / `selectOneHealthyInstance` 三个都没有页面调用。
- `pages/InstanceTable.jsx` 的副标题写「数据源 GET /api/naming/listServices」，
  但请求实例 `baseURL` 是空串 ⇒ 实际打出去的是 `/naming/listServices`（没有 `/api` 前缀）。
- z-rpc 已被 z-opc 主壳的 `domainRoutes.ts` 与 z-team 的 OpsWorkbench 引用
  ⇒ `/z-rpc/docs` 在平台主壳里**可达**（主壳按 manifest 的 `routes` / `menuItems` 挂载）。
- `src/pages.js` 原先手挑导出名，漏了 `HomePage` / `LoginPage`，
  而 `z-rpc-suit/src/App.jsx` 正在从 `@yuku123/z-rpc-component/pages` 导入这两个名字
  ⇒ suit 构建报 `"LoginPage" is not exported by "../z-rpc-component/dist/pages.js"`（本次修复）。

## 七、变更记录

| 版本 | 变更 |
|---|---|
| 0.1.0 | 新增组件文档页 `ComponentDocPage`（lead 008 §7）：`src/DOC.md` + 基座 `ComponentDoc` 薄包装 |
| 0.1.0 | `src/pages.js` 补齐 `HomePage` / `LoginPage` 转发，修掉 suit 构建的 "not exported by" 失败 |
| 0.1.0 | 首版：服务注册表 + 实例表 + RpcApp 挂载点 + manifest |
