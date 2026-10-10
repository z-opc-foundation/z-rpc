import { ClusterOutlined, HomeOutlined, NodeIndexOutlined } from '@ant-design/icons'
import ServiceTable from './pages/ServiceTable'


export {default as ServiceTable} from './pages/ServiceTable'
import HomePage from './pages/HomePage'

/** 菜单 + 路由清单（lead 008 §10/§14/§16 批量落地）。App 壳在 suit 侧组装。 */
export const appMeta = { title: 'z-rpc 服务发现', short: 'z-rpc' }

export const menuItems = [
    { key: '/z-rpc/home', label: '首页', icon: <HomeOutlined /> },
    { key: '/z-rpc/services', label: '服务注册表', icon: <ClusterOutlined /> },
]

export const routeTable = [
    { path: '/z-rpc/home', Component: HomePage },
    { path: '/z-rpc/services', Component: ServiceTable },
]

export { default as HomePage } from './pages/HomePage'
export { default as LoginPage } from './pages/LoginPage'
