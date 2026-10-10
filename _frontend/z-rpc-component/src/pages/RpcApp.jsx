import {Navigate, Route, Routes} from 'react-router-dom'
import ServiceTable from './ServiceTable'
import InstanceTable from './InstanceTable'

/** z-rpc 注册视图 — OpsWorkbench 以 /rpc/* 通配挂进来（数据源是 z-config 的 naming 模块） */
export default function RpcApp() {
    return (
        <Routes>
            <Route index element={<Navigate to="service" replace/>}/>
            <Route path="service" element={<ServiceTable/>}/>
            <Route path="instance" element={<InstanceTable/>}/>
        </Routes>
    )
}
