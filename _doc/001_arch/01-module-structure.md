# Z-RPC 架构设计文档

> 详细解释 Z-RPC 的分层架构、核心组件与设计决策。

## 1. 分层架构

Z-RPC 严格遵循 Dubbo 的"API/SPI/Impl"三层解耦，每个模块都只依赖 API 模块，从不反向依赖 Impl。

```
┌─────────────────────────────────────────────────────────────┐
│                  z-rpc-examples (业务)                    │
│             user-service / order-service                  │
└─────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────┐
│              z-rpc-spring-boot-starter (集成)              │
│  @ZRpcService / @ZRpcReference / ZRpcAutoConfiguration    │
└─────────────────────────────────────────────────────────────┘
                            │
┌─────────────────────────────────────────────────────────────┐
│                    z-rpc-core (聚合)                       │
└─────────────────────────────────────────────────────────────┘
                            │
   ┌─────────┬─────────┬─────────┬─────────┬─────────┐
   │         │         │         │         │         │
   ▼         ▼         ▼         ▼         ▼         ▼
┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐
│ API │  │Cluster│ │Filter│ │Regis│ │Mock │  │Admi│
│     │  │     │  │     │  │try  │  │     │  │n   │
└─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘
   │         │         │         │         │
   └─────────┴─────────┴─────────┴─────────┘
                │
   ┌─────────────┴─────────────┐
   ▼                           ▼
┌──────────┐              ┌──────────┐
│Protocol │              │Remoting │
│+ Serialize│             │ (Netty)  │
└──────────┘              └──────────┘
            │                  │
            └────────┬─────────┘
                     ▼
              ┌──────────┐
              │ common+  │
              │   spi    │
              └──────────┘
```

## 2. 调用链（Consumer → Provider）

```
User Code
  ↓ proxy.invoke()
  ↓ Filter Chain (Monitor/Trace/Context/...)
  ↓ Cluster.join(directory).invoke(inv)
  ↓ Directory.list() → Router.route() → LoadBalance.select()
  ↓ DubboInvoker.invoke()
  ↓ NettyClient.send(ZRpcMessage)
  ↓ ZRpcMessageEncoder.encode()  ──→  24B header + body
  ↓ TCP
  ↓ ZRpcMessageDecoder.decode() ←──  24B header + body
  ↓ Provider Filter Chain
  ↓ Service Ref.method()  ──→  业务实现
  ↓ ZRpcMessage (Response)
  ↓ NettyClient receives
  ↓ DefaultFuture.received(requestId)
  ↓ Filter 后置
  ↓ proxy.recreate() → return value
```

## 3. 服务发现（基于 z-config）

```
Provider 启动:
  ServiceConfig.export()
    → new RpcServer(port)
    → start()
    → ZConfigRegistry.register(url)
    → z-config Naming.register("z-rpc/com.zifang.UserService/providers/...")

Consumer 启动:
  ReferenceConfig.refer()
    → ZConfigRegistry.subscribe(consumerUrl, listener)
    → listener.notify(providerUrls)
    → RegistryDirectory.refresh(urlInvokerMap)
    → Cluster.join(directory)

运行时:
  z-config Naming watcher 推送变化
    → listener.notify
    → Directory 动态刷新
    → 重新 Router + LB
```

## 4. SPI 微内核

参考 Dubbo 的 ExtensionLoader 实现：

```java
ExtensionLoader<Protocol> loader = ExtensionLoader.getExtensionLoader(Protocol.class);
Protocol protocol = loader.getExtension("z-rpc");        // 按名加载
Protocol default_ = loader.getDefaultExtension();        // 默认（@SPI 值）
List<Filter> filters = loader.getActivateExtension("consumer");  // 激活
```

**资源声明格式：**

`META-INF/z-rpc/com.zifang.z.rpc.api.Protocol`:
```
z-rpc=com.zifang.z.rpc.protocol.ZRpcProtocolImpl
```

**@SPI / @Adaptive / @Activate 用法：**

```java
@SPI("failover")                    // 默认实现 key
public interface Cluster { ... }

@Adaptive                          // 自适应（按 URL 参数选择）
public interface LoadBalance { ... }

@Activate(group = "consumer", order = 100)  // 自动激活
public class MonitorFilter implements Filter { ... }
```

## 5. Z-RPC 协议

24 字节定长头 + 可选 attachments + body：

```
┌────────────┬──────────────┬──────────────┬──────────────┐
│  Magic (4B) │  Ver |Type   │  SerID |Cmp  │  Status (2B)  │
│  0x5A525043│   4b | 4b   │   4b | 4b   │              │
├────────────┴──────────────┴──────────────┴──────────────┤
│            RequestId (8 bytes)                          │
├──────────────────────────────────────────────────────────┤
│            BodyLength (4 bytes)                         │
├────────────────────┬────────────────────────────────────┤
│  HeaderLength (2B)  │  Reserved (2B)                   │
├────────────────────┴────────────────────────────────────┤
│   Attachments (KV 序列，可选)                            │
│   ─────────────────────────────────────────            │
│   Body (序列化后)                                       │
└──────────────────────────────────────────────────────────┘
```

**关键字段：**
- `Magic = 0x5A525043` ("ZRPC" 大端)
- `Ver` 当前 `1`
- `Type` 1=Request, 2=Response, 3=HeartbeatReq, 4=HeartbeatRes
- `SerID` 1=Java, 2=JSON, 3=Hessian2, 4=Kryo, 5=Protobuf
- `Status` 0=OK, 1=Timeout, 2=ConnLost, 3=BizErr, 4=NoProvider
- `RequestId` 消费者/提供者双向匹配
- `BodyLength` 不含头

## 6. Filter 链

**Consumer（5 个内置）：**
```
ConsumerContextFilter (order=10)  // 设置调用上下文
  → TraceFilter (order=20)        // 注入 traceId
  → MonitorFilter (order=100)     // 上报指标
  → FutureFilter (order=110)      // 异步适配
  → TokenFilter (order=120)       // 透传用户 token
```

**Provider（5 个内置）：**
```
ProviderContextFilter (order=10)  // 还原上下文
  → TraceFilter (order=20)        // 提取 traceId
  → ProviderMonitorFilter (order=100)  // 服务端指标
  → TimeoutFilter (order=110)     // 超时检查
  → ExceptionFilter (order=120)   // 异常包装
```

## 7. 集群容错（6 种）

| Cluster         | 行为                       | 适用场景        |
|-----------------|--------------------------|-------------|
| `Failover`      | 失败重试（默认 2 次）                | 读操作等幂等场景  |
| `Failfast`      | 失败立即抛异常                    | 写操作等非幂等   |
| `Failsafe`      | 失败吞掉异常返回空                  | 日志/审计等不重要  |
| `Failback`      | 失败后异步重投                    | 通知类      |
| `Broadcast`     | 调用所有 provider                 | 缓存刷新/广播   |
| `Available`     | 选第一个可用的                    | 调试用      |

## 8. 负载均衡（5 种）

| LoadBalance              | 算法                          | 时间复杂度 |
|--------------------------|-----------------------------|--------|
| `Random`                 | 随机                          | O(1)   |
| `RoundRobin`             | 平滑加权轮询 (SmoothWeightedRR) | O(1)   |
| `LeastActive`            | 最少活跃 + 权重                   | O(n)   |
| `ConsistentHash`         | 一致性哈希 (参数 hash)            | O(log n) |
| `WeightedRoundRobin`     | 加权轮询（传统）                   | O(n)   |

## 9. 注册中心

| Registry       | 描述                  | 适用     |
|----------------|---------------------|--------|
| `z-config`     | zifang 自研的注册中心        | 推荐     |
| `zknaming`     | ZooKeeper 封装         | 传统场景   |
| `in-memory`    | 进程内 Map（仅供测试）      | 单进程测试  |

## 10. 与 z-config 的整合

z-config 的 `NamingService` 提供：

- `registerInstance(serviceName, instance)` — 注册 Provider
- `deregisterInstance(...)` — 注销
- `getAllInstances(serviceName)` — 拉取
- `subscribe(serviceName, listener)` — 订阅变更

`ZConfigRegistry` 把它包装为 RPC 注册中心接口。

## 11. 监控体系

**数据流：**

```
Consumer.invoke() ──→ MonitorFilter (AOP)
                          ↓
                    MetricsCollector.record()
                          ↓
                    Histogram (P50/P90/P99)
                          ↓
                    MetricsReporter (周期 10s)
                          ↓
                    HTTP POST /api/admin/metrics/push
                          ↓
                    z-rpc-admin
                          ↓
                    MetricsStorageService
                          ↓
                    GET /api/admin/services/{key}/metrics
                          ↓
                    React Dashboard ECharts
```

## 12. Admin 控制台

```
┌──────────────────────────────────────────────────────────┐
│                   Z-RPC Admin Console                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ 服务总数  │  │Provider │  │  总 QPS  │  │  健康度   │  │
│  │    12    │  │   28    │  │  15,234 │  │  99.9%   │  │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  │
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │   实时调用趋势 (QPS / P99 / 错误率)                 │ │
│  │   ╱╲    ╱╲  ╱─╲                                       │ │
│  │  ╱  ╲  ╱  ╲╱   ╲___                                   │ │
│  └────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

**前端技术栈：**
- React 18.3 + TypeScript 5.5
- Vite 5.4 (开发/构建)
- Ant Design 5.21 (UI)
- React Router 6 (路由)
- ECharts 5.5 (图表)
- Zustand (状态)
- Axios (HTTP)

**后端技术栈：**
- Spring Boot 2.7.18
- 端口 9090
- REST API + 内存存储（生产可换 Redis/H2）

## 13. 性能基准（参考）

| 场景               | QPS   | P99    | 备注      |
|------------------|------|-------|---------|
| 单连接同步调用         | 10k+ | < 10ms | Hessian2 |
| 4 字节简单对象        | 50k+ | < 5ms  | 紧凑数据   |
| 1KB 业务对象        | 20k+ | < 8ms  | 典型业务  |
| 10KB 大对象         | 5k+  | < 15ms | 大报文   |
| 多 Consumer 共享连接 | 30k+ | < 10ms | 长连接复用  |

> 注：以上为参考值，实际数据依赖业务场景、序列化器、JVM 调优等。
