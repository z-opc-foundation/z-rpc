import { useEffect, useState } from 'react'
import { Card, Row, Col, Select, Spin } from 'antd'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { listServices, getServiceMetrics } from '../../api'
import type { MetricsPoint } from '../../types'

export default function MetricsPanel() {
  const [services, setServices] = useState<any[]>([])
  const [selected, setSelected] = useState<string | undefined>()
  const [metrics, setMetrics] = useState<Record<string, MetricsPoint[]>>({})
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    listServices().then((data) => {
      const list = data || []
      setServices(list)
      if (list.length > 0) setSelected(list[0].serviceKey)
    })
  }, [])

  useEffect(() => {
    if (!selected) return
    setLoading(true)
    getServiceMetrics(selected).then((m) => {
      setMetrics(m || {})
    }).finally(() => setLoading(false))
  }, [selected])

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
      <Spin spinning={loading}>
        <Row gutter={[16, 16]}>
          <Col span={24}><Card><ReactECharts option={qpsOption} style={{ height: 280 }} /></Card></Col>
          <Col span={24}><Card><ReactECharts option={rtOption} style={{ height: 280 }} /></Card></Col>
          <Col span={24}><Card><ReactECharts option={errOption} style={{ height: 280 }} /></Card></Col>
        </Row>
      </Spin>
    </div>
  )
}
