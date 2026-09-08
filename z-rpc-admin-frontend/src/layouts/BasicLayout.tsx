import { Layout, Menu, theme } from 'antd'
import { Link, useLocation, Outlet } from 'react-router-dom'
import {
  DashboardOutlined,
  AppstoreOutlined,
  LineChartOutlined,
  ApartmentOutlined,
  FileSearchOutlined,
  SettingOutlined,
  GithubOutlined
} from '@ant-design/icons'

const { Header, Sider, Content } = Layout

const menuItems = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: <Link to="/dashboard">概览</Link> },
  { key: '/services', icon: <AppstoreOutlined />, label: <Link to="/services">服务管理</Link> },
  { key: '/metrics', icon: <LineChartOutlined />, label: <Link to="/metrics">监控面板</Link> },
  { key: '/topology', icon: <ApartmentOutlined />, label: <Link to="/topology">服务拓扑</Link> },
  { key: '/traces', icon: <FileSearchOutlined />, label: <Link to="/traces">链路追踪</Link> },
  { key: '/config', icon: <SettingOutlined />, label: <Link to="/config">动态配置</Link> }
]

export default function BasicLayout() {
  const location = useLocation()
  const { token: { colorBgContainer } } = theme.useToken()

  return (
    <Layout className="z-rpc-layout">
      <Header className="z-rpc-header">
        <div>
          <h1>Z-RPC Admin Console</h1>
          <span className="version">v1.0.0</span>
        </div>
        <div style={{ color: '#fff' }}>
          <GithubOutlined style={{ fontSize: 18, marginRight: 8 }} />
          <span>Apache-2.0</span>
        </div>
      </Header>
      <Layout>
        <Sider width={220} style={{ background: colorBgContainer }}>
          <Menu
            mode="inline"
            selectedKeys={[menuItems.find((m) => location.pathname.startsWith(m.key))?.key || '/dashboard']}
            style={{ height: '100%', borderRight: 0 }}
            items={menuItems}
          />
        </Sider>
        <Layout>
          <Content className="z-rpc-content">
            <Outlet />
          </Content>
        </Layout>
      </Layout>
    </Layout>
  )
}
