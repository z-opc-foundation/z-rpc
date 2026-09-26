# Z-RPC

> **Java 8 + Netty 4 的 RPC 框架** — 微内核 + SPI 插件化，参考 Apache Dubbo / SOFA-RPC / gRPC / brpc 设计
> Java 8 编译级别（`maven.compiler.source/target=8`）· Spring Boot 2.7.12 · Netty 4 · MIT License

[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.2-blue?logo=apache-maven)](https://central.sonatype.com/search?q=g:io.github.yuku123+a:z-rpc*)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)
[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://openjdk.org)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.12-6DB33F)](https://spring.io)

---

## 先说清楚这份文档的口径

本 README 里**每一句"支持 / 默认 / 自动"都在 `_doc/002_测试报告.md` 里有对应的实测条目**。上一版 README 的 13 段 Java 示例里有 9 段编译不过（`RpcServerConfig`、`InMemoryRegistry`、`MockFilter`、`client.createGenericProxy(..)` 这些名字在仓库里根本不存在），能力表里的"6 种集群容错 / 5 种负载均衡 / 10 个内置 Filter / z-config + ZK 注册中心 / dubbo + http 协议 / Prometheus 上报"也都没有代码兑现，因此本版按实测重写，并把**没有兑现的**功能从能力表移到 [还没做](#还没做这些是实测结论不是计划) 一节，每条带上报告里的缺陷编号。

已发布到 Maven Central 的是 **1.0.2**（`groupId: io.github.yuku123`）。要提醒一句：**已发布的 1.0.2 是 2026-09-13 的字节**，此后本仓 `src/main` 落的 37 处修复**一个都没进过任何已发布构件**（报告 §9.4 / N42）。要用修复后的行为，得从本仓构建。

---

## 🚀 5 分钟接入

### 方式一：注解驱动（Spring Boot 应用，最常见）

`spring-boot-starter` 只带 `spring-context`，**不含 Web**；下面的 `@RestController` 示例需要自己引入 `spring-boot-starter-web`。

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-rpc-spring-boot-starter</artifactId>
    <version>1.0.2</version>
</dependency>
```

#### 服务端（提供方）

`@ZRpcService` 本身已元标注 `@Component`，但**光打它还不够**：注入器由 `@EnableZRpc` 装配，所以启动类上要有 `@EnableZRpc`（报告 §7 第 4/16 行，两条都是先踩坑再补的）。

```java
// 1. 定义接口（提供方与消费方共享）
public interface UserService {
    User findById(long id);
    List<User> findByIds(List<Long> ids);
}

// 2. 服务端实现
@ZRpcService(interfaceClass = UserService.class, version = "1.0.0")
public class UserServiceImpl implements UserService {

    @Override
    public User findById(long id) {
        return new User();
    }

    @Override
    public List<User> findByIds(List<Long> ids) {
        return new ArrayList<User>();
    }
}

// 3. 启动类：@EnableZRpc 负责把导出器与注入器装进容器
@SpringBootApplication
@EnableZRpc(scanBasePackages = "com.example.user")
public class UserProviderApp {
    public static void main(String[] args) {
        SpringApplication.run(UserProviderApp.class, args);
    }
}
```

`application.yml` —— 下面这份**只列 `ZRpcProperties` 真实存在的 key**（前缀 `z.rpc`，嵌套类 `application` / `registry` / `server` / `consumer` / `provider` / `protocol`）：

```yaml
z:
  rpc:
    enabled: true
    application:
      name: user-service
      version: 1.0.0
      organization: example
    server:
      enabled: true
      host: 0.0.0.0
      port: 20880
      threads: 200
      ioThreads: 0
      payload: 8388608
    protocol:
      name: z-rpc            # 目前只有这一种
      port: 20880
      serialization: java    # 线上真正用的是 Java 原生序列化，见"协议"一节
    provider:
      timeout: 3000
      threads: 200
      delay: 0
      weight: 100
      async: false
      token: ""
      serialization: java
```

上一版 README 这里的 `z.rpc.port` / `z.rpc.serialize` / `z.rpc.cluster` / `z.rpc.loadbalance` / `z.rpc.retries` / `z.rpc.timeout` / `z.rpc.group` / `z.rpc.filter.enabled` / `z.rpc.metrics.*` / `z.rpc.client.*` / `z.rpc.server.threads.boss|worker|business` / `z.rpc.server.channel.*` / `z.rpc.registry.group|username|password` / `z.rpc.mock-default` / `z.rpc.rest-port` **都不是 `ZRpcProperties` 的字段**。Spring Boot 的 relaxed binding 对未知 key 是**静默忽略**，所以照旧文档写不会报错，只会被无声丢掉。

#### 客户端（消费方）

```java
@RestController
public class UserController {

    @ZRpcReference(version = "1.0.0", url = "z-rpc://127.0.0.1:20880")
    private UserService userService;     // 远程代理，像本地 bean 一样用

    @GetMapping("/users/{id}")
    public User get(@PathVariable long id) {
        return userService.findById(id); // 走 RPC
    }
}
```

`url` 是**直连**，目前这是唯一被跑通过的消费方式。注册中心那条路还没接线：`z.rpc.registry.*` 四个 key 都真实存在（`address` / `namespace` / `type` / `enabled`），但 core 侧 `ServiceConfig.registerToRegistry()` 里加载注册中心的那一行是注释掉的，字段恒 null，随后那句 `registryService.register(..)` 必抛 NPE 并被外层 `catch (Exception)` 吞成一行日志，`export()` 照样"成功"（报告 N13 / §2）。`z-config` 与 ZK 的实现类在本仓不存在。

这条链路的端到端证据：两个真 JVM（Ubuntu 18.04 / JDK 1.8.0_362）上，`order-service` 经 `@ZRpcReference(url = "zrpc://127.0.0.1:20880")` 取回**只存在于 `user-service` 进程内存里的** `userName='Alice'`（报告 §9.5–§9.8）。

### 方式二：API 调用（不依赖 Spring）

上一版这一段用的 `RpcServerConfig` / `RpcClientConfig` / `server.register(接口, 实现)` / `client.start()` / `client.createProxy(..)` 全部不存在。真实形状如下 —— 注意 `RpcClient` 只发 `RpcRequest`，**不给接口代理**；要拿到能按接口调的 `Invoker`，走 `Protocol` SPI：

```java
// 服务端：一台真实监听的 RpcServer
RpcServer server = new RpcServer("0.0.0.0", 20880);
server.registerService(UserService.class, new UserServiceImpl());
server.start();                       // throws InterruptedException

// 客户端：经 Protocol SPI 得到 Invoker，自己拼 Invocation
ZRpcProtocolImpl protocol = new ZRpcProtocolImpl();
URL provider = new URL("z-rpc", "127.0.0.1", 20880, UserService.class.getName());
Invoker<UserService> invoker = protocol.refer(UserService.class, provider, null);
Result r = invoker.invoke(new RpcInvocation(UserService.class.getName(), "findById",
        new Class<?>[] { long.class }, new Object[] { 1001L }));
Object value = r.getValue();          // 由 Provider 算出来的结果
invoker.destroy();
server.stop();
```

`registerService(Class, Object)` 注册的是**裸接口名**；要把版本并进路由键得用 `register(Class, Object, String version)`（键形如 `接口全名:version`，`RpcServer.serviceKey(..)` 就是这条规则的出处）。两条路都不合版本那条的坑见报告 N14。

`Invoker.invoke(..)` 声明 `throws Throwable` —— 业务异常不保证被包成 `RpcException`，上层按 `RpcException` catch 会漏（报告 N16 最后一条）。

---

## 📦 模块与实际装配状态

`ls -d z-rpc-*/` 有 **17** 个目录，根 pom 的 `<modules>` 里**启用 14 个**，另外 3 行被注释掉：`z-rpc-examples`、`z-rpc-admin`，还有一行重复的 `z-rpc-registry`（registry 本身在启用列表里，注释那行是历史遗留）。上一版写的"17 子模块"是把目录数当成了模块数。

| 模块 | reactor | 里面真实存在的东西 |
|---|---|---|
| `z-rpc-common` | ✅ | `URL` / `Node` / `RpcException` / `RpcConstants` / `ProtocolConstants`（`Result` 不在这，在 `z-rpc-api`） |
| `z-rpc-spi` | ✅ | `@SPI` / `@Adaptive` / `@Activate` / `ExtensionLoader` |
| `z-rpc-api` | ✅ | `Invoker` / `Invocation` / `Result` / `Filter` / `Protocol` / `ProxyFactory` 接口 + `JdkProxyFactory` |
| `z-rpc-serialize` | ✅ | 5 个 `Serialization` 注册项（java / json / hessian2 / kryo / protobuf） |
| `z-rpc-protocol` | ✅ | 26 字节定长头的 `ZRpcMessageEncoder` / `ZRpcMessageDecoder` + `ZRpcProtocolImpl` |
| `z-rpc-remoting` | ✅ | `RpcServer` / `RpcClient` / 10 字节帧的 `RpcMessageEncoder` / `RpcMessageDecoder` / `RpcServerHandler` |
| `z-rpc-registry` | ✅ | `RegistryService` + `InMemoryRegistryService`、`RpcRegistry` + `InMemoryRpcRegistry` |
| `z-rpc-cluster` | ✅ | 5 个 `Cluster`、3 个 `LoadBalance`、`Directory` / `AbstractDirectory` / `StaticDirectory` / `Router`（`RegistryDirectory` 在 `z-rpc-core`，同包不同模块） |
| `z-rpc-filter` | ✅ | **4** 个 Filter：`ConsumerTraceFilter` / `MonitorFilter` / `ProviderContextFilter` / `ProviderMonitorFilter` |
| `z-rpc-mock` | ✅ | **1 个接口** `GenericService`，无实现、无代理工厂 |
| `z-rpc-metrics` | ✅ | `Histogram` / `MetricsCollector` / `MetricsReporter` |
| `z-rpc-async` | ✅ | `RpcContext`（traceId + attachments）/ `DefaultFuture` |
| `z-rpc-core` | ✅ | `ServiceConfig` / `ReferenceConfig` / `RegistryDirectory` 消费侧装配 |
| `z-rpc-spring-boot-starter` | ✅ | `@ZRpcService` / `@ZRpcReference` / `@EnableZRpc` / `ZRpcProperties` / 导出器与注入器 |
| `z-rpc-examples` | ❌ 注释掉 | `user-service`（提供方）+ `order-service`（消费方） |
| `z-rpc-admin` | ❌ 注释掉 | Spring Boot 后端，监听 **19090** |
| `z-rpc-admin-frontend` | ❌ 不是 Maven 模块 | React + AntD + Vite 前端，监听 **5173** |

---

## ✨ 能力矩阵（实测）

扩展点资源目录是 **`META-INF/z-rpc/`**（带连字符）。上一版在"微内核 + SPI"一节写的 `META-INF/zrpc/` 在仓库里不存在，两处混用会让自定义扩展静默加载不到。`ExtensionLoader` **不走 JDK `ServiceLoader`**（`src/main` 里 `ServiceLoader` 命中 0 行），它自己读上面那个目录下的清单文件。

| 扩展点 | `@SPI` | 已注册实现 | key |
|---|---|---|---|
| `com.zifang.z.rpc.api.Protocol` | ✅ | **1** | `z-rpc` |
| `com.zifang.z.rpc.api.ProxyFactory` | ✅ | **1** | `jdk` |
| `com.zifang.z.rpc.cluster.Cluster` | ✅ | **5** | `failover` / `failfast` / `failsafe` / `failback` / `broadcast` |
| `com.zifang.z.rpc.loadbalance.LoadBalance` | ✅ | **3** | `random` / `roundrobin` / `leastactive` |
| `com.zifang.z.rpc.filter.Filter` | ✅ | **4** | `consumer-trace` / `monitor` / `provider-context` / `provider-monitor` |
| `com.zifang.z.rpc.serialize.Serialization` | ✅ | **5** | `java` / `json` / `hessian2` / `kryo` / `protobuf` |
| `com.zifang.z.rpc.registry.RegistryService` | ✅ | **1** | `in-memory` |

`Cluster` 那一行的 ✅ 有半边条件：`com.zifang.z.rpc.cluster` 的 5 个类型（`Cluster` / `Directory` / `AbstractDirectory` / `Router` / `FailoverCluster`）在 `z-rpc-cluster` 与 `z-rpc-core` 里**各编译了一份**，core 那 5 份是修复前的快照，而唯一有差异的正是 `Cluster.java` —— 它少了 `@SPI("failover")` 那两行。依赖 `z-rpc-core` 的进程里 core 自己的 classes 在 classpath 上排第一，加载到的就是**没注解的那份**，于是 `ExtensionLoader.getExtensionLoader(Cluster.class)` 在 core 及其下游（含 starter、含已发布的 1.0.2）照样抛 `is not annotated with @SPI`（报告 N12，两条 `bug_` 用例钉着；删哪一边是模块边界决策，见报告 §8 第 7 条）。

### 集群容错（5 种，不是 6 种）

`failover` / `failfast` / `failsafe` / `failback` / `broadcast`。上一版列的第 6 种 **`available` 在仓库里没有实现类**，任何 SPI 清单里也搜不到。

`FailoverCluster` 的语义有五处和名字不符，用之前先读报告 N16：首次尝试**永远跳过 provider #0**（`select(invokers, 0, …)` 在 `size ≥ 2` 时返回 `get(1)`）；只有**抛出来**的失败才重试，`Result` 里带异常的一次都不重试；`isAvailable()` 只看目录空不空；`retries=-1` 会走到那句 `IllegalStateException("Should never reach here")`；无 provider 抛的是裸 `IllegalStateException` 而不是 `RpcException.noProvider`。

### 负载均衡（3 种，不是 5 种）

`random` / `roundrobin` / `leastactive`。上一版的 **`SmoothWeightedRR` 与 `ConsistentHash` 不存在**（`forking` / `consistenthash` 在任何 SPI 清单里都搜不到）。

而且**配置项本身没人 consult**：`z.rpc.consumer.loadbalance` / `cluster` / `timeout` 唯一的去处是 `ZRpcFrameworkInitializer` 那句 `log.info`（**并不会**被搬到 `ReferenceConfig` 上 —— 搬属性的是 `@ZRpcReference` 那条路，跟 yaml 无关），`z.rpc.consumer.retries` 连横幅都没进（见"配置项到底哪些有落点"那张表）。结果就是策略名随便写也不报错，超时最终落到 `RpcClient` 的字段常量（报告 N15：`bug_loadBalanceSettingIsNeverConsulted`、`bug_clusterSettingIsIgnored`、`bug_timeoutSettingIsWriteOnly`、`bug_protocolSettingDoesNotChangeTheFrame`）。

### 序列化（5 种注册，2 种有硬限制）

`java`（默认）/ `json` / `hessian2` / `kryo` / `protobuf` 都在 SPI 清单里。已知两条实测限制，还没修：

- **`json`**：目标类型是容器时会静默丢条目 —— `deserialize(bytes, LinkedHashMap.class)` 回来是 `size=0` 的 Map，同一份数据 java / hessian2 / kryo 都能拿回 2 个条目（报告 H3；根因在外部构件 `z-util-parser-json`）。
- **`protobuf`**：只接受 `com.google.protobuf.MessageLite`，喂 POJO / `String` / `Integer` 一律抛 `RpcException`；本仓没有任何 `.proto` 生成类，所以这个注册项实际不可用（报告 H4）。
- 五个实现对 `serialize(null)` 都返回**长度 0** 的数组，而不是抛错。

未知序列化 id 现在会 fail-fast（上一版是静默回落 Hessian2），已修，报告 H2 / §7。

### 协议

**线上跑的帧是 10 字节**：`ZRPC`(4) + version(1) + msgType(1) + bodyLen(4)，body 用 **Java 原生序列化**（`AC ED` 开头）。这是 `z-rpc-remoting` 的 `RpcMessageEncoder`，也是唯一被真实 socket 往返验证过的一套（报告编解码轮 / `RpcMessageCodecTest` 18 条）。

`z-rpc-protocol` 里另有**第二套离线编解码器**，定长头 **26 字节**（`ProtocolConstants.HEADER_LENGTH = 26`；上一版写 24，那 2 字节的差会让半包停在 24/25 字节时抛 `IndexOutOfBoundsException`，已修，报告 H1）：

```
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Magic Number (0x5A525043 "ZRPC")          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|Ver (1B)  |MsgType(1B)|SerId(1B)|Cmp(1B)|     Status (2B)     |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Request ID (8 bytes)                       |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                    Body Length (4 bytes)                      |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
| Header Length (2B) |   Reserved (2B)                          |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

别把两边读数混着读：`FrameCodecTest` 的 17 条钉的是 26 字节那套，生产 `RpcClient` / `RpcServer` 走的是 10 字节那套。**`dubbo` 与 `http` 两种协议没有实现**（`Protocol` SPI 只有 `z-rpc` 一个 key），所以旧 README 的"多协议端口：单服务同时监听 zrpc + http"与"通过 Rest 调用（dubbo 兼容）"不成立。

### 配置项到底哪些有落点（33 个 key，逐个判）

上面那份 yaml 里的 key **都真实存在**（`ZRpcProperties` 反射遍历得 33 个叶子），但"存在"和"起作用"是两件事。这一张表按**谁真的读了它**分三档，判据全在 `ConfigLandingContractTest` 里，不是数出来的：

- **真用**：值流进了装配决策或构造参数（`z.rpc.enabled` 与 `z.rpc.registry.enabled` / `server.enabled` 走 `@ConditionalOnProperty` 与 `if` 分支决定 bean 在不在；`server.host` / `server.port` 是 `new RpcServer(..)` 的实参）。
- **只进横幅**：只在 `ZRpcFrameworkInitializer` 的 `log.info` 里被读了一次，打印完就没人管 —— 改它只会改变启动日志的字样。
- **没人读**：全仓 `src/main` 里连 `get<Group>().get<Key>()` 这条链都搜不到，纯装饰。

| key | 类型 | 默认值 | 落点 |
|---|---|---|---|
| `z.rpc.application.name` | String | z-rpc-app | 只进横幅 |
| `z.rpc.application.organization` | String | zifang | 只进横幅 |
| `z.rpc.application.version` | String | 1.0.0 | 只进横幅 |
| `z.rpc.consumer.async` | boolean | false | 没人读 |
| `z.rpc.consumer.check` | boolean | false | 没人读 |
| `z.rpc.consumer.cluster` | String | failover | 只进横幅 |
| `z.rpc.consumer.connections` | int | 1 | 没人读 |
| `z.rpc.consumer.loadbalance` | String | random | 只进横幅 |
| `z.rpc.consumer.retries` | int | 2 | 没人读 |
| `z.rpc.consumer.serialization` | String | hessian2 | 没人读 |
| `z.rpc.consumer.timeout` | int | 3000 | 只进横幅 |
| `z.rpc.enabled` | boolean | true | 真用 |
| `z.rpc.protocol.compressThreshold` | int | 1024 | 没人读 |
| `z.rpc.protocol.name` | String | z-rpc | 只进横幅 |
| `z.rpc.protocol.port` | int | 20880 | 没人读 |
| `z.rpc.protocol.serialization` | String | hessian2 | 只进横幅 |
| `z.rpc.provider.async` | boolean | false | 没人读 |
| `z.rpc.provider.delay` | int | 0 | 没人读 |
| `z.rpc.provider.serialization` | String | hessian2 | 没人读 |
| `z.rpc.provider.threads` | int | 200 | 没人读 |
| `z.rpc.provider.timeout` | int | 5000 | 没人读 |
| `z.rpc.provider.token` | boolean | false | 没人读 |
| `z.rpc.provider.weight` | int | 100 | 没人读 |
| `z.rpc.registry.address` | String | 127.0.0.1:8084 | 只进横幅 |
| `z.rpc.registry.enabled` | boolean | true | 真用 |
| `z.rpc.registry.namespace` | String | public | 没人读 |
| `z.rpc.registry.type` | String | z-config | 只进横幅 |
| `z.rpc.server.enabled` | boolean | true | 真用 |
| `z.rpc.server.host` | String | 0.0.0.0 | 真用 |
| `z.rpc.server.ioThreads` | int | 8 | 没人读 |
| `z.rpc.server.payload` | int | 8388608 | 没人读 |
| `z.rpc.server.port` | int | 20880 | 真用 |
| `z.rpc.server.threads` | int | 200 | 没人读 |

**5 真用 / 10 只进横幅 / 18 没人读。** 也就是说：这仓的 yaml 面比它的行为面大得多 —— 上一版 README 那些**根本不存在**的 key（下面那句清单）至少还会报错机会都没有，而这一张表里"没人读"的 18 个是**真实存在、静默无效**，更难发现。默认值与类型不是抄的，是用例里 `newInstance()` 之后逐个 getter 读出来的，写错一个字这条就红。

### 注解属性到底哪些有落点

这张表是照 `ZRpcServiceExporter` 与 `ZRpcReferenceInjector` 的实测写的，不是照注解声明写的（报告 H8 / H9 / N15）：

| 注解 | 属性总数 | 有落点 | 没有落点 |
|---|---|---|---|
| `@ZRpcService` | 11 | `interfaceClass` / `version` | `interfaceName` / `group` / `weight` / `delay` / `timeout` / `retries` / `loadbalance` / `cluster` / `async` |
| `@ZRpcReference` | 17 | `interfaceName` / `version` / `group` / `timeout` / `retries` / `loadbalance` / `cluster` / `registry` / `url` / `async` / `oneway` | `interfaceClass` / `check` / `lazy` / `connections` / `client` / `serialization` |

判据（不是文字，是跑得动的）：**有落点 = 这个属性在对应处理器源码里以 `annotation.<属性>()` 被读到至少一次；没有落点 = 0 次**。两列的名字合起来必须**正好等于** `ZRpcService.class.getDeclaredMethods()` 的名字集合（**逐个名字比，不比个数**），"属性总数"那一列也必须等于反射出来的数量 —— 全在 `ConfigLandingContractTest` 里。

- `@ZRpcService` 的 `interfaceClass` 算有落点：不写则取实现类的唯一接口，多接口全导。剩下 9 个放不进去 —— `RpcServer` 的三个 `register*` 重载（`registerService(Class, Object)` / `registerService(String, Object)` / `register(Class, Object, String)`）里**没有任何 int/long 参数位**可放，这条也是反射量的。
- **`@ZRpcReference` 的 `interfaceClass` 是本轮从"有落点"列挪出来的**：注入器调的是 `buildReferenceConfig(annotation, field.getType())`，整个文件里 `annotation.interfaceClass()` 命中 **0** 次 —— 接口取的是**字段声明类型**，注解里那个 `Class` 从来没被读过。证据用例：字段声明成 `Alpha` 而注解写 `interfaceClass = Beta.class`，`ReferenceConfig.getInterfaceClass()` 回来的是 `Alpha`（`bug_annotationInterfaceClassIsNeverRead`）。
- `check` / `lazy` / `connections` / `client` / `serialization` 这 5 个在 `ReferenceConfig` 里 **field / setter / getter 全 0 命中**（上一版这里写的是"5 个里 4 个"，实测是 5 个都没有）；搬过去的 `timeout` / `cluster` / `loadbalance` / `retries` 下游没人读。

`@ZRpcReference` 的 `check` 默认 `true` 完全不起作用：对一个必连不上的 `zrpc://127.0.0.1:1`，注入器照样返回 JDK 代理，故障被推迟到首次调用（报告 `bug_deadUrlStillYieldsProxy`）。

### 声明式 API（`ServiceConfig` / `ReferenceConfig`）

**所有 setter 都返回 `void`**（`ServiceConfig` 20 个 setter、`ReferenceConfig` 同理），所以旧 README 那段 `new ServiceConfig<>().setInterface(..).setRef(..)` 的链式写法不可能成立；方法名也是 `setInterfaceClass` 而不是 `setInterface`。真实形状：

```java
ServiceConfig<UserService> service = new ServiceConfig<UserService>();
service.setInterfaceClass(UserService.class);
service.setRef(new UserServiceImpl());
service.setPort(20880);
service.export();                      // 注意：bind 失败也返回，见下方警告

ReferenceConfig<UserService> ref = new ReferenceConfig<UserService>();
ref.setInterfaceClass(UserService.class);
ref.setUrl("z-rpc://127.0.0.1:20880");
UserService proxy = ref.get();
Object one = proxy.findById(1001L);
ref.destroy();
service.unexport();
```

两条实测警告：

- `export()` 在端口被占、bind 失败时**照样返回并把 `exported` 置 true**（报告 N18 `bug_exportLiesWhenBindFails`）。
- `ReferenceConfig.destroy()` 会去 `destroy` 那个 **SPI 单例** registry，同 JVM 里其他引用者的注册/订阅一起失效（报告 N18 `bug_destroyTakesDownSharedRegistry`）。

### 自定义 Filter

```java
@Activate(group = "consumer", order = 50)
public class MyFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invoker.invoke(invocation);
        } finally {
            System.out.println("RT=" + (System.currentTimeMillis() - start));
        }
    }
}
```

资源文件 `META-INF/z-rpc/com.zifang.z.rpc.filter.Filter`：

```
my=com.example.filter.MyFilter
```

**但当前没有任何一处生产代码装配过滤器链**，所以这个 Filter 写出来不会被调用（报告 N19，10 条全绿钉着这件事）：`getActivateExtension` 在 `src/main` 的调用点是 **0**（唯一的定义在 `ExtensionLoader` 自己文件里）；库里没有"链对象"，责任链只有一个返回 `List` 的加载器，没有任何东西按 order 把 `Invoker` 包起来；真实服务端 `RpcServerHandler` 是拿 `RpcRequest` 直接反射调 POJO。另外 `getActivateExtension()` 还叠了一个加载时序 bug：`cachedActivates` 只在 `loadExtensionClasses()` 里填，冷 loader 上它永远返回空，必须先碰一次别的 API 才有内容（报告 H5）。`@Activate` 的三个属性（`group` / `value` / `order`）真实存在且运行时读得到，只是没人用。

---

## 还没做（这些是实测结论，不是计划）

| 旧 README 的主张 | 实测 |
|---|---|
| `@ZRpcReference(mock = "true")`、`mockReturn`、`MockFilter`、`mock-default` | `@ZRpcReference` 的 17 个属性里 `mock` 出现 **0** 次；`MockFilter` 类不存在；`ZRpcProperties` 没有 `mock-default` key；`z-rpc-mock` 整模块只有 `GenericService` 一个接口 |
| 泛化调用 `client.createGenericProxy(name, version)` | 没有任何 `createGenericProxy`。`GenericService` 有 `$invoke(String, String[], Object[])` 和 `default $invokeMap(..)`，但**没有生产实现、没有代理工厂产出它**；`$invokeMap` 的参数类型是取实参运行时类，子类会被报成子类、`null` 只能报成 `java.lang.Object`（报告 N22） |
| 注册中心 z-config / ZK Naming / "未启动时降级为 InMemoryRegistry" | `RegistryService` SPI 只有 `in-memory` 一项；`ZConfigRegistry` / `ZkNamingRpcRegistry` 源码是 `.java.disabled` 且已从清单里注释掉；core 侧加载注册中心的那行注释着，字段恒 null（N13）；没有任何"降级"代码路径 |
| Prometheus 集成（`z_rpc_*` 指标）、对接 SkyWalking / Jaeger | 全仓 `src/main` 里 `z_rpc` / `prometheus` 命中 **0** 行；链路追踪只有本地 `traceId` 塞 MDC，没有 exporter |
| 指标上报 `Filter → MetricsCollector → MetricsReporter → HTTP POST → z-rpc-admin` | `MetricsReporter` 的"上报"只打日志 —— 直接扫它的 class 字节常量池，`java/net`、`Socket`、`Http`、`admin` **一个都不出现**（N20 `bug_reportingHasNoTransport`）；且 `src/main` 里没有任何地方启动它。admin 侧确实有 `POST /api/admin/metrics/push`，但没人发 |
| 指标表里的 `ConnectionManager` / `HeartbeatReconnector` | 两个类都不存在。真实存在的只有 `Histogram` / `MetricsCollector` / `MetricsReporter`（`z-rpc-metrics`）与 `MonitorFilter` / `ProviderMonitorFilter`（`z-rpc-filter`） |
| 健康检查：心跳 + 离线剔除 | 目录层订阅到空通知时**旧 provider 全部留着**，服务下线在客户端永不生效（N17 `bug_emptyNotificationIsIgnored`）；一次失败把该 invoker 永久打掉且不重建（N17 `bug_oneFailureBricksTheInvoker`） |
| 连接池复用 / `ReferenceCountExchangeClient` / 时间轮 `HashedWheelTimer` | 三个名字在 `src/main` 都不存在。`z.rpc.consumer.connections` 属性有，注入器直接丢弃（H8 余下五项） |
| 灰度发布按 version / group 路由、`z.rpc.weight.<group>` 权重表 | `weight` 是个 map 形 key，`ZRpcProperties` 里只有 `provider.weight`（int）；`group` 在注册中心配置里不存在；声明式导出走的是不带版本的那条注册路径，**版本在两侧各丢一次**，而 `RegistryDirectory` 以 `host:port` 为键，同地址两个版本会塌成一条（N14） |

---

## 🔬 SPI 扩展点怎么加

`@SPI` 只能标在**接口**上，值是该接口的默认实现 key（`@SPI("failover")`）；`Scope` 枚举与 `@Spi(scope = ..)` 这种写法在仓库里不存在（旧 README 的 Case 6 就是这么写的，编译不过）。

```java
public class MyRegistryService implements RegistryService {

    @Override
    public void register(URL url) {
    }

    @Override
    public void unregister(URL url) {
    }

    @Override
    public void subscribe(URL url, NotifyListener listener) {
    }

    @Override
    public void unsubscribe(URL url, NotifyListener listener) {
    }

    @Override
    public List<URL> lookup(URL url) {
        return new ArrayList<URL>();
    }

    @Override
    public void destroy() {
    }
}
```

资源文件 `META-INF/z-rpc/com.zifang.z.rpc.registry.RegistryService`：

```
my-registry=com.example.MyRegistryService
```

`RegistryService` 接口上已有 `@SPI("in-memory")`，所以新增实现不需要改接口。加载侧一切正常（`getExtension("my-registry")` 拿得到实例、按名字缓存成单例），**只是 core 侧不会去加载它** —— 要用得自己 `setRegistryService(..)` 手工注入 `ServiceConfig` / `ReferenceConfig`（这正是报告 `registryModeWorksWhenHandWired` 跑通的方式）。

---

## 🏗 目录结构

```
z-rpc/
├── pom.xml                                  # 聚合 + dependencyManagement，14 个启用模块
├── z-rpc-common/                            # URL / Node / Result / RpcException / ProtocolConstants
├── z-rpc-spi/                               # @SPI @Adaptive @Activate ExtensionLoader
├── z-rpc-api/                               # Invoker Invocation Result Filter Protocol ProxyFactory
├── z-rpc-serialize/                         # java json hessian2 kryo protobuf
├── z-rpc-protocol/                          # 26 字节定长头编解码 + ZRpcProtocolImpl
├── z-rpc-remoting/                          # RpcServer RpcClient + 线上那套 10 字节帧
├── z-rpc-registry/                          # RegistryService/RpcRegistry + 两份 InMemory 实现
├── z-rpc-cluster/                           # 5 Cluster + 3 LoadBalance + Directory + Router
├── z-rpc-filter/                            # 4 个 Filter（暂无装配点）
├── z-rpc-mock/                              # GenericService 接口（无实现）
├── z-rpc-metrics/                           # Histogram MetricsCollector MetricsReporter
├── z-rpc-async/                             # RpcContext DefaultFuture
├── z-rpc-core/                              # ServiceConfig ReferenceConfig RegistryDirectory
├── z-rpc-spring-boot-starter/                # 注解 + ZRpcProperties + 导出器/注入器
├── z-rpc-examples/                          # 不在 reactor：user-service(提供方) order-service(消费方)
├── z-rpc-admin/                             # 不在 reactor：Spring Boot 后端，19090
└── z-rpc-admin-frontend/                     # React + AntD + Vite，5173
```

---

## 🧪 跑测试与实跑示例

```bash
mvn -B clean test                 # 反应堆 14 个模块
cd z-rpc-admin && mvn -B clean test   # admin 不在 reactor，要单独跑
```

当前读数（2026-09-26，工厂层轮之后）：**548 条用例 = 反应堆 536 + admin 12**，51 个测试源文件（532 个 `@Test` / `@ParameterizedTest` 标注）/ 42 个 surefire 报告类，`fail / error / skip` 全 0。钉本 README（收尾那把连本报告一起钉）的尺现在共 **25** 条：README 契约轮的 12 条管模块表 / SPI 表 / 端口 / 许可 / 常量 / 测试源文件数，落点尺轮的 7 条管那两张**落点表**（33 个 yaml key 逐个判 + 两个注解的属性名字集合），引用尺轮的 6 条管**文档指向仓库内部的指针、以及文档转述出去的计数** —— 点名的 `bug_` 用例名要是真方法、写的"报告 §x.y / 第 N 条"要在报告里落得下去、点名的驼峰标识符要么搜得到要么那一行明说不存在、写下的每个行号指针（`名字`（:行号） 与 `名字:行号` 两形）要落在那个方法在盘上的区间里、报告里转述的 README 指针计数要等于现场重数的。前三组都双向注入验过会红（报告 §11.2、§12.3、§13.3），引用尺那 3 支变异的靶子就是本轮真实改掉的那处假用例名；收尾那把行号尺的三支猎物在测试里，它**第一次真跑就点出 5 处落不下去**（§13.1 末、§8 第 18 条），而它后来抓到的一次漂移，抓到的是**自己那一格**——给这把尺补猎物把那 18 行插进了它自己的声明之前（报告 §13.1 末）。最后那把转述计数尺第一次真跑抓到的是**报告自己**：那句里的 `§` 计数已从 9 涨到 11，而报告附的期望输出还写着 9（报告 §8 第 19 条）。这一轮的注入台账共 **6** 支、6 支都按预期落定（报告 §13.3）：一支把小节绑定窗口退回旧形状，分了两次来证红——一次让真文档点火，一次只留尺自带的猎物点火（报告 §13.2 第 7 条）；另一支方向朝绿，摘掉"去读盘上那份报告"那一句之后 18 条重新全绿，证的就是那句承重（报告 §13.2 第 8 条）。同一棵树在 macOS/JDK 25/Maven 3.9.14 与 Ubuntu 18.04/JDK 1.8.0_362/Maven 3.6.0 上逐模块读数相同（只有耗时不同）。逐模块分布与被钉住的缺陷清单见 [`_doc/002_测试报告.md`](_doc/002_测试报告.md)。

> 上一版这里写的 "312 + 78 + 14 + 31 + 22 + 18 + 15 PASS" 在仓库里没有任何对应产物，已按实测替换。

### 两个示例应用

`z-rpc-examples` 的 pom 坐标是 `com.zifang:z-rpc-core:1.0.0-SNAPSHOT` 一族，**只存在于作者机器的 `~/.m2`**，且不在 reactor 里（报告 N43）。在干净机器上直接 `mvn spring-boot:run` 连编译都过不去；要跑示例得先让 examples 解析到本仓构件。

跑通后的形状（已实测）：

| 应用 | HTTP | RPC |
|---|---|---|
| `z-rpc-examples/user-service` | 20881 | 20880（`z.rpc.server.port`） |
| `z-rpc-examples/order-service` | 20890 | 不起服务端（`z.rpc.server.enabled: false`） |

```bash
curl "http://localhost:20890/order/create?userId=1&amount=99.9"
curl "http://localhost:20890/order/user/1"
```

返回体里的 `userName` 只存在于 `user-service` 进程的内存中 —— 能取回即证明跨进程调用成立：

```json
{"id":1001,"userId":1,"userName":"Alice","amount":99.9,"status":"CREATED"}
```

`server.port`（Tomcat）与 `z.rpc.server.port`（Netty）**不能写成同一个值**：上一版两份 yml 都写 20880，Tomcat 先 bind、Netty 随后 `BindException`，该应用从来没成功启动过（报告 N44）。

### 控制台

```bash
cd z-rpc-admin && mvn spring-boot:run          # http://localhost:19090
cd z-rpc-admin-frontend && pnpm install && pnpm dev   # http://localhost:5173
```

上一版写 admin 在 9090 —— `z-rpc-admin/src/main/resources/application.yml` 里是 **19090**。

---

## 🐳 Docker

```dockerfile
FROM eclipse-temurin:8-jdk AS build
COPY . /src
RUN cd /src && mvn -B -DskipTests package

FROM eclipse-temurin:8-jre
# 聚合模块 z-rpc-examples 自己不出 jar（没有 spring-boot-maven-plugin），
# 可执行 jar 在两个示例子模块里：
COPY --from=build /src/z-rpc-examples/user-service/target/*.jar /app.jar
EXPOSE 20880 20881
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

`-Pcentral` 是**发布 profile**（上传 Central 用），跑普通构建不需要它；上一版把它写进了 Dockerfile。示例 jar 的可执行性还受 N43（examples 坐标）影响，先在能解析到本仓构件的环境里验证再拿去容器化。

---

## 📊 性能

上一版这里有一张 "单连接 24,000 QPS / P99 1.2ms / 异步 145,000 QPS" 的表，**仓内没有任何基准产物能支撑这些数字**（没有 JMH、没有压测脚本、没有结果文件），已删除。要性能数据请先在 `_doc/` 里放一份可复现的压测记录。

---

## 📚 文档

项目文档统一在 `_doc/`：

- [`_doc/001_arch/`](_doc/001_arch/) — 架构文档
  - [`00-overview.md`](_doc/001_arch/00-overview.md)
  - [`01-module-structure.md`](_doc/001_arch/01-module-structure.md)
  - [`技术实现.md`](_doc/001_arch/技术实现.md)
  - [`z-rpc-admin-frontend.md`](_doc/001_arch/z-rpc-admin-frontend.md)
- [`_doc/002_测试报告.md`](_doc/002_测试报告.md) — 缺陷台账（C/H/M/R/N 全部编号、37 处 `src/main` 改动、跨平台复跑读数）
- [`_doc/003_script/`](_doc/003_script/) — [`deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh) · [`install-settings.sh`](_doc/003_script/install-settings.sh) · [`push.sh`](_doc/003_script/push.sh)

上一版这里链了 11 篇 `docs/ARCHITECTURE.md` 之类的路径，`docs/` 目录不存在，11 条全是死链，已按真实文件替换。

---

## 🤝 贡献

改 `src/main` 请同步 `_doc/002_测试报告.md` 的台账：报告里每个哈希、行号、计数都要能用同一条命令复算。两条硬约束：**不要 `mvn install`**（会造出与反应堆分叉的构件），**不要用 `-pl` 单模块跑测试**（会从 `~/.m2` 解析到 9 月 13 日的旧构件，产生假红）。

## 📄 许可证

[MIT License](LICENSE) — `LICENSE` 文件第一行就是 `MIT License`；上一版底部写的 "Apache License 2.0" 与徽章里的 Apache 是错的。
