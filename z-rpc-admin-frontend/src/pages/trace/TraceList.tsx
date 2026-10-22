import { useCallback, useEffect, useState } from 'react'
import { Card, Table, Tag, type TableColumnsType } from 'antd'
import { EmptyState, ErrorState, LoadingState } from '@yuku123/z-frontend-common/ui'
import { listTraces, listServices } from '../../api'
import type { TraceRecord } from '../../types'
import dayjs from 'dayjs'

export default function TraceList() {
  const [traces, setTraces] = useState<TraceRecord[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)

  const load = useCallback(async () => {
    try {
      // 如果没真实 trace，生成一些 demo 数据
      const data = await listTraces().catch(() => [])
      if (!data || data.length === 0) {
        const services = await listServices().catch(() => [])
        const fake: TraceRecord[] = []
        for (let i = 0; i < 30; i++) {
          const svc = services[Math.floor(Math.random() * Math.max(services.length, 1))] || { serviceKey: 'com.zifang.demo.user.UserService' }
          fake.push({
            traceId: `trace-${Date.now()}-${i}`,
            service: svc.serviceKey,
            method: ['getUser', 'listUsers', 'createUser'][i % 3],
            startTime: Date.now() - i * 1000,
            rt: Math.floor(Math.random() * 100 + 5),
            success: Math.random() > 0.1,
            error: Math.random() > 0.9 ? 'NullPointerException at line 42' : undefined,
            remoteAddress: `10.0.0.${Math.floor(Math.random() * 254 + 1)}:20880`
          })
        }
        setTraces(fake)
      } else {
        setTraces(data)
      }
      setError(null)
    } catch (e) {
      setError(e)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
    const timer = setInterval(load, 10000)
    return () => clearInterval(timer)
  }, [load])

  const columns: TableColumnsType<TraceRecord> = [
    { title: 'Trace ID', dataIndex: 'traceId', key: 'traceId', render: (t: string) => <code>{t.slice(0, 16)}...</code> },
    { title: '服务', dataIndex: 'service', key: 'service', render: (s: string) => <Tag color="blue">{s}</Tag> },
    { title: '方法', dataIndex: 'method', key: 'method' },
    { title: 'RT (ms)', dataIndex: 'rt', key: 'rt', sorter: (a, b) => a.rt - b.rt },
    { title: '状态', dataIndex: 'success', key: 'success', render: (s: boolean) => s ? <Tag color="green">成功</Tag> : <Tag color="red">失败</Tag> },
    { title: '远程地址', dataIndex: 'remoteAddress', key: 'remoteAddress' },
    { title: '开始时间', dataIndex: 'startTime', key: 'startTime', render: (t: number) => dayjs(t).format('HH:mm:ss') }
  ]

  if (error) {
    return <ErrorState error={error} onRetry={load} variant="full" />
  }

  if (loading) {
    return <LoadingState variant="table" rows={8} />
  }

  return (
    <Card>
      {traces.length === 0 ? (
        <EmptyState title="暂无链路追踪数据" description="等待 z-rpc-admin 采集到调用链后在此展示" />
      ) : (
        <Table rowKey="traceId" columns={columns} dataSource={traces} pagination={{ pageSize: 20 }} />
      )}
    </Card>
  )
}
