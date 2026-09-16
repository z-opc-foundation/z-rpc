import { Card, Tabs, Form, Input, InputNumber, Button, Space, message } from 'antd'
import { useState } from 'react'

const { TextArea } = Input

export default function DynamicConfig() {
  const [mockRule, setMockRule] = useState('force:return Hello Mock')
  const [weight, setWeight] = useState(100)
  const [condition, setCondition] = useState('host = 10.* => 10.0.0.1')

  const onSave = (type: string) => {
    message.success(`${type} 已保存（演示）`)
  }

  return (
    <Card>
      <Tabs
        items={[
          {
            key: 'router',
            label: '路由规则',
            children: (
              <Form layout="vertical" style={{ maxWidth: 720 }}>
                <Form.Item label="条件路由 (host => instance)">
                  <Input value={condition} onChange={(e) => setCondition(e.target.value)} placeholder="host = 10.* => 10.0.0.1" />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button type="primary" onClick={() => onSave('路由规则')}>保存</Button>
                    <Button onClick={() => setCondition('')}>重置</Button>
                  </Space>
                </Form.Item>
              </Form>
            )
          },
          {
            key: 'mock',
            label: 'Mock 规则',
            children: (
              <Form layout="vertical" style={{ maxWidth: 720 }}>
                <Form.Item label="Mock 规则">
                  <TextArea
                    value={mockRule}
                    onChange={(e) => setMockRule(e.target.value)}
                    rows={4}
                    placeholder="force:return xxx 或 force:throw RuntimeException"
                  />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button type="primary" onClick={() => onSave('Mock 规则')}>保存</Button>
                    <Button onClick={() => setMockRule('')}>重置</Button>
                  </Space>
                </Form.Item>
              </Form>
            )
          },
          {
            key: 'weight',
            label: '权重调整',
            children: (
              <Form layout="vertical" style={{ maxWidth: 720 }}>
                <Form.Item label="Provider 权重 (0~200)">
                  <InputNumber min={0} max={200} value={weight} onChange={(v) => setWeight(v || 100)} />
                </Form.Item>
                <Form.Item>
                  <Space>
                    <Button type="primary" onClick={() => onSave('权重')}>应用</Button>
                    <Button onClick={() => setWeight(100)}>重置</Button>
                  </Space>
                </Form.Item>
              </Form>
            )
          }
        ]}
      />
    </Card>
  )
}
