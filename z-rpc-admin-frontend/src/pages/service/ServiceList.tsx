import { useCallback, useEffect, useRef, useState } from 'react'
import { Table, Tag, Space, Button, type TableColumnsType } from 'antd'
import { EyeOutlined } from '@ant-design/icons'
import { Link } from 'react-router-dom'
import { EmptyState, ErrorState, LoadingState, RefreshButton, SearchInput, TableToolbar } from '@yuku123/z-frontend-common/ui'
import { listServices } from '../../api'
import type { ServiceVO } from '../../types'

export default function ServiceList() {
  const [services, setServices] = useState<ServiceVO[]>([])
  const [loading, setLoading] = useState(true)
  const [keyword, setKeyword] = useState('')
  const [error, setError] = useState<unknown>(null)

  // 记住最近一次真正发起的查询: 共享 SearchInput 在 Enter 上会同时经 onPressEnter 与
  // onKeyDown 触发两次 onSearch, 不去重就会打两次重复请求。手动刷新走 force 绕过该去重。
  const lastRequestedRef = useRef<string | null>(null)

  const load = useCallback(async (kw: string, force = false) => {
    if (!force && lastRequestedRef.current === kw) return
    lastRequestedRef.current = kw
    setLoading(true)
    setError(null)
    try {
      const data = await listServices(kw)
      setServices(data || [])
    } catch (e) {
      // 加载失败就近展示错误态并给出重试入口, 不再只弹一条顶部 toast
      setError(e)
      setServices([])
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load('')
  }, [load])

  const columns: TableColumnsType<ServiceVO> = [
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
      sorter: (a, b) => a.providerCount - b.providerCount
    },
    {
      title: 'Consumer 数',
      dataIndex: 'consumerCount',
      key: 'consumerCount'
    },
    {
      title: 'QPS',
      key: 'qps',
      render: (_: unknown, r) => (r.metrics?.qps || 0).toFixed(1)
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, r) => (
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
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <TableToolbar
        left={
          <SearchInput
            placeholder="搜索服务名"
            value={keyword}
            onChange={setKeyword}
            onSearch={(v) => {
              setKeyword(v)
              load(v)
            }}
          />
        }
        right={<RefreshButton onClick={() => load(keyword, true)} loading={loading} />}
      />

      {error ? (
        <ErrorState error={error} onRetry={() => load(keyword, true)} />
      ) : loading ? (
        <LoadingState variant="table" rows={6} />
      ) : (
        <Table
          rowKey="serviceKey"
          columns={columns}
          dataSource={services}
          // 分页按 lead 008 §2 规则 2 补齐四件套（原来只有 pageSize: 20）。
          // 这页的过滤是**服务端过滤**（SearchInput → listServices(keyword) 走后端查），
          // 所以不接基座 ListTable 的 searchKeys —— 否则页面上会出现两个搜索框。
          pagination={{
            pageSize: 20,
            showSizeChanger: true,
            pageSizeOptions: ['10', '20', '50', '100'],
            showTotal: (total) => `共 ${total} 条`,
          }}
          size="middle"
          locale={{
            emptyText: (
              <EmptyState
                title="暂无服务"
                description="当前关键字下没有已注册的服务, 可换个关键字或刷新重试"
              />
            )
          }}
        />
      )}
    </div>
  )
}
