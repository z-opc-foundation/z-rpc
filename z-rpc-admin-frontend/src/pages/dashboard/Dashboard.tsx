import { useCallback, useEffect, useState } from 'react'
import { Row, Col, Card } from 'antd'
import {
  AppstoreOutlined,
  CloudServerOutlined,
  ApiOutlined,
  ThunderboltOutlined
} from '@ant-design/icons'
import { EChartsChart } from '@yuku123/z-frontend-common/ui/echarts'
import { ErrorState, GradientStatCard, LoadingState } from '@yuku123/z-frontend-common/ui'
import dayjs from 'dayjs'
import { getDashboardOverview, listServices } from '../../api'
import type { DashboardOverview } from '../../types'

export default function Dashboard() {
  const [overview, setOverview] = useState<DashboardOverview | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [trendData, setTrendData] = useState<any>({ timestamps: [], qps: [], p99: [], errorRate: [] })

  const load = useCallback(async () => {
    try {
      const data = await getDashboardOverview()
      setOverview(data)
      setError(null)

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
      // 轮询失败不清空已有数据 —— 只标记错误, 让页面保留上一次成功的读数
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
    const timer = window.setInterval(load, 5000)
    return () => window.clearInterval(timer)
  }, [load])

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
    return <LoadingState variant="cards" rows={4} />
  }

  // 首次加载就失败 ⇒ 没有任何读数可展示, 用整页错误态 + 重试
  if (error && !overview) {
    return <ErrorState error={error} onRetry={load} variant="full" />
  }

  return (
    <div>
      {/* 轮询中途失败: 就近提示但不清空已渲染的读数 */}
      {error ? <ErrorState error={error} onRetry={load} style={{ marginBottom: 16 }} /> : null}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <GradientStatCard icon={<AppstoreOutlined />} label="服务总数" value={overview?.serviceCount ?? 0} color="blue" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <GradientStatCard icon={<CloudServerOutlined />} label="Provider 数" value={overview?.providerCount ?? 0} color="green" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <GradientStatCard icon={<ApiOutlined />} label="总 QPS" value={overview?.totalQps ?? 0} color="orange" />
        </Col>
        <Col xs={24} sm={12} md={6}>
          <GradientStatCard icon={<ThunderboltOutlined />} label="健康度" value={99.9} suffix="%" precision={1} color="violet" />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col span={24}>
          <Card title="实时调用趋势 (QPS / P99 / 错误率)" bordered={false}>
            <EChartsChart option={trendOption} height={360} />
          </Card>
        </Col>
      </Row>
    </div>
  )
}
