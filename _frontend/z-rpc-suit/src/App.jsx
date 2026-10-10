import {Navigate, Route, Routes} from 'react-router-dom'
import {AppLayout} from '../../../../_shared/z-frontend-common-local/dist/z-frontend-common.es.js'
import {menuItems, routeTable} from '@yuku123/z-rpc-component/pages'

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/services" replace/>}/>
            <Route path="/" element={
                <AppLayout menuItems={menuItems} appTitle="z-rpc 服务发现" appShort="RPC" appIcon={{icon: <img src="/icon.png" alt="RPC" style={{width: "100%", height: "100%", objectFit: "cover", borderRadius: 8}}/>, color: '#0891b2', label: 'RPC'}}/>
            }>
                {routeTable.map((r) => (
                    <Route key={r.path} path={r.path} element={<r.Component/>}/>
                ))}
            </Route>
        </Routes>
    )
}
