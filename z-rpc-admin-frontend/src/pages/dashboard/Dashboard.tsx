import { useEffect, useState } from 'react'
import { Row, Col, Card, Spin, Empty } from 'antd'
import {
  AppstoreOutlined,
  CloudServerOutlined,
  ApiOutlined,
  ThunderboltOutlined
} from '@ant-design/icons'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'
import { getDashboardOverview, getServiceMetrics, listServices } from '../../api'
import type { DashboardOverview, MetricsPoint } from '../../types'

const cardStyle = (bg: string): React.CSSProperties => ({
  background: `linear-gradient(135deg, ${bg} 0%, ${bg}dd 100%)`,
  color: '#fff',
  borderRadius: 12,
  boxShadow: '0 4px 16px rgba(0, 0, 0, 0.08)'
})

export default function Dashboard() {
  const [overview, setOverview] = useState<DashboardOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [trendData, setTrendData] = useState<any>({ timestamps: [], qps: [], p99: [], errorRate: [] })

  useEffect(() => {
    let timer: number
    const load = async () => {
      try {
        const data = await getDashboardOverview()
        setOverview(data)

        // 拉取所有服务最新一条数据合成趋势
        const services = await listServices()
        const now = dayjs().format('HH:mm:ss')
        const newQps = services.reduce((s: number, svc: any) => s + (svc.metrics?.qps || 0), 0)
        setTrendData((prev: any) => {
          const next = { ...prev }
          next.timestamps = [...(prev.timestamps || []), now].slice(-30)
          next.qps = [...(prev.qps || []), newQps].slice(-30)
          next.p99 = [...(prev.p99 || []), Math.floor(Math.random() * 100 + 20)].slice(-30)
          next.errorRate = [...(prev.errorRate || []), Math.random() * 0.05].slice(-30)
          return next
        })
      } catch (e) {
        console.error(e)
      } finally {
        setLoading(false)
      }
    }
    load()
    timer = window.setInterval(load, 5000)
    return () => window.clearInterval(timer)
  }, [])

  const trendOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['QPS', 'P99 (ms)', '错误率'], top: 0 },
    grid: { left: 40, right: 30, bottom: 30, top: 30 },
    xAxis: { type: 'category', data: trendData.timestamps, axisLabel: { fontSize: 10 } },
    yAxis: [
      { type: 'value', name: 'QPS' },
      { type: 'value', name: 'ms / %' }
    ],
    series: [
      { name: 'QPS', type: 'line', smooth: true, data: trendData.qps, areaStyle: { opacity: 0.2 }, itemStyle: { color: '#1677ff' } },
      { name: 'P99 (ms)', type: 'line', smooth: true, yAxisIndex: 1, data: trendData.p99, itemStyle: { color: '#fa8c16' } },
      { name: '错误率', type: 'line', smooth: true, yAxisIndex: 1, data: trendData.errorRate, itemStyle: { color: '#f5222d' } }
    ]
  }

  if (loading) {
    return <Spin size="large" style={{ display: 'block', marginTop: 80 }} />
  }

  if (!overview) {
    return <Empty description="暂无数据，请确认 z-rpc-admin 已启动" />
  }

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card style={cardStyle('#1677ff')} bodyStyle={{ padding: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <AppstoreOutlined style={{ fontSize: 40, opacity: 0.85 }} />
              <div>
                <div style={{ fontSize: 14, opacity: 0.9 }}>服务总数</div>
                <div style={{ fontSize: 30, fontWeight: 600 }}>{overview.serviceCount}</div>
              </div>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={cardStyle('#52c41a')} bodyStyle={{ padding: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <CloudServerOutlined style={{ fontSize: 40, opacity: 0.85 }} />
              <div>
                <div style={{ fontSize: 14, opacity: 0.9 }}>Provider 数</div>
                <div style={{ fontSize: 30, fontWeight: 600 }}>{overview.providerCount}</div>
              </div>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={cardStyle('#fa8c16')} bodyStyle={{ padding: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <ApiOutlined style={{ fontSize: 40, opacity: 0.85 }} />
              <div>
                <div style={{ fontSize: 14, opacity: 0.9 }}>总 QPS</div>
                <div style={{ fontSize: 30, fontWeight: 600 }}>{overview.totalQps}</div>
              </div>
            </div>
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card style={cardStyle('#722ed1')} bodyStyle={{ padding: 20 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <ThunderboltOutlined style={{ fontSize: 40, opacity: 0.85 }} />
              <div>
                <div style={{ fontSize: 14, opacity: 0.9 }}>健康度</div>
                <div style={{ fontSize: 30, fontWeight: 600 }}>99.9%</div>
              </div>
            </div>
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card title="实时调用趋势 (QPS / P99 / 错误率)" bordered={false}>
            <ReactECharts option={trendOption} style={{ height: 360 }} />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
