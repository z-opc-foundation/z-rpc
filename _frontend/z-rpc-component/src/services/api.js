/**
 * z-rpc API client：走 /naming/** 面（服务注册发现）。
 *
 * ⚠ **2026-10-25 修**：baseURL 原为**空串** ⇒ axios 打出去的是裸 `/naming/listServices`
 *   （**没有** /api 前缀）⇒ 生产实测：
 *     · GET /naming/listServices → **200 + text/html**（落 SPA 兜底页 ⇒ JSON 解析失败
 *       ⇒ 服务注册表面板永远空，且页面报「后端未连接」）
 *   而 z-config-web 的 `ZNamingController` 映射在 **`/api/naming`**（实测
 *   `GET /api/naming/listServices` → 200 + `{"data":[]}`）⇒ baseURL 必须是 `/api`。
 *   （本仓 DOC.md §六 早就记过这条「副标题写 /api/naming、实际打出去没有 /api 前缀」，
 *     一直没人改 ⇒ 这一轮补上。）
 */
import {createRequest} from '@yuku123/z-frontend-common'

const request = createRequest({baseURL: '/api', tokenKey: 'zrpc_token'})

export default request

export function configureRpc(config) {
    if (config && config.apiBase !== undefined) {
        request.defaults.baseURL = config.apiBase
    }
}

export const rpcApi = {
    listServices: () => request.get('/naming/listServices'),
    allInstances: (serviceName, groupName) =>
        request.get('/naming/getAllInstances', {params: {serviceName, groupName}}),
    healthyInstances: (serviceName, healthy = true, groupName) =>
        request.get('/naming/selectInstances/healthy', {params: {serviceName, healthy, groupName}}),
    oneHealthy: (serviceName, groupName) =>
        request.get('/naming/selectOneHealthyInstance', {params: {serviceName, groupName}}),
}

/**
 * clusterMap 是 JSON 字符串（后端 `ZServiceInfo#clusterMap` 就是 String），
 * 但 **`/naming/listServices` 的响应键叫 `clusters`**（不是 `clusterMap`）⇒ 两种形态都收。
 * @returns {object|null} 解析失败返回 null（页面据此显示「解析失败」）
 */
export function parseClusterMap(raw) {
    if (!raw) return {}
    if (typeof raw === 'object') return raw
    try { return JSON.parse(raw) } catch { return null }
}

/**
 * clusterMap → 实例行数组（扁平化 clusterName + ip:port + healthy + weight）。
 *
 * ⚠ 2026-10-25 修：后端 `ZNamingInstance` 的字段是 **ip**（没有 host）、
 * 且带 **ephemeral**；原实现只产 `host`、丢掉 ephemeral ⇒
 * 「IP」列（dataIndex='ip'）恒空、「临时实例」列恒空、rowKey 里 ip 段恒为 undefined。
 */
export function flattenInstances(cm) {
    if (!cm) return []
    const rows = []
    Object.entries(cm).forEach(([cluster, instances]) => {
        (instances || []).forEach((ins, i) => {
            const ip = ins.ip || ins.host || ''
            rows.push({
                key: `${cluster}-${i}`,
                cluster,
                ip,
                host: ip,
                port: ins.port || '',
                healthy: ins.healthy !== false,
                weight: ins.weight ?? 1,
                ephemeral: ins.ephemeral === true,
                enabled: ins.enabled !== false,
                metadata: ins.metadata || {},
            })
        })
    })
    return rows
}
