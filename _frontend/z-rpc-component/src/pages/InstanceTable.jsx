import {useEffect, useState} from 'react'
import {Alert, Badge, Button, Card, Input, Table, Tag} from 'antd'
import {SearchOutlined} from '@ant-design/icons'
import {flattenInstances, parseClusterMap, rpcApi} from '../services/api'
import {EmptyState, PageHeader} from '@/common/components/ui'
import {useSearchParams} from 'react-router-dom'

export default function InstanceTable() {
    const [params] = useSearchParams()
    const [serviceName, setServiceName] = useState(params.get('serviceName') || '')
    const [rows, setRows] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)

    const load = async (name) => {
        if (!name) {
            setRows([])
            setError(null)
            return
        }
        setLoading(true)
        try {
            const services = await rpcApi.listServices()
            const hit = (services || []).find((s) => s.serviceName === name)
            if (!hit) {
                setRows([])
                setError(new Error(`注册表里没有服务 ${name}`))
                return
            }
            const cm = parseClusterMap(hit.clusterMap)
            if (cm === null) {
                setRows([])
                setError(new Error(`服务 ${name} 的 clusterMap 不是合法 JSON，无法解析实例`))
                return
            }
            setRows(flattenInstances(cm))
            setError(null)
        } catch (e) {
            setRows([])
            setError(e)
        } finally {
            setLoading(false)
        }
    }

    useEffect(() => {
        load(serviceName)
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [])

    return (
        <div>
            <PageHeader title="服务实例"
                        description="按服务名展开 clusterMap 里的实例（数据源 GET /api/naming/listServices → clusterMap）"/>
            <Card>
                <Input.Search
                    style={{marginBottom: 12, maxWidth: 420}}
                    placeholder="服务名（格式 group@@name）"
                    enterButton={<SearchOutlined/>}
                    value={serviceName}
                    loading={loading}
                    onChange={(e) => setServiceName(e.target.value)}
                    onSearch={(v) => load(v)}
                />
                {!serviceName ? (
                    <Alert type="info" showIcon message="请输入服务名后查询"
                           description="实例接口 getAllInstances 要求 serviceName，注册表为空时无可用服务。"/>
                ) : error ? (
                    <Alert type="error" showIcon message={`实例读取失败：${error.message}`}/>
                ) : (
                    <Table
                        size="small"
                        rowKey={(r) => `${r.ip}:${r.port}:${r.cluster}`}
                        loading={loading}
                        dataSource={rows}
                        columns={[
                            {title: 'IP', dataIndex: 'ip', key: 'ip', width: 150},
                            {title: '端口', dataIndex: 'port', key: 'port', width: 90},
                            {title: '集群', dataIndex: 'cluster', key: 'cluster', width: 140, render: (v) => <Tag>{v}</Tag>},
                            {
                                title: '健康', dataIndex: 'healthy', key: 'healthy', width: 100,
                                render: (v) => (v === false
                                    ? <Badge status="error" text="不健康"/>
                                    : <Badge status="success" text="健康"/>)
                            },
                            {title: '权重', dataIndex: 'weight', key: 'weight', width: 80},
                            {title: '临时实例', dataIndex: 'ephemeral', key: 'ephemeral', width: 100, render: (v) => (v ? '是' : '否')},
                            {title: '元数据', dataIndex: 'metadata', key: 'metadata', ellipsis: true, render: (v) => v ? JSON.stringify(v) : '-'},
                        ]}
                        locale={{emptyText: <EmptyState title="该服务暂无实例" description="clusterMap 解析后为空"/>}}
                    />
                )}
            </Card>
        </div>
    )
}
