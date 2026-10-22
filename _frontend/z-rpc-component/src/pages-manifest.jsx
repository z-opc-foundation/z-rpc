import { ClusterOutlined, HomeOutlined, NodeIndexOutlined, ReadOutlined } from '@ant-design/icons'
import ServiceTable from './pages/ServiceTable'
import InstanceTable from './pages/InstanceTable'
import RpcApp from './pages/RpcApp.jsx'


export {default as ServiceTable} from './pages/ServiceTable'
import HomePage from './pages/HomePage'
import ComponentDocPage from './pages/ComponentDoc.jsx'

/** 菜单 + 路由清单（lead 008 §10/§14/§16 批量落地）。App 壳在 suit 侧组装。 */
export const appMeta = { title: 'z-rpc 服务发现', short: 'z-rpc' }

export const menuItems = [
    { key: '/z-rpc/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-rpc/services', label: '服务注册表', icon: <ClusterOutlined /> },
    { key: '/z-rpc/docs', label: '组件文档', icon: <ReadOutlined /> },
]

export const routes = [
    { path: '/z-rpc/home', Component: HomePage },
    { path: '/z-rpc/services', Component: ServiceTable },
    { path: '/z-rpc/instances', Component: InstanceTable },
    { path: '/z-rpc/docs', Component: ComponentDocPage },
    { path: '/z-rpc/:rest*', Component: RpcApp },
]

export { default as HomePage } from './pages/HomePage'
export { default as LoginPage } from './pages/LoginPage'
// 主壳接线用：文档页统一以 ComponentDocPage 这个名字从主入口导出。
export { default as ComponentDocPage } from './pages/ComponentDoc.jsx'
