/**
 * `@yuku123/z-frontend-common` 的本地类型声明。
 *
 * 为什么需要这个文件:
 *   基座包 1.1.2 发布物里只有 JS(无 .d.ts),`exports` 也没有 `types` 条件。
 *   本仓是 TypeScript 且 `strict: true`(⇒ noImplicitAny),直接 import 会报 TS7016
 *   "Could not find a declaration file for module ..."。
 *   已迁移的 z-ext / z-mist 都是 .jsx,不会撞上这个问题;本仓必须自己补声明。
 *
 * 声明内容以基座源码真源为准:
 *   /Users/zifang/workplace/ceo_workplace/z-opc-foundation/z-frontend-common/src/components/ui/*.jsx
 * 若基座改了 props,这里要跟着改 —— 否则类型会骗过编译器,静默吃掉真实不匹配。
 */
declare module '@yuku123/z-frontend-common/ui' {
  import type { CSSProperties, ReactNode } from 'react'
  import type { TagProps } from 'antd'

  /** 统一空状态: 图标 + 标题 + 描述 + 行动按钮 */
  export function EmptyState(props: {
    icon?: ReactNode
    title?: string
    description?: ReactNode
    actionText?: string
    onAction?: () => void
    size?: 'sm' | 'md' | 'lg'
    style?: CSSProperties
  }): ReactNode

  /** 统一错误状态: 原因 + 重试入口 */
  export function ErrorState(props: {
    error?: unknown
    title?: string
    description?: ReactNode
    onRetry?: () => void
    retryText?: string
    variant?: 'inline' | 'full'
    style?: CSSProperties
  }): ReactNode

  /** 统一骨架屏: cards / table / list / detail 四种预设 */
  export function LoadingState(props: {
    variant?: 'cards' | 'table' | 'list' | 'detail'
    rows?: number
    active?: boolean
    style?: CSSProperties
  }): ReactNode

  /** 防抖搜索框(300ms),Enter 立即触发,Esc 清空 */
  export function SearchInput(props: {
    value?: string
    onChange?: (value: string) => void
    onSearch?: (value: string) => void
    placeholder?: string
    debounceMs?: number
    width?: number
    allowClear?: boolean
    style?: CSSProperties
  }): ReactNode

  /** 表格上方工具条: left 过滤区 / right 操作区 / extra 附加 */
  export function TableToolbar(props: {
    left?: ReactNode
    right?: ReactNode
    extra?: ReactNode
  }): ReactNode

  /** 紧凑刷新按钮 */
  export function RefreshButton(props: {
    onClick?: () => void
    loading?: boolean
    title?: string
  }): ReactNode

  /** 渐变数据卡 */
  export function GradientStatCard(props: {
    icon?: ReactNode
    label?: string
    value?: number
    suffix?: string
    color?: 'pink' | 'blue' | 'green' | 'orange' | 'violet' | 'cyan' | 'red' | 'yellow'
    trend?: number | string
    href?: string
    loading?: boolean
    precision?: number
  }): ReactNode

  /** 通用状态徽标 */
  export function StatusBadge(props: {
    status?: string | number
    type?: string
    mapping?: Record<string | number, { label: string; color?: string }>
  } & Omit<TagProps, 'children'>): ReactNode
}

declare module '@yuku123/z-frontend-common/ui/echarts' {
  import type { CSSProperties } from 'react'

  /** echarts 安全包装: 自动 init/dispose + ResizeObserver 自适应 */
  export function EChartsChart(props: {
    option?: unknown
    height?: number | string
    className?: string
    style?: CSSProperties
  }): ReactNode
}
