# Z-RPC

> 一款参考 Apache Dubbo / SOFA-RPC / gRPC / brpc 设计的 Java 高性能 RPC 框架，
> 集成 z-config 服务注册中心、z-util 工具包，提供完整的服务治理与可视化控制台。

![z-rpc-logo](https://img.shields.io/badge/z--rpc-1.0.0-blue)
![java](https://img.shields.io/badge/Java-8%2B-orange)
![maven](https://img.shields.io/badge/Maven-multi--module-brightgreen)
![license](https://img.shields.io/badge/License-Apache%202.0-blue)

---

## ✨ 核心特性

- 🧩 **微内核 + SPI 插件化** - 协议/序列化/负载均衡/集群/路由/过滤器 全部 SPI 可替换
- 🌐 **多种序列化** - Java / JSON / Hessian2 / Kryo / Protobuf 5 种实现
- 🔌 **多注册中心** - z-config（默认）/ In-Memory / ZK Naming
- ⚖️ **5 种负载均衡** - Random / RoundRobin / LeastActive / SmoothWeightedRR / ConsistentHash
- 🛡 **6 种集群容错** - Failover / Failfast / Failsafe / Failback / Broadcast / Available
- 🚦 **10 个内置 Filter** - 监控/Trace/上下文/Mock/Token
- 📊 **完整指标** - QPS / P50-P90-P99 / 错误率 / 活跃连接
- 🧰 **Mock 与泛化调用** - 配合 z-rpc-admin 动态 Mock
- 🎨 **可视化控制台** - React + Ant Design + Vite + ECharts
- 🔌 **Spring Boot 自动装配** - 注解驱动 (`@ZRpcService` / `@ZRpcReference`)

---

## 📁 项目结构

```
z-rpc/                                          # Maven 聚合（17 子模块）
├── z-rpc-common                                # 基础：URL/Node/Result/RpcException
├── z-rpc-spi                                   # SPI 微内核：ExtensionLoader + @SPI/@Adaptive
├── z-rpc-api                                   # 核心接口：Invoker/Protocol/Filter/ProxyFactory
├── z-rpc-serialize                             # 5 种序列化器
├── z-rpc-protocol                              # Z-RPC 私有二进制协议 (24 字节头)
├── z-rpc-remoting                              # Netty 4 + 连接池 + 心跳
├── z-rpc-registry                              # 注册中心：z-config / memory / zk
├── z-rpc-cluster                               # 集群/路由/负载均衡
├── z-rpc-filter                                # 内置 Filter 链
├── z-rpc-mock                                  # Mock + 泛化调用
├── z-rpc-metrics                               # 指标采集与上报
├── z-rpc-async                                 # DefaultFuture + RpcContext
├── z-rpc-core                                  # 聚合包（对外发布）
├── z-rpc-spring-boot-starter                   # Spring Boot 2.7 自动装配
├── z-rpc-examples                              # Demo
│   ├── user-service                            # 提供方：UserService
│   └── order-service                           # 消费方：OrderConsumer
├── z-rpc-admin                                 # 管理后台后端（端口 9090）
└── z-rpc-admin-frontend                        # 管理控制台前端（端口 5173）
```

---

## 🏛 整体架构

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot Application                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐    │
│  │ @ZRpcService    │  │ @ZRpcReference  │  │ @EnableZRpc     │    │
│  │ HelloServiceImpl│  │ UserService     │  │ scanBasePackages│    │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘    │
└───────────┼─────────────────────┼─────────────────────┼──────────────┘
            │                     │                     │
   ┌────────▼────────┐   ┌────────▼────────┐   ┌────────▼────────┐
   │ ServiceConfig   │   │ ReferenceConfig│   │ ZRpcAutoConfig  │
   │   + Export      │   │   + Refer      │   │   + SPI Load    │
   └────────┬────────┘   └────────┬────────┘   └────────┬────────┘
            │                     │                     │
            ▼                     ▼                     ▼
   ┌─────────────────────────────────────────────────────────────┐
   │            Filter Chain (Consumer / Provider)              │
   │  Trace → Context → Monitor → Mock → Future → Token        │
   └────────────────────────┬────────────────────────────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
   ┌────────────────┐  ┌────────────────┐  ┌────────────────┐
   │   Cluster      │  │   Directory    │  │  LoadBalance   │
   │ Failover/..    │  │  Registry/Static│  │ Random/RR/LAH  │
   └────────┬───────┘  └────────┬───────┘  └────────┬───────┘
            │                   │                   │
            └───────────────────┼───────────────────┘
                                ▼
   ┌─────────────────────────────────────────────────────────────┐
   │            Invoker (Provider 代理) + Connection Manager      │
   └────────────────────────┬────────────────────────────────────┘
                                │
            ┌───────────────────┴───────────────────┐
            ▼                                         ▼
   ┌────────────────────┐                ┌────────────────────┐
   │  z-rpc-registry    │                │  z-rpc-protocol    │
   │  z-config Naming   │                │  Z-RPC 24B header  │
   │  watch + push      │                │  5 种序列化器        │
   └────────────────────┘                │  Netty 长连接       │
                                          │  心跳 + 重连        │
                                          └────────────────────┘
```

### 协议格式 (Z-RPC Binary)

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Magic Number (0x5A525043 "ZRPC")         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|Ver| MsgType |SerType|Cmp|        Status (2 bytes)            |
| 4b|  4b   |  4b   |4b |                                    |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Request ID (8 bytes)                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Body Length (4 bytes)                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Header Length (2B) |   Reserved (2B)                        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     [Optional] Attachments (KV)         |      Body         |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

---

## 🚀 快速开始

### 环境要求

- JDK 8+
- Maven 3.6+
- 已部署 z-config (端口 8084) 【可选，未启动时降级为 InMemoryRegistry】
- Node 16+ (前端开发)

### 1. 启动注册中心 (可选)

```bash
# 如果已有 z-config 则跳过，否则本地起一个
git clone https://github.com/zifangsky/z-config.git
cd z-config && mvn spring-boot:run
```

### 2. 启动 Admin (可选)

```bash
cd z-rpc-admin
mvn spring-boot:run          # http://localhost:9090
```

### 3. 启动 Provider Demo

```bash
cd z-rpc-examples/user-service
mvn spring-boot:run          # 监听 20880
```

### 4. 启动 Consumer Demo

```bash
cd z-rpc-examples/order-service
mvn spring-boot:run          # 监听 20890
```

### 5. 测试调用

```bash
curl "http://localhost:20890/order/create?userId=1&amount=99.9"
curl "http://localhost:20890/order/user/1"
```

返回示例：

```json
{"id":1001,"userId":1,"userName":"Alice","amount":99.9,"status":"CREATED"}
```

### 6. 启动前端控制台 (可选)

```bash
cd z-rpc-admin-frontend
pnpm install      # 或 npm install
pnpm dev          # http://localhost:5173
```

---

## 📚 使用指南

### 注解驱动（推荐）

**Provider：**

```java
@ZRpcService(interfaceClass = UserService.class, version = "1.0.0", weight = 100)
@Service
public class UserServiceImpl implements UserService {
    public UserDTO getUser(Long id) {
        return userMap.get(id);
    }
}
```

**Consumer：**

```java
@Service
public class OrderConsumer {
    @ZRpcReference(version = "1.0.0", timeout = 3000, retries = 2, loadbalance = "random")
    private UserService userService;

    public UserDTO queryUser(Long id) {
        return userService.getUser(id);   // 像本地方法一样调用
    }
}
```

**配置：**

```yaml
z:
  rpc:
    enabled: true
    application:
      name: user-service
    registry:
      address: 127.0.0.1:8084
      type: z-config
    server:
      port: 20880
    consumer:
      timeout: 3000
      loadbalance: random
      cluster: failover
```

### 编程式 API

```java
ServiceConfig<UserService> service = new ServiceConfig<UserService>()
    .setInterface(UserService.class)
    .setRef(new UserServiceImpl())
    .setVersion("1.0.0")
    .setPort(20880)
    .setRegistry("127.0.0.1:8084");
service.export();

ReferenceConfig<UserService> ref = new ReferenceConfig<UserService>()
    .setInterfaceClass(UserService.class)
    .setVersion("1.0.0")
    .setRegistry("127.0.0.1:8084");
UserService proxy = ref.get();
proxy.getUser(1L);
```

### 自定义 Filter

```java
@Activate(group = "consumer", order = 50)
public class MyFilter implements Filter {
    @Override
    public Result invoke(Invoker<?> invoker, Invocation inv) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invoker.invoke(inv);
        } finally {
            System.out.println("RT=" + (System.currentTimeMillis() - start));
        }
    }
}
```

在 `META-INF/z-rpc/com.zifang.z.rpc.filter.Filter` 中声明：

```
my=com.zifang.demo.MyFilter
```

---

## 🔬 SPI 扩展点

| 接口             | 默认实现                  | 资源文件                                            |
|----------------|-----------------------|--------------------------------------------------|
| `Protocol`     | `ZRpcProtocolImpl`    | `META-INF/z-rpc/com.zifang.z.rpc.api.Protocol`  |
| `ProxyFactory` | `JdkProxyFactory`     | `META-INF/z-rpc/com.zifang.z.rpc.api.ProxyFactory` |
| `Cluster`      | `FailoverCluster` 等 5 | `META-INF/z-rpc/com.zifang.z.rpc.cluster.Cluster` |
| `LoadBalance`  | `Random` / `RR` / `LAH` | `META-INF/z-rpc/com.zifang.z.rpc.loadbalance.LoadBalance` |
| `Serialization`| 5 种                   | `META-INF/z-rpc/com.zifang.z.rpc.serialize.Serialization` |
| `RegistryService` | `ZConfig` / `InMemory` | `META-INF/z-rpc/com.zifang.z.rpc.registry.RegistryService` |
| `Filter`       | 10 个                  | `META-INF/z-rpc/com.zifang.z.rpc.filter.Filter`  |

---

## 📊 指标与监控

| 指标                  | 类型      | 位置              |
|---------------------|---------|-----------------|
| `qps_{svc}_{m}`     | 计数器     | MonitorFilter    |
| `rt_p50/p90/p99`    | 直方图     | Histogram        |
| `error_rate`        | 计数器     | MonitorFilter    |
| `active_connections`| Gauge   | ConnectionManager |
| `reconnect_count`   | 计数器     | HeartbeatReconnector |

**上报流程：** `Filter → MetricsCollector → MetricsReporter → HTTP POST → z-rpc-admin`

---

## 🛠 性能参考

- 单连接 QPS 10k+ @ P99 < 10ms (Hessian2 + Gzip)
- 连接池复用：多 Consumer 共享长连接
- 引用计数 ReferenceCountExchangeClient 模式（参考 Dubbo）
- 时间轮 HashedWheelTimer 替代 ScheduledExecutorService

---

## 🏗 路线图

- [x] 多模块 SPI 微内核
- [x] Z-RPC 二进制协议 + 5 种序列化
- [x] 6 种 Cluster + 5 种 LB
- [x] 10 个内置 Filter
- [x] Mock + 泛化调用
- [x] Spring Boot Starter
- [x] Admin 后端 + React 前端
- [ ] 连接池 + 心跳完整实现
- [ ] Triple 协议 (gRPC over HTTP/2)
- [ ] 服务网格 Sidecar
- [ ] 完整链路追踪 (类似 SkyWalking)

---

## 📖 参考项目

| 项目         | 版本    | 借鉴点                                |
|------------|-------|------------------------------------|
| Dubbo      | 3.3.6  | SPI 微内核 / 集群容错 / 路由 / Triple |
| SOFA-RPC   | 5.14.3 | 5 件套 / 4 层 Filter / 字节码生成     |
| gRPC-Java  | 1.83.1 | 拦截器 / xDS / Channelz            |
| brpc       | 1.17.0 | Server/Channel/Controller / bvar    |
| rpcx       | 1.9.4  | 4 层正交架构 / 消息协议                |

> 详细复刻分析参见：[/z-biz-creator/z-biz-learning-yuque-loc/yuque/开源研究/002_源码分析/](file:///Users/zifang/workplace/ceo_workplace/z-biz-creator/z-biz-learning-yuque-loc/yuque/开源研究/002_源码分析/)

---

## 🤝 贡献

欢迎 PR / Issue。

## 📄 许可证

[Apache License 2.0](LICENSE)
