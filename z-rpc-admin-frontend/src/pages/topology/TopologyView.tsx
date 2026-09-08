import { Card, Empty } from 'antd'

export default function TopologyView() {
  return (
    <Card title="服务拓扑">
      <Empty
        description={
          <div>
            <p>服务依赖图</p>
            <p style={{ fontSize: 12, color: '#999' }}>
              将基于 z-config 注册中心数据，自动构建服务调用关系图
            </p>
          </div>
        }
      />
    </Card>
  )
}
