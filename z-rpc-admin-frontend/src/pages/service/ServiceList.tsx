import { useEffect, useState } from 'react'
import { Table, Input, Tag, Space, Button, message, Spin } from 'antd'
import { SearchOutlined, ReloadOutlined, EyeOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { listServices } from '../../api'
import type { ServiceVO } from '../../types'

export default function ServiceList() {
  const [services, setServices] = useState<ServiceVO[]>([])
  const [loading, setLoading] = useState(true)
  const [keyword, setKeyword] = useState('')

  const load = async () => {
    setLoading(true)
    try {
      const data = await listServices(keyword)
      setServices(data || [])
    } catch (e: any) {
      message.error('加载服务列表失败: ' + (e?.message || ''))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const columns = [
    {
      title: '服务名',
      dataIndex: 'serviceKey',
      key: 'serviceKey',
      render: (text: string) => <Tag color="blue">{text}</Tag>
    },
    {
      title: 'Provider 数',
      dataIndex: 'providerCount',
      key: 'providerCount',
      sorter: (a: ServiceVO, b: ServiceVO) => a.providerCount - b.providerCount
    },
    {
      title: 'Consumer 数',
      dataIndex: 'consumerCount',
      key: 'consumerCount'
    },
    {
      title: 'QPS',
      key: 'qps',
      render: (_: any, r: ServiceVO) => (r.metrics?.qps || 0).toFixed(1)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, r: ServiceVO) => (
        <Space>
          <Link to={`/services/${encodeURIComponent(r.serviceKey)}`}>
            <Button type="link" icon={<EyeOutlined />}>详情</Button>
          </Link>
          <Link to={`/metrics/${encodeURIComponent(r.serviceKey)}`}>
            <Button type="link">监控</Button>
          </Link>
        </Space>
      )
    }
  ]

  return (
    <Spin spinning={loading}>
      <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
        <Space style={{ marginBottom: 16 }}>
          <Input
            placeholder="搜索服务名"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={load}
            allowClear
            style={{ width: 280 }}
          />
          <Button type="primary" onClick={load}>搜索</Button>
          <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
        </Space>
        <Table
          rowKey="serviceKey"
          columns={columns}
          dataSource={services}
          pagination={{ pageSize: 20 }}
          size="middle"
        />
      </div>
    </Spin>
  )
}
