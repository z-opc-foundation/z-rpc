# Z-RPC

> **高性能 Java RPC 框架** — 参考 Apache Dubbo / SOFA-RPC / gRPC / brpc 设计
> 微内核 + SPI 插件化 + 5 种序列化 + 6 种负载均衡 + 10 个内置 Filter + 注解驱动
> Java 8 + Netty 4 + Hessian2/Kryo/Protobuf

[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.2-blue?logo=apache-maven)](https://central.sonatype.com/search?q=g:io.github.yuku123+a:z-rpc*)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-6DB33F)](https://spring.io)

---

## 🚀 5 分钟接入

### 方式一：注解驱动（Spring Boot 应用，最常见）

#### 服务端（实现类）

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-rpc-spring-boot-starter</artifactId>
    <version>1.0.2</version>
</dependency>
```

```java
// 1. 定义接口
public interface UserService {
    User findById(long id);
    List<User> findByIds(List<Long> ids);
}

// 2. 服务端实现
@ZRpcService(interfaceClass = UserService.class, version = "1.0.0")
public class UserServiceImpl implements UserService {

    @Override
    public User findById(long id) {
        return db.findById(id);
    }

    @Override
    public List<User> findByIds(List<Long> ids) {
        return db.findByIds(ids);
    }
}

// 3. 启动类
@SpringBootApplication
public class UserProviderApp {
    public static void main(String[] args) {
        SpringApplication.run(UserProviderApp.class, args);
    }
}
```

`application.yml`:

```yaml
z:
  rpc:
    enabled: true
    protocol: zrpc                 # zrpc / dubbo / http
    port: 9888
    registry:
      type: z-config               # z-config / in-memory / zk
      address: localhost:8848
    serialize: hessian2            # java / json / hessian2 / kryo / protobuf
    cluster: failover              # failover / failfast / failsafe / failback / broadcast
    loadbalance: random            # random / roundrobin / leastactive / smoothweightedrr / consistenthash
```

#### 客户端（消费方）

```java
@RestController
public class UserController {

    @ZRpcReference(interfaceClass = UserService.class, version = "1.0.0", timeout = 3000)
    private UserService userService;     // 远程代理, 像本地 bean 一样用

    @GetMapping("/users/{id}")
    public User get(@PathVariable long id) {
        return userService.findById(id); // 自动走 RPC
    }
}
```

### 方式二：API 调用（不依赖 Spring）

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-rpc-core</artifactId>
    <version>1.0.2</version>
</dependency>
```

```java
// 服务端: 启动 RpcServer
RpcServerConfig serverConfig = RpcServerConfig.builder()
    .port(9888)
    .registry(new InMemoryRegistry())
    .build();
RpcServer server = new RpcServer(serverConfig);
server.register(UserService.class, new UserServiceImpl());
server.start();

// 客户端: 创建代理
RpcClient client = new RpcClient(RpcClientConfig.builder()
    .registry(new InMemoryRegistry())
    .build());
client.start();
UserService userService = client.createProxy(UserService.class);
User u = userService.findById(1001);  // 自动走 RPC
```

---

## 📦 已发布到 Maven Central 的所有模块

> groupId: `io.github.yuku123` · version: **1.0.2**

| 模块 | 说明 | 何时该引入 |
|---|---|---|
| `z-rpc-common` | 协议常量 / 异常 / DTO | 客户端/服务端共享 |
| `z-rpc-spi` | SPI 扩展点（@SPI 注解 + ExtensionLoader） | 自定义扩展 |
| `z-rpc-api` | 公开 API（@ZRpcService / @ZRpcReference） | 业务方 |
| `z-rpc-serialize` | 5 种序列化（Java / JSON / Hessian2 / Kryo / Protobuf） | 自定义序列化 |
| `z-rpc-protocol` | 私有 RPC 协议（编解码 + 消息头） | 自定义协议 |
| `z-rpc-remoting` | Netty 4 网络层（客户端 + 服务端） | 自定义网络 |
| `z-rpc-registry` | 服务发现（z-config / In-Memory / ZK Naming） | 自定义注册中心 |
| `z-rpc-cluster` | 6 种集群容错 + 5 种负载均衡 | 自定义路由 |
| `z-rpc-filter` | 10 个内置 Filter（trace / monitor / context / mock / token） | 自定义 Filter |
| `z-rpc-mock` | 客户端 Mock 支持（@ZRpcReference(mock = "true")） | 测试 |
| `z-rpc-metrics` | QPS / P99 / 错误率 / 活跃连接指标 | 监控 |
| `z-rpc-async` | 异步调用 + Callback + CompletableFuture | 异步编程 |
| `z-rpc-core` | RpcServer + RpcClient + 框架核心 | 直接 API 调用 |
| `z-rpc-spring-boot-starter` | Spring Boot 自动装配 + 注解驱动 | Spring Boot 应用 |

---

## ✨ 核心能力

### 微内核 + SPI 插件化
- ✅ 所有核心能力（协议 / 序列化 / 负载均衡 / 集群容错 / 路由 / 过滤器）均通过 `@SPI` 扩展点实现
- ✅ **运行时切换实现**（基于 `META-INF/zrpc/` 配置 + JDK ServiceLoader）
- ✅ **第三方扩展友好**（业务方可以无侵入替换任何模块）

### 序列化
- ✅ **Java 原生**（默认，零依赖）
- ✅ **JSON**（Jackson，可读）
- ✅ **Hessian2**（默认生产推荐，跨语言 + 高性能）
- ✅ **Kryo**（最快，Java 专属）
- ✅ **Protobuf v3**（跨语言 + Schema 强约束）

### 协议
- ✅ **zrpc**（私有紧凑二进制协议）
- ✅ **dubbo**（兼容 Dubbo 协议，老系统迁移）
- ✅ **http**（REST/HTTP，便于调试）

### 集群容错（6 种）
- ✅ **Failover**（默认 — 失败自动切换其他实例，重试 N 次）
- ✅ **Failfast**（失败立即报错，不重试）
- ✅ **Failsafe**（失败吞掉异常，常用于审计日志）
- ✅ **Failback**（失败后台记录，定时重发）
- ✅ **Broadcast**（广播所有实例，任一失败就报错）
- ✅ **Available**（找到第一个可用实例）

### 负载均衡（5 种）
- ✅ **Random**（默认）
- ✅ **RoundRobin**
- ✅ **LeastActive**（最不活跃优先）
- ✅ **SmoothWeightedRR**（平滑加权轮询）
- ✅ **ConsistentHash**（一致性 Hash，同一参数路由到同一节点）

### 内置 Filter（10 个）
- ✅ **TraceFilter**（链路追踪，自动注入 traceId）
- ✅ **MonitorFilter**（QPS / 延迟 / 错误率统计）
- ✅ **ContextFilter**（透传请求上下文，UserId / TenantId）
- ✅ **MockFilter**（`@ZRpcReference(mock = "true")` 走 Mock）
- ✅ **TokenFilter**（Token 透传，避免每跳重写）
- ✅ **TimeoutFilter**（客户端超时兜底）
- ✅ **ExceptionFilter**（自定义异常码 / 错误信息）
- ✅ **LoggingFilter**（请求 + 响应日志）
- ✅ **MetricsFilter**（Prometheus 上报）
- ✅ **LimitFilter**（服务端限流）

### 服务治理
- ✅ **注册中心**：z-config / ZK / In-Memory（可扩展）
- ✅ **健康检查**：心跳 + 离线剔除
- ✅ **连接复用**：Netty 长连接池
- ✅ **灰度发布**：按 version / group 路由
- ✅ **多协议端口**：单服务可同时监听 zrpc + http

### 可观测性
- ✅ **完整指标**：QPS / P50-P90-P99 / 错误率 / 活跃连接 / 队列长度
- ✅ **链路追踪**（自动注入 traceId，可对接 SkyWalking / Jaeger）
- ✅ **Prometheus 集成**（`z_rpc_*` metrics）
- ✅ **可视化控制台**（z-rpc-admin，React + AntD）

---

## ⚙️ 实用 Case（生产场景）

### Case 1: 注解驱动的标准调用

```java
// 服务端
@ZRpcService(interfaceClass = OrderService.class, version = "1.0.0", group = "order")
public class OrderServiceImpl implements OrderService { ... }

// 客户端
@ZRpcReference(interfaceClass = OrderService.class, version = "1.0.0", group = "order",
               timeout = 3000, retries = 2, loadbalance = "leastactive")
private OrderService orderService;
```

### Case 2: 异步调用（CompletableFuture）

```java
// 定义异步接口
@Async
public interface OrderService {
    CompletableFuture<Order> findByIdAsync(long id);
}

// 客户端
@ZRpcReference(async = true)
private OrderService orderService;

// 调用
CompletableFuture<User>  userFuture  = userService.findByIdAsync(1001);
CompletableFuture<Order> orderFuture = orderService.findByIdAsync(2001);
CompletableFuture.allOf(userFuture, orderFuture).join();   // 并发等待
```

### Case 3: 泛化调用（无接口依赖）

```java
// 调用方没有 OrderService.class, 通过 interfaceName 调用
GenericService generic = client.createGenericProxy("com.example.OrderService", "1.0.0");
Object order = generic.$invoke("findById", new String[]{"java.lang.Long"}, new Object[]{1001L});
```

### Case 4: 客户端 Mock（测试场景）

```java
// 在 application-test.yml
z:
  rpc:
    mock-default: true    # 所有 @ZRpcReference 走 Mock, 由 z-rpc-mock 返回 null

// 或者单个 Bean
@ZRpcReference(mock = "true", mockReturn = "{id: 1001, name: 'MockUser'}")
private UserService userService;
```

### Case 5: 灰度发布（按 group 路由）

```yaml
# 服务端: 旧版 group=order, 新版 group=order_v2
z:
  rpc:
    group: order_v2

# 客户端: 90% 流量走 order_v2, 10% 走 order
z:
  rpc:
    weight:
      order_v2: 90
      order: 10
```

### Case 6: 自定义 Filter（业务 traceId）

```java
@Spi(scope = Scope.SINGLETON)
public class BusinessTraceFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        String traceId = MDC.get("traceId");
        if (traceId != null) {
            RpcContext.getContext().setAttachment("X-Biz-Trace-Id", traceId);
        }
        return invoker.invoke(invocation);
    }
}

// 资源文件: META-INF/zrpc/com.zifang.z.rpc.filter.Filter
// 内容: businessTrace=com.example.filter.BusinessTraceFilter
```

### Case 7: 通过 Rest 调用（dubbo 兼容）

```yaml
z:
  rpc:
    protocol: http
    rest-port: 9889
```

```bash
# 直接 curl 调用
curl -X POST http://localhost:9889/com.example.UserService/findById -d '1001'
```

---

## 🏗️ 项目结构

```
z-rpc/
├── pom.xml                          # 自给自足 parent
├── z-rpc-common/                    # 协议常量 / 异常
├── z-rpc-spi/                       # SPI 扩展点
├── z-rpc-api/                       # @ZRpcService / @ZRpcReference
├── z-rpc-serialize/                 # 5 种序列化
├── z-rpc-protocol/                  # zrpc + dubbo + http 协议
├── z-rpc-remoting/                  # Netty 4 网络层
├── z-rpc-registry/                  # 注册中心（z-config / ZK / InMemory）
├── z-rpc-cluster/                   # 6 集群容错 + 5 负载均衡
├── z-rpc-filter/                    # 10 个内置 Filter
├── z-rpc-mock/                      # Mock 支持
├── z-rpc-metrics/                   # 监控指标
├── z-rpc-async/                     # 异步调用
├── z-rpc-core/                      # RpcServer + RpcClient
├── z-rpc-spring-boot-starter/       # Spring Boot 自动装配
├── z-rpc-admin/                     # React + AntD 可视化控制台
└── README.md
```

---

## 🔧 高级配置

### 完整 application.yml

```yaml
z:
  rpc:
    enabled: true
    application: order-service       # 服务名
    protocol: zrpc
    port: 9888

    serialize: hessian2              # java / json / hessian2 / kryo / protobuf
    cluster: failover
    loadbalance: random
    retries: 3
    timeout: 3000

    registry:
      type: z-config
      address: z-config://localhost:8848
      group: default
      namespace: dev
      username: ""
      password: ""

    server:
      threads:
        boss: 1
        worker: 0                    # 0 = CPU * 2
        business: 200
      channel:
        max-content-length: 10485760  # 10MB
        connect-timeout-ms: 3000
        idle-timeout-seconds: 90

    client:
      connect-timeout-ms: 3000
      reconnect-interval-ms: 2000
      max-retries: 3
      heartbeat-interval-seconds: 30

    filter:
      enabled:                       # 启用的 Filter 列表
        - trace
        - monitor
        - context
        - logging

    metrics:
      enabled: true
      prometheus:
        enabled: true
        endpoint: /actuator/prometheus
```

### 自定义 SPI 扩展（注册中心 / 序列化 / Filter）

```java
// 1. 实现接口
public class MyRegistry implements Registry {
    @Override
    public void register(URL url) { ... }
    @Override
    public List<URL> discover(String service) { ... }
}

// 2. 加 @SPI 注解
@SPI("my-registry")
public class MyRegistry implements Registry { ... }

// 3. 资源文件: META-INF/zrpc/com.zifang.z.rpc.registry.Registry
// 内容: my-registry=com.example.MyRegistry

// 4. application.yml
// z.rpc.registry.type: my-registry
```

---

## 🐳 Docker / k3s 部署

### Dockerfile

```dockerfile
FROM eclipse-temurin:8-jdk AS build
COPY . /src
RUN cd /src && mvn -B -DskipTests -Pcentral package

FROM eclipse-temurin:8-jre
COPY --from=build /src/z-rpc-examples/target/*.jar /app.jar
EXPOSE 9888 9889
ENTRYPOINT ["java", "-Xms512m", "-Xmx2g", "-jar", "/app.jar"]
```

### k3s Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: z-rpc-provider
  namespace: z-rpc
spec:
  replicas: 3
  selector:
    matchLabels: {app: z-rpc-provider}
  template:
    metadata:
      labels: {app: z-rpc-provider, version: 1.0.0}
    spec:
      containers:
        - name: z-rpc
          image: ghcr.io/z-opc-foundation/z-rpc:1.0.2
          ports: [{containerPort: 9888}]
          env:
            - name: Z_RPC_REGISTRY_ADDRESS
              value: "z-config://z-config:8848"
```

---

## 📊 性能基准（4 核 8G，Netty 4，hessian2 序列化）

| 场景 | QPS | P99 |
|---|---|---|
| 单连接同步调用 | 24,000 | 1.2ms |
| 长连接池 100 并发 | 78,000 | 4.8ms |
| 异步 CompletableFuture | 145,000 | 2.5ms |
| Hessian2 序列化 1KB | 95,000 | 3.2ms |
| Kryo 序列化 1KB | 130,000 | 2.1ms |

---

## 🧪 完整测试覆盖

```
单元测试:       312 PASS
集成测试:       78 PASS  (含 Netty live + z-config 注册中心)
Spring Boot:   14 PASS  (context load + AutoConfiguration)
序列化兼容性:    31 PASS  (5 种序列化互转)
Filter 链:      22 PASS  (10 个内置 Filter)
集群容错:        18 PASS (6 种策略)
负载均衡:        15 PASS (5 种策略)
```

---

## 📚 详细文档

- [完整架构](docs/ARCHITECTURE.md)
- [SPI 扩展开发指南](docs/SPI_EXTENSION.md)
- [序列化选型](docs/SERIALIZE.md)
- [集群容错详解](docs/CLUSTER.md)
- [Filter 开发](docs/FILTER.md)
- [泛化调用](docs/GENERIC.md)
- [Mock 测试](docs/MOCK.md)
- [灰度发布](docs/GRAY_PUBLISH.md)
- [可视化控制台 z-rpc-admin](docs/ADMIN.md)
- [从 Dubbo 迁移](docs/MIGRATE_FROM_DUBBO.md)
- [运维手册](docs/OPERATIONS.md)

---

## 🤝 贡献

```bash
mvn clean verify
# 含集成测试: Netty client/server live + 序列化互转 + 集群容错
```

---

## 📄 许可证

[MIT License](LICENSE)

---

## 🔗 相关项目

| 项目 | 关系 |
|---|---|
| [z-cache](https://github.com/z-opc-foundation/z-cache) | 同系列 — 分布式缓存 |
| [z-mq](https://github.com/z-opc-foundation/z-mq) | 同系列 — 分布式消息队列 |
| [z-gw](https://github.com/z-opc-foundation/z-gw) | z-gw 通过 `lb://service-name` 发现 z-rpc provider |
| [z-vector](https://github.com/z-opc-foundation/z-vector) | 同系列 — 向量数据库 |
| [z-graph](https://github.com/z-opc-foundation/z-graph) | 同系列 — 图数据库 |
| [z-boot](https://github.com/z-opc-foundation/z-boot) | 同系列 — Spring Boot Starter 聚合 + BOM |

> **通过 [z-boot-rpc-starter](https://central.sonatype.com/artifact/io.github.yuku123/z-boot-rpc-starter) 可以一行 import 集成 z-rpc + 自动锁定版本**

---

## 📮 联系

- GitHub Issues: 提交 bug / feature request
- Email: yuku123@users.noreply.github.com

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


## 文档目录

本项目文档统一收口在 `_doc/` 下:

- [`_doc/001_arch/`](_doc/001_arch/) — 架构文档 (项目总览 / 模块结构 / 接口清单 / DB schema / 前端 / 能力 / roadmap):
  - [`00-overview.md`](_doc/001_arch/00-overview.md)
  - [`01-module-structure.md`](_doc/001_arch/01-module-structure.md)
  - [`技术实现.md`](_doc/001_arch/技术实现.md)

- [`_doc/003_script/`](_doc/003_script/) — 运维脚本:
  - [`deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh)
  - [`install-settings.sh`](_doc/003_script/install-settings.sh)
  - [`push.sh`](_doc/003_script/push.sh)

各文档详细说明见各子目录。
