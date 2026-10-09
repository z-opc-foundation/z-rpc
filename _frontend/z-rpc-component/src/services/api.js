/**
 * z-rpc API client：走 /naming/** 面（服务注册发现）。
 */
import {createRequest} from '@yuku123/z-frontend-common'

const request = createRequest({baseURL: '', tokenKey: 'zrpc_token'})

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

/** clusterMap 是 JSON 字符串（后端就这么存的），解析失败返回 null */
export function parseClusterMap(raw) {
    if (!raw) return {}
    try { return JSON.parse(raw) } catch { return null }
}

/** clusterMap → 实例行数组（扁平化 clusterName + host:port + healthy + weight） */
export function flattenInstances(cm) {
    if (!cm) return []
    const rows = []
    Object.entries(cm).forEach(([cluster, instances]) => {
        (instances || []).forEach((ins, i) => {
            rows.push({
                key: `${cluster}-${i}`,
                cluster,
                host: ins.host || ins.ip || '',
                port: ins.port || '',
                healthy: ins.healthy !== false,
                weight: ins.weight ?? 1,
                metadata: ins.metadata || {},
            })
        })
    })
    return rows
}
