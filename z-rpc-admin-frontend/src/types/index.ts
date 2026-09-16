export interface ServiceVO {
  serviceKey: string
  serviceName: string
  version: string
  group: string
  providerCount: number
  consumerCount: number
  metrics?: { qps?: number }
}

export interface ProviderVO {
  id: string
  service: string
  address: string
  host: string
  port: number
  weight: number
  version: string
  group: string
  healthy: boolean
  metrics?: Record<string, any>
}

export interface MetricsPoint {
  timestamp: number
  qps: number
  p50: number
  p90: number
  p99: number
  errorRate: number
}

export interface TraceRecord {
  traceId: string
  service: string
  method: string
  startTime: number
  rt: number
  success: boolean
  error?: string
  remoteAddress?: string
}

export interface DashboardOverview {
  serviceCount: number
  providerCount: number
  totalQps: number
}
