import { Routes, Route, Navigate } from 'react-router-dom'
import BasicLayout from './layouts/BasicLayout'
import Dashboard from './pages/dashboard/Dashboard'
import ServiceList from './pages/service/ServiceList'
import ServiceDetail from './pages/service/ServiceDetail'
import MetricsPanel from './pages/metrics/MetricsPanel'
import TraceList from './pages/trace/TraceList'
import TopologyView from './pages/topology/TopologyView'
import DynamicConfig from './pages/config/DynamicConfig'

function App() {
  return (
    <Routes>
      <Route path="/" element={<BasicLayout />}>
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="services" element={<ServiceList />} />
        <Route path="services/:serviceKey" element={<ServiceDetail />} />
        <Route path="metrics" element={<MetricsPanel />} />
        <Route path="metrics/:serviceKey" element={<MetricsPanel />} />
        <Route path="topology" element={<TopologyView />} />
        <Route path="traces" element={<TraceList />} />
        <Route path="config" element={<DynamicConfig />} />
      </Route>
    </Routes>
  )
}

export default App
