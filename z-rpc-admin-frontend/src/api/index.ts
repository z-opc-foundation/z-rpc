import client from './client'

// Dashboard
export const getDashboardOverview = () => client.get<any, any>('/api/admin/dashboard/overview')

// Services
export const listServices = (keyword?: string) =>
  client.get<any, any>('/api/admin/services', { params: { keyword } })

export const getServiceDetail = (serviceKey: string) =>
  client.get<any, any>(`/api/admin/services/${encodeURIComponent(serviceKey)}`)

// Providers
export const listProviders = () => client.get<any, any>('/api/admin/providers')

export const registerProvider = (data: any) =>
  client.post<any, any>('/api/admin/providers/register', data)

// Metrics
export const getServiceMetrics = (serviceKey: string) =>
  client.get<any, any>(`/api/admin/services/${encodeURIComponent(serviceKey)}/metrics`)

export const pushMetrics = (data: any) =>
  client.post<any, any>('/api/admin/metrics/push', data)

// Traces
export const listTraces = () => client.get<any, any>('/api/admin/traces')
