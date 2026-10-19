# z-rpc 框架技术实现文档

## 1. 整体架构

z-rpc 是一个基于 Java 实现的分布式 RPC 框架，参考 Dubbo 设计思想，采用分层架构模式，实现了服务注册与发现、负载均衡、集群容错等核心功能。

```
┌─────────────────────────────────────────────────────────────┐
│                     服务消费者 (Consumer)                     │
├─────────────────────────────────────────────────────────────┤
│  代理层 (Proxy)  →  集群层 (Cluster)  →  调用层 (Invoke)      │
│     ↑                    ↑                    ↑            │
│  JDK动态代理          负载均衡/容错             请求调用        │
├─────────────────────────────────────────────────────────────┤
│  注册中心 (Registry)  ←  服务发现 & 服务注册                    │
│  (基于 Z-Config 实现)                                       │
├─────────────────────────────────────────────────────────────┤
│  远程通信 (Remoting)  -  Netty 网络通信层                      │
│  编码/解码  →  网络传输  →  服务端处理                         │
├─────────────────────────────────────────────────────────────┤
│                     服务提供者 (Provider)                     │
│  服务导出  →  请求处理  →  方法调用  →  结果返回               │
└─────────────────────────────────────────────────────────────┘
```

## 2. 模块划分

### 2.1 z-rpc-core 核心模块

| 包名            | 功能说明 | 核心类                                          |
|---------------|------|----------------------------------------------|
| `annotation`  | 注解定义 | `@ZRpcService`, `@ZRpcReference`             |
| `cluster`     | 集群管理 | `RegistryDirectory`, `FailoverCluster`       |
| `common`      | 公共类  | `URL`                                        |
| `config`      | 配置管理 | `ServiceConfig`, `ReferenceConfig`           |
| `invoke`      | 调用抽象 | `Invocation`, `Invoker`, `Result`            |
| `loadbalance` | 负载均衡 | `RandomLoadBalance`, `RoundRobinLoadBalance` |
| `registry`    | 注册中心 | `RegistryService`, `ZConfigRegistry`         |
| `remoting`    | 远程通信 | `RpcClient`, `RpcServer`, `RpcRequest`       |
| `proxy`       | 代理层  | `ProxyFactory`                               |

### 2.2 z-rpc-spring-boot-starter 模块

| 类名                      | 功能               |
|-------------------------|------------------|
| `ZRpcAutoConfiguration` | Spring Boot 自动配置 |
| `ZRpcProperties`        | 配置属性绑定           |

## 3. 核心流程详解

### 3.1 服务导出流程 (Provider)

```
ServiceConfig.export()
    ├── checkConfig()          # 检查接口类、实现类配置
    ├── buildServiceUrl()      # 构建服务 URL
    ├── startRpcServer()       # 启动 RPC 服务器
    │   ├── new RpcServer(port)
    │   ├── rpcServer.registerService(interface, impl)
    │   └── 异步启动 Netty 服务器
    └── registerToRegistry()   # 注册到注册中心
        └── ZConfigRegistry.register(url)
            └── namingService.registerInstance(...)
```

### 3.2 服务引用流程 (Consumer)

```
ReferenceConfig.get()
    ├── checkConfig()              # 检查接口类配置
    ├── connectRegistry()          # 连接注册中心
    ├── createProxy()              # 创建服务代理
    │   ├── createDirectory()      # 创建 RegistryDirectory
    │   │   └── registryService.subscribe(url, listener)
    │   ├── cluster.join(directory) # 创建集群 Invoker
    │   │   └── FailoverClusterInvoker
    │   └── proxyFactory.getProxy() # 创建 JDK 动态代理
    │       └── Proxy.newProxyInstance(...)
    └── return proxy
```

### 3.3 远程调用流程

```
// 消费者侧
proxy.sayHello("world")
    └── InvokerInvocationHandler.invoke()
        └── clusterInvoker.invoke(invocation)
            └── FailoverClusterInvoker.doInvoke()
                ├── list(invocation) -> 获取 invokers
                ├── select(invokers) -> 负载均衡选择
                └── invoker.invoke(invocation)
                    └── DubboInvoker.invoke()
                        └── rpcClient.sendRequest(request)
                            └── Netty 网络发送

// 提供者侧
Netty Server 接收请求
    └── RpcServerHandler.channelRead()
        └── 解码 RpcRequest
        └── 查找服务实现
        └── 反射调用方法
        └── 编码 RpcResponse
        └── 返回结果
```

## 4. 核心组件详解

### 4.1 URL 类 - 统一资源定位符

```java
public class URL implements Serializable {
    private String protocol;      // 协议: z-rpc
    private String host;          // 主机地址
    private int port;             // 端口
    private String serviceInterface;  // 服务接口名
    private String group;         // 服务分组
    private String version;       // 服务版本
    private Map<String, String> parameters;  // 附加参数
    private int weight;           // 权重
}
```

**URL 格式**: `z-rpc://192.168.1.100:20880/com.example.HelloService?version=1.0.0&weight=100`

### 4.2 注册中心 - ZConfigRegistry

基于 Z-Config 服务发现框架实现，提供服务注册与发现能力。

```java
public class ZConfigRegistry implements RegistryService {
    private ZNamingService namingService;  // 命名服务

    // 核心方法
    public void register(URL url);           // 注册服务
    public void unregister(URL url);         // 注销服务
    public void subscribe(URL url, NotifyListener listener);  // 订阅服务
    public void unsubscribe(URL url, NotifyListener listener); // 取消订阅
    public List<URL> lookup(URL url);        // 查询服务
}
```

### 4.3 负载均衡

#### RandomLoadBalance - 随机负载均衡

```java
public class RandomLoadBalance extends AbstractLoadBalance {
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        int length = invokers.size();
        int totalWeight = getTotalWeight(invokers, invocation);

        if (totalWeight > 0) {
            // 按权重随机
            int offset = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < length; i++) {
                Invoker<T> invoker = invokers.get(i);
                int weight = getWeight(invoker, invocation);
                offset -= weight;
                if (offset < 0) {
                    return invoker;
                }
            }
        }
        // 所有权重相同，直接随机
        return invokers.get(ThreadLocalRandom.current().nextInt(length));
    }
}
```

#### RoundRobinLoadBalance - 轮询负载均衡

```java
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    // 带权重的轮询状态
    private final ConcurrentMap<String, WeightedRoundRobin> weightedStates = new ConcurrentHashMap<>();

    private <T> Invoker<T> selectByWeightedRoundRobin(List<Invoker<T>> invokers, ...) {
        // 清理过期的状态
        weightedStates.entrySet().removeIf(entry -> {
            return System.currentTimeMillis() - state.lastUpdateTime > 10000; // 10秒过期
        });

        // 平滑加权轮询算法
        for (Invoker<T> invoker : invokers) {
            WeightedRoundRobin state = weightedStates.computeIfAbsent(...);
            state.current += weight;  // 增加当前权重

            if (state.current > maxCurrent) {
                selectedInvoker = invoker;
                selectedState = state;
            }
        }

        // 减少选中 invoker 的当前权重
        selectedState.current -= getTotalWeight(invokers, invocation);
        return selectedInvoker;
    }
}
```

### 4.4 集群容错

#### FailoverClusterInvoker - 失败自动切换

```java
public class FailoverClusterInvoker<T> extends AbstractClusterInvoker<T> {
    @Override
    public Result doInvoke(Invocation invocation) throws RpcException {
        List<Invoker<T>> invokers = list(invocation);
        int retries = url.getParameter("retries", 2) + 1;
        RpcException exception = null;
        Set<Invoker<T>> invoked = new HashSet<>(retries);

        for (int i = 0; i < retries; i++) {
            try {
                // 选择未调用过的 invoker
                Invoker<T> invoker = select(invokers, invocation, invoked);
                invoked.add(invoker);
                return invoker.invoke(invocation);
            } catch (RpcException e) {
                exception = e;
                if (e.isNetworkException()) {
                    // 网络异常，可以重试
                    continue;
                }
                // 业务异常，不重试
                throw e;
            }
        }
        throw new RpcException("Failed to invoke after " + retries + " retries", exception);
    }
}
```

### 4.5 网络通信

基于 Netty 实现的高性能网络通信层：

```java
// 服务端启动
public class RpcServer {
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    public void start() throws InterruptedException {
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new RpcMessageDecoder())    // 解码器
                        .addLast(new RpcMessageEncoder())      // 编码器
                        .addLast(new RpcServerHandler(services)); // 业务处理器
                }
            });

        ChannelFuture future = bootstrap.bind(port).sync();
        future.channel().closeFuture().sync();
    }
}

// 客户端
public class RpcClient {
    private Bootstrap bootstrap;
    private Channel channel;

    public void connect() throws InterruptedException {
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
            .channel(NioSocketChannel.class)
            .handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(new RpcMessageDecoder())
                        .addLast(new RpcMessageEncoder())
                        .addLast(new RpcClientHandler());
                }
            });

        ChannelFuture future = bootstrap.connect(host, port).sync();
        channel = future.channel();
    }
}
```

## 5. Spring Boot 集成

### 5.1 自动配置

```java
@Configuration
@EnableConfigurationProperties(ZRpcProperties.class)
public class ZRpcAutoConfiguration {

    @Bean
    public ZRpcBeanPostProcessor zRpcBeanPostProcessor() {
        return new ZRpcBeanPostProcessor();
    }
}

// 配置属性
@ConfigurationProperties(prefix = "z.rpc")
@Data
public class ZRpcProperties {
    private String registry;      // 注册中心地址
    private int port = 20880;     // 服务端端口
    private String version = "1.0.0";  // 服务版本
}
```

### 5.2 注解驱动

```java
// 服务提供者
@Service
@ZRpcService
public class HelloServiceImpl implements HelloService {
    @Override
    public String sayHello(String name) {
        return "Hello, " + name;
    }
}

// 服务消费者
@Service
public class UserService {
    @ZRpcReference
    private HelloService helloService;

    public void greet() {
        String result = helloService.sayHello("World");
        System.out.println(result);
    }
}
```

## 6. 关键设计决策

### 6.1 为什么使用 Z-Config 作为注册中心

- **一体化**：Z-Config 是内部已有的服务发现框架，可以复用现有基础设施
- **高性能**：基于 Netty 的长连接，支持推送模式
- **轻量级**：无需额外部署 ZooKeeper 等中间件

### 6.2 为什么使用 Netty 作为通信框架

- **高性能**：基于 NIO 的异步非阻塞 I/O
- **可靠性**：内置心跳检测、断线重连机制
- **易扩展**：Pipeline 设计方便添加编解码器

### 6.3 负载均衡算法选择

- **随机**：实现简单，均匀性好
- **轮询**：平滑加权轮询算法，考虑服务权重
- **最少活跃**：追踪每个服务的活跃调用数

## 7. 性能优化

### 7.1 连接复用

- 客户端与服务端建立长连接，避免频繁创建销毁
- 使用连接池管理多个服务提供者连接

### 7.2 序列化优化

- 使用 Kryo/Protostuff 等高性能序列化框架
- 支持自定义序列化扩展

### 7.3 线程模型

- 使用 Netty 的 EventLoop 处理 I/O
- 业务线程池处理业务逻辑
- 避免 I/O 线程阻塞

## 8. 容错机制

### 8.1 调用端容错

| 策略       | 说明     | 适用场景       |
|----------|--------|------------|
| Failover | 失败自动切换 | 读操作，幂等操作   |
| Failfast | 快速失败   | 非幂等写操作     |
| Failsafe | 失败安全   | 日志记录，非关键调用 |

### 8.2 服务端保护

- **限流**：基于令牌桶算法限制 QPS
- **熔断**：失败率达到阈值时自动熔断
- **降级**：返回兜底数据，保证可用性

## 9. 扩展点

框架提供 SPI 扩展机制，可在以下扩展点自定义实现：

1. **Registry** - 自定义注册中心
2. **LoadBalance** - 自定义负载均衡策略
3. **Cluster** - 自定义集群容错策略
4. **Transporter** - 自定义网络传输层
5. **Serialization** - 自定义序列化方式

## 10. 监控与运维

### 10.1 指标收集

- QPS、RT、成功率、失败率
- 线程池、连接池状态
- JVM 内存、GC 情况

### 10.2 链路追踪

集成 SkyWalking/Zipkin，实现分布式调用链路追踪。

### 10.3 动态配置

支持通过配置中心动态调整：

- 超时时间
- 重试次数
- 负载均衡策略
- 熔断阈值

---

*文档版本: 1.0*
*最后更新: 2026-03-29*
