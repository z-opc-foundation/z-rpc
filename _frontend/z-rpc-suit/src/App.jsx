import {Navigate, Route, Routes} from 'react-router-dom'
import {AppLayout} from '@yuku123/z-frontend-common'
import {menuItems, routeTable} from '@yuku123/z-rpc-component/pages'

export default function App() {
    return (
        <Routes>
            <Route path="/" element={<Navigate to="/services" replace/>}/>
            <Route path="/" element={
                <AppLayout menuItems={menuItems} appTitle="z-rpc 服务发现" appShort="RPC"/>
            }>
                {routeTable.map((r) => (
                    <Route key={r.path} path={r.path} element={<r.Component/>}/>
                ))}
            </Route>
        </Routes>
    )
}
