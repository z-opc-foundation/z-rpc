/** 服务清单：/naming/listServices + 逐服务实例展开。 */
import {useEffect, useState} from 'react'
import {Alert, Button, Space, Table, Tag, Typography} from 'antd'
import {ReloadOutlined} from '@ant-design/icons'
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

            <Table rowKey="key" dataSource={rows} columns={columns} loading={loading && !rows.length}
                   size="small" pagination={{pageSize: 20}}
                   expandable={{
                       expandedRowRender: (r) => {
                           const cm = parseClusterMap(r.clusterMap)
                           const instances = cm ? flattenInstances(cm) : []
                           if (!instances.length) return <Text type="secondary">无实例或解析失败</Text>
                           return (
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
                   }}/>
        </div>
    )
}
