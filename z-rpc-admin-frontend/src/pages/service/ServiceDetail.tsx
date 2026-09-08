import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Card, Descriptions, Table, Tag, Spin, Button, Space, Tabs } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { getServiceDetail, getServiceMetrics } from '../../api'
import type { ProviderVO, MetricsPoint } from '../../types'
import ReactECharts from 'echarts-for-react'
import dayjs from 'dayjs'

export default function ServiceDetail() {
  const { serviceKey } = useParams<{ serviceKey: string }>()
  const decoded = serviceKey ? decodeURIComponent(serviceKey) : ''
  const [providers, setProviders] = useState<ProviderVO[]>([])
  const [metrics, setMetrics] = useState<Record<string, MetricsPoint[]>>({})
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    try {
      const detail = await getServiceDetail(decoded)
      setProviders(detail.providers || [])
      const m = await getServiceMetrics(decoded)
      setMetrics(m || {})
    } catch (e) {
      console.error(e)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (decoded) load()
  }, [decoded])

  const providerColumns = [
    { title: '实例 ID', dataIndex: 'id', key: 'id' },
    { title: '地址', dataIndex: 'address', key: 'address', render: (a: string) => <Tag color="geekblue">{a}</Tag> },
    { title: '版本', dataIndex: 'version', key: 'version' },
    { title: '分组', dataIndex: 'group', key: 'group' },
    { title: '权重', dataIndex: 'weight', key: 'weight' },
    { title: '健康', dataIndex: 'healthy', key: 'healthy', render: (h: boolean) => h ? <Tag color="green">健康</Tag> : <Tag color="red">异常</Tag> }
  ]

  const allPoints = Object.entries(metrics).flatMap(([k, v]) => v.map((p) => ({ ...p, key: k })))
  const chartOption = {
    tooltip: { trigger: 'axis' },
    legend: { data: ['P50', 'P90', 'P99'] },
    xAxis: { type: 'category', data: allPoints.map((p) => dayjs(p.timestamp).format('HH:mm:ss')) },
    yAxis: { type: 'value', name: 'ms' },
    series: [
      { name: 'P50', type: 'line', smooth: true, data: allPoints.map((p) => p.p50), itemStyle: { color: '#52c41a' } },
      { name: 'P90', type: 'line', smooth: true, data: allPoints.map((p) => p.p90), itemStyle: { color: '#fa8c16' } },
      { name: 'P99', type: 'line', smooth: true, data: allPoints.map((p) => p.p99), itemStyle: { color: '#f5222d' } }
    ]
  }

  return (
    <Spin spinning={loading}>
      <Space direction="vertical" style={{ width: '100%' }} size={16}>
        <Card>
          <Space>
            <Link to="/services"><Button icon={<ArrowLeftOutlined />}>返回</Button></Link>
            <h2 style={{ margin: 0 }}>服务详情：{decoded}</h2>
          </Space>
        </Card>

        <Tabs
          items={[
            {
              key: 'providers',
              label: 'Provider 列表',
              children: (
                <Card>
                  <Table
                    rowKey="id"
                    columns={providerColumns}
                    dataSource={providers}
                    pagination={false}
                    size="middle"
                  />
                </Card>
              )
            },
            {
              key: 'metrics',
              label: '性能监控',
              children: (
                <Card title="响应时间分布 (P50/P90/P99)">
                  <ReactECharts option={chartOption} style={{ height: 360 }} />
                </Card>
              )
            }
          ]}
        />
      </Space>
    </Spin>
  )
}
