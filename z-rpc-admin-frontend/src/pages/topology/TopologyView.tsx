import { Card } from 'antd'
import { EmptyState } from '@yuku123/z-frontend-common/ui'

export default function TopologyView() {
  return (
    <Card title="服务拓扑">
      <EmptyState
        title="服务依赖图"
        description="将基于 z-config 注册中心数据，自动构建服务调用关系图"
      />
    </Card>
  )
}
