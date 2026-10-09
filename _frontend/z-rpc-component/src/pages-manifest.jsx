import {ClusterOutlined, NodeIndexOutlined} from '@ant-design/icons'
import ServiceTable from './pages/ServiceTable'

export const menuItems = [
    {key: '/services', icon: <ClusterOutlined/>, label: '服务注册表'},
]

const routeTable = [
    {path: 'services', Component: ServiceTable},
]
export {routeTable}
export {default as ServiceTable} from './pages/ServiceTable'
