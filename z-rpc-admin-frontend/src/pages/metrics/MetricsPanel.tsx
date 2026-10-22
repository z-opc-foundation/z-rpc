import { useCallback, useEffect, useState } from 'react'
import { Card, Row, Col, Select } from 'antd'
import { EChartsChart } from '@yuku123/z-frontend-common/ui/echarts'
import { ErrorState, LoadingState } from '@yuku123/z-frontend-common/ui'
import dayjs from 'dayjs'
import { listServices, getServiceMetrics } from '../../api'
import type { MetricsPoint } from '../../types'

export default function MetricsPanel() {
  const [services, setServices] = useState<any[]>([])
  const [selected, setSelected] = useState<string | undefined>()
  const [metrics, setMetrics] = useState<Record<string, MetricsPoint[]>>({})
  const [loading, setLoading] = useState(true)
  const [metricsLoading, setMetricsLoading] = useState(false)
  const [error, setError] = useState<unknown>(null)

  const loadServices = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const list = await listServices()
      const arr = list || []
      setServices(arr)
      if (arr.length > 0) setSelected(arr[0].serviceKey)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  const loadMetrics = useCallback(async (serviceKey: string) => {
    setMetricsLoading(true)
    setError(null)
    try {
      const m = await getServiceMetrics(serviceKey)
      setMetrics(m || {})
    } catch (e) {
      setError(e)
    } finally {
      setMetricsLoading(false)
    }
  }, [])

  useEffect(() => {
    loadServices()
  }, [loadServices])

  useEffect(() => {
    if (selected) loadMetrics(selected)
  }, [selected, loadMetrics])

  const allPoints = Object.values(metrics).flat()

  const qpsOption = {
    tooltip: { trigger: 'axis' },
    title: { text: 'QPS 时序', left: 0 },
    xAxis: { type: 'category', data: allPoints.map((p) => dayjs(p.timestamp).format('HH:mm:ss')) },
    yAxis: { type: 'value', name: 'QPS' },
    series: [{ name: 'QPS', type: 'line', smooth: true, areaStyle: { opacity: 0.3 }, data: allPoints.map((p) => p.qps), itemStyle: { color: '#1677ff' } }]
  }
  const rtOption = {
    tooltip: { trigger: 'axis' },
    title: { text: '响应时间 (P50/P90/P99)', left: 0 },
    legend: { data: ['P50', 'P90', 'P99'] },
    xAxis: { type: 'category', data: allPoints.map((p) => dayjs(p.timestamp).format('HH:mm:ss')) },
    yAxis: { type: 'value', name: 'ms' },
    series: [
      { name: 'P50', type: 'line', smooth: true, data: allPoints.map((p) => p.p50), itemStyle: { color: '#52c41a' } },
      { name: 'P90', type: 'line', smooth: true, data: allPoints.map((p) => p.p90), itemStyle: { color: '#fa8c16' } },
      { name: 'P99', type: 'line', smooth: true, data: allPoints.map((p) => p.p99), itemStyle: { color: '#f5222d' } }
    ]
  }
  const errOption = {
    tooltip: { trigger: 'axis' },
    title: { text: '错误率', left: 0 },
    xAxis: { type: 'category', data: allPoints.map((p) => dayjs(p.timestamp).format('HH:mm:ss')) },
    yAxis: { type: 'value', name: '%', max: 1 },
    series: [{ name: '错误率', type: 'line', smooth: true, data: allPoints.map((p) => p.errorRate), areaStyle: { color: '#f5222d', opacity: 0.2 }, itemStyle: { color: '#f5222d' } }]
  }

  if (loading) {
    return <LoadingState variant="detail" rows={6} />
  }

  if (error) {
    return <ErrorState error={error} onRetry={() => (selected ? loadMetrics(selected) : loadServices())} variant="full" />
  }

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Select
          style={{ width: 360 }}
          placeholder="选择服务"
          value={selected}
          onChange={setSelected}
          options={services.map((s) => ({ value: s.serviceKey, label: s.serviceKey }))}
        />
      </Card>
      {metricsLoading ? (
        <LoadingState variant="detail" rows={6} />
      ) : (
        <Row gutter={[16, 16]}>
          <Col span={24}><Card><EChartsChart option={qpsOption} height={280} /></Card></Col>
          <Col span={24}><Card><EChartsChart option={rtOption} height={280} /></Card></Col>
          <Col span={24}><Card><EChartsChart option={errOption} height={280} /></Card></Col>
        </Row>
      )}
    </div>
  )
}
