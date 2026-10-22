/** 服务清单：/naming/listServices + 逐服务实例展开。 */
import {useEffect, useState} from 'react'
import {Alert, Button, Space, Table, Tag, Typography} from 'antd'
import {ReloadOutlined} from '@ant-design/icons'
import {ListTable} from '@yuku123/z-frontend-common/ui'
import {flattenInstances, parseClusterMap, rpcApi} from '../services/api'

const {Title, Paragraph, Text} = Typography

export default function ServiceTable() {
    const [rows, setRows] = useState([])
    const [loading, setLoading] = useState(false)
    const [error, setError] = useState(null)
    const [expanded, setExpanded] = useState({})

    const fetch = async () => {
        setLoading(true)
        try {
            const services = await rpcApi.listServices()
            const enriched = (services || []).map((s, i) => ({
                key: s.serviceName || i,
                serviceName: s.serviceName || s.name,
                groupName: s.groupName || 'DEFAULT_GROUP',
                clusterMap: s.clusterMap,
                instanceCount: (() => {
                    const cm = parseClusterMap(s.clusterMap)
                    if (!cm) return -1
                    return flattenInstances(cm).length
                })(),
            }))
            setRows(enriched)
            setError(null)
        } catch (e) {
            setError(e?.message || String(e))
        } finally { setLoading(false) }
    }

    useEffect(() => { fetch() }, [])

    const columns = [
        {title: '服务名', dataIndex: 'serviceName', key: 'serviceName', width: 280,
            render: (v) => <Text code style={{fontSize: 12}}>{v}</Text>},
        {title: '分组', dataIndex: 'groupName', key: 'groupName', width: 160},
        {title: '实例数', dataIndex: 'instanceCount', key: 'count', width: 100,
            render: (v) => v < 0 ? <Tag color="error">clusterMap 解析失败</Tag> : v},
    ]

    return (
        <div>
            <Space style={{marginBottom: 16}}>
                <Title level={4} style={{margin: 0}}>服务注册表</Title>
                <Button icon={<ReloadOutlined/>} onClick={fetch} loading={loading}>刷新</Button>
            </Space>
            <Paragraph type="secondary">/naming/listServices 全量服务 + clusterMap 展开实例。</Paragraph>

            {error && <Alert type="error" showIcon style={{marginBottom: 16}} message="后端未连接" description={error}/>}

            {/* 服务注册表 —— 走基座 ListTable（lead 008 §2 规则 2/3/4 三件套：
                分页四件套 + 端侧过滤框 + EmptyState）。
                【分页为什么这么写】原来只有 pagination={{pageSize: 20}}，
                缺 showSizeChanger / pageSizeOptions / showTotal 三项 ⇒ 按 §2 规则 2 补齐，
                pageSize 保留原来的 20（不顺手改成基座默认的 10）。 */}
            <ListTable
                rows={rows}
                rowKey="key"
                columns={columns}
                loading={loading && !rows.length}
                size="small"
                searchKeys="serviceName,groupName"
                searchPlaceholder="按服务名 / 分组过滤"
                emptyTitle="暂无已注册服务"
                emptyDescription="/naming/listServices 返回了空列表 —— 服务还没注册上来。"
                pagination={{
                    pageSize: 20,
                    showSizeChanger: true,
                    pageSizeOptions: ['10', '20', '50', '100'],
                    showTotal: (total) => `共 ${total} 条`,
                }}
                expandable={{
                    expandedRowRender: (r) => {
                        const cm = parseClusterMap(r.clusterMap)
                        const instances = cm ? flattenInstances(cm) : []
                        if (!instances.length) return <Text type="secondary">无实例或解析失败</Text>
                        return (
                            // 实例明细子表 —— pagination={false} 豁免说明：这是**父行（服务）展开后
                            // 才出现的子表**（父子行可展开的树形表），行数由该服务的实例数决定；
                            // 加分页条会把「父行 + 展开行」拆到两页，反而看不出实例属于哪个服务。
                            <Table rowKey="key" dataSource={instances} size="small" pagination={false}
                                   columns={[
                                       {title: '集群', dataIndex: 'cluster', width: 120},
                                       {title: '地址', key: 'addr',
                                           render: (_, i) => <Text code>{i.host}:{i.port}</Text>},
                                       {title: '健康', dataIndex: 'healthy', width: 80,
                                           render: (v) => v ? <Tag color="green">健康</Tag> : <Tag color="red">不健康</Tag>},
                                       {title: '权重', dataIndex: 'weight', width: 80},
                                   ]}/>
                        )
                    },
                }}
            />
        </div>
    )
}
