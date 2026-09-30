# z-rpc

> Java 8 + Netty 的 RPC 框架 —— 微内核 + SPI 插件化，形状参照 Apache Dubbo / SOFA-RPC / gRPC / brpc

一人公司基座里承担服务间调用的那一层。本仓的诚实形状是：一条被真实 socket 往返验证过的通路
（`@ZRpcService` / `@ZRpcReference` + `z-rpc://host:port` **直连** + Java 原生序列化 + JDK 动态代理 +
`RpcServer` / `RpcClient`），加上一圈**接口已经就位、装配点尚未接上**的可插拔扩展
（协议 / 序列化 / 集群 / 负载均衡 / 过滤器 / 注册中心 / 代理工厂，全部走自研 `ExtensionLoader`）。
凡是写不出对应实现的旧主张，都收进下方「还没做」一节，标注实测编号，不再混在能力表里。

---

## 📋 基本信息

| 字段 | 值 |
|------|-----|
| **仓库** | `z-rpc`（GitHub `yuku123/z-rpc`） |
| **Maven 坐标** | 聚合 pom `io.github.yuku123:z-rpc`，14 个 jar 子模块同版本 |
| **当前版本** | `1.0.4`（根 pom `<version>`；812743d 由 1.0.3 抬到 1.0.4，属 1.0.19 批量发布） |
| **父项目** | `io.github.yuku123:z-boot-parent:1.0.21`（`<relativePath/>` 留空，parent 在 repo1；ab6941f 迁入 1.0.19、5dbae8d 抬到 1.0.21；本仓**原先没有 `<parent>`**，是根 pom 自包含） |
| **Maven Central** | 已发布：聚合 pom + 14 个 jar 模块的 `1.0.4` 逐个 `curl` repo1 全部 200；`z-rpc-examples` / `z-rpc-admin` 不在 reactor、也不在 Central（1.0.3 / 1.0.4 均 404） |
| **默认端口** | RPC 服务端 `20880`（`z.rpc.server.port`，Netty）；示例 HTTP `20881` / `20890`；admin HTTP `19090`；admin-frontend `5173` |
| **运行口径** | Java 8（`compile.version=8`）· Spring Boot 2.7.18 · Netty 4.1.138.Final · log4j2 2.17.2（本仓按坐标钉住） |
| **最近更新** | 2026-09-30 |

端口分布（读自各模块 `src/main/resources/application.yml` 与 `vite.config.ts`）：

| 应用 | HTTP `server.port` | RPC `z.rpc.server.port` | context-path |
|------|--------------------|--------------------------|--------------|
| `z-rpc-admin`（不在 reactor） | 19090 | — | `/` |
| `z-rpc-examples/user-service`（不在 reactor） | 20881 | 20880 | `/` |
| `z-rpc-examples/order-service`（不在 reactor） | 20890 | 未起服务端（`z.rpc.server.enabled: false`） | `/` |
| `z-rpc-admin-frontend`（Vite dev server） | 5173 | — | — |

> Tomcat 的 `server.port` 与 Netty 的 `z.rpc.server.port` **不能写成同一个值**：examples 的 user-service
> 两份 yml 都写 20880 时，Tomcat 先 bind、Netty 随后 `BindException`，该应用从来没成功启动过
> （[`_doc/002_测试报告.md`](_doc/002_测试报告.md) N44）。

---

## 🎯 能力清单（逐条对应到代码）

下表每一项都指得回 `src/main/java` 里的具体类；对不上实现的都挪到
[还没做](#-还没做这些是实测结论不是计划)。

| 能力 | 入口 | 说明 |
|------|------|------|
| 服务导出 / 引用 | `z-rpc-core` `ServiceConfig` / `ReferenceConfig` | 声明式 API；所有 setter 返回 `void`，`ReferenceConfig.get()` 返回接口代理 |
| Spring 装配 | `z-rpc-spring-boot-starter` `ZRpcServiceExporter` / `ZRpcReferenceInjector` + `@EnableZRpc` | `@ZRpcService` 元标注 `@Component`，但导出/注入器由 `@EnableZRpc` 装配，启动类必须显式打 |
| 直连消费 | `@ZRpcReference(url = "z-rpc://host:port")` | **唯一**被真实 socket 往返验证过的消费路径 |
| 线上协议帧 | `z-rpc-remoting` `RpcMessageEncoder` / `RpcMessageDecoder` | 10 字节头（`ZRPC` 4B + version 1B + msgType 1B + bodyLen 4B），body 走 **Java 原生序列化**（`AC ED` 开头） |
| 离线协议帧 | `z-rpc-protocol` `ZRpcMessageEncoder` / `ZRpcMessageDecoder` | 26 字节定长头（`ProtocolConstants.HEADER_LENGTH = 26`），带序列化 id / 压缩位 / 状态码 |
| 序列化 | `z-rpc-serialize` 五个 `Serialization` 实现 | `java` / `json` / `hessian2` / `kryo` / `protobuf` |
| 集群容错 | `z-rpc-cluster` 五个 `Cluster` 实现 | `failover` / `failfast` / `failsafe` / `failback` / `broadcast` |
| 负载均衡 | `z-rpc-cluster` 三个 `LoadBalance` | `random` / `roundrobin` / `leastactive`；另有 `z-rpc-cluster/discovery` 子包（`InstanceLoadBalancer` 一族 + `ServiceDiscovery`） |
| 过滤器 | `z-rpc-filter` 四个 `Filter` | `consumer-trace` / `monitor` / `provider-context` / `provider-monitor`（**当前无装配点**，见「还没做」） |
| 注册中心 | `z-rpc-registry` `RegistryService` SPI | 只注册 `in-memory` 一项；`z-config` / `zknaming` 类源码后缀 `.java.disabled`，清单里也是注释状态 |
| SPI 微内核 | `z-rpc-spi` `@SPI` / `@Adaptive` / `@Activate` / `ExtensionLoader` | **不走 JDK `ServiceLoader`**，资源目录固定 `META-INF/z-rpc/`（带连字符） |
| 泛化调用（接口层） | `z-rpc-mock` `GenericService` | 只有 `$invoke(String,String[],Object[])` 与 `default $invokeMap(..)` 两个方法签名，无生产实现、无代理工厂产出它 |
| 指标采集 | `z-rpc-metrics` `Histogram` / `MetricsCollector` / `MetricsReporter` | 类真实存在；但 `MetricsReporter` 无 transport、`src/main` 里也没有启动它的地方 |
| 上下文与异步 | `z-rpc-async` `RpcContext` / `DefaultFuture` | traceId + attachments；`DefaultFuture` 撑起 `Invoker` 侧的同步↔异步桥 |

**已发布构件的口径**：1.0.3（2417288，2026-09-28 上传）与 1.0.4（812743d，2026-09-29 上传）
是从 `src/main` 已修完的那棵树发布的，中央件**已经包含**过去几轮 37 处修复；本仓旧 README 那句
「已发布的字节里一个修复都没进」是 1.0.2 时代的陈述，已过期。当前源码与 1.0.4 唯一未发布的差
是根 pom：`<parent>` 已从 1.0.19 抬到 1.0.21（5dbae8d），且 flatten 声明常开（730bbda）——
这两处只影响下一支构件的 pom 形状，不影响 jar 里的类。

---

## 🏗️ 项目结构

`ls -d z-rpc-*/` 有 **17** 个目录；根 pom 的 `<modules>` 启用 **14 条**，另有两行历史遗留的注释
`<module>z-rpc-examples</module>` / `<module>z-rpc-admin</module>` 和一行重复的
`<module>z-rpc-registry</module>`（registry 本身在启用列表里，注释那行不生效）。
`z-rpc-admin-frontend` 是独立 npm 工程、**不是 Maven 模块**。

```
z-rpc/
├── pom.xml                          # 聚合 pom：parent=z-boot-parent:1.0.21、version=1.0.4、flatten 常开
├── z-rpc-common/                    # URL / Node / RpcException / RpcConstants / ProtocolConstants（Result 在 z-rpc-api）
├── z-rpc-spi/                       # @SPI / @Adaptive / @Activate / ExtensionLoader / ExtensionFactory（+ 两个 factory 实现）
├── z-rpc-api/                       # Invoker / Invocation / RpcInvocation / Result / Filter / Protocol / ProxyFactory 接口 + JdkProxyFactory + Exporter + Listener
├── z-rpc-serialize/                 # 5 个 Serialization 实现 + SerializationFactory（java/json/hessian2/kryo/protobuf）
├── z-rpc-protocol/                  # 26 字节定长头 ZRpcMessageEncoder / ZRpcMessageDecoder + ZRpcProtocol / ZRpcProtocolImpl + ZRpcMessage
├── z-rpc-remoting/                  # RpcServer / RpcClient + 10 字节帧 Encoder/Decoder + RpcServerHandler / RpcClientHandler / RpcRequest / RpcResponse / RpcClientHolder
├── z-rpc-registry/                  # RegistryService + InMemoryRegistryService + RpcRegistry + InMemoryRpcRegistry + ServiceInstance + NotifyListener（+ 两个 .java.disabled）
├── z-rpc-cluster/                   # 5 Cluster + 3 LoadBalance + Directory / AbstractDirectory / StaticDirectory / Router + discovery 子包（InstanceLoadBalancer 一族 + ServiceDiscovery）
├── z-rpc-filter/                    # 4 个 Filter（暂无装配点）
├── z-rpc-mock/                      # GenericService 一个接口
├── z-rpc-metrics/                   # Histogram / MetricsCollector / MetricsReporter
├── z-rpc-async/                     # RpcContext / DefaultFuture
├── z-rpc-core/                      # ServiceConfig / ReferenceConfig / RegistryDirectory + 5 份 cluster 类型副本
├── z-rpc-spring-boot-starter/       # @ZRpcService / @ZRpcReference / @EnableZRpc / ZRpcProperties / 六个 AutoConfiguration / Exporter / Injector / FrameworkInitializer
├── z-rpc-examples/                  # ❌ 不在 reactor：聚合 pom（子模块 user-service / order-service，都是 Spring Boot 应用）
├── z-rpc-admin/                     # ❌ 不在 reactor：Spring Boot 控制台后端，19090（`z-config-client` 依赖注释着）
├── z-rpc-admin-frontend/            # ❌ 不在 Maven：React 18 + AntD 5 + Vite 5 前端，5173
└── _doc/                            # 见文末「文档目录」
```

### 模块与 reactor 状态

| 模块 | 在 reactor | 已上 Central | 里面真实存在的东西 |
|---|---|---|---|
| `z-rpc-common` | ✅ | ✅ 1.0.4 | `URL` / `Node` / `RpcException` / `RpcConstants` / `ProtocolConstants`（`Result` 不在这、在 `z-rpc-api`） |
| `z-rpc-spi` | ✅ | ✅ 1.0.4 | `@SPI` / `@Adaptive` / `@Activate` / `ExtensionLoader` / `ExtensionFactory` / `SpiExtensionFactory` / `AdaptiveExtensionFactory` |
| `z-rpc-api` | ✅ | ✅ 1.0.4 | `Invoker` / `Invocation` / `RpcInvocation` / `Result` / `Listener` / `Filter` / `Protocol` / `ProxyFactory` / `Exporter` 接口 + `JdkProxyFactory` |
| `z-rpc-serialize` | ✅ | ✅ 1.0.4 | 5 个 `Serialization` 注册项（java / json / hessian2 / kryo / protobuf）+ `SerializationFactory` |
| `z-rpc-protocol` | ✅ | ✅ 1.0.4 | 26 字节定长头的 `ZRpcMessageEncoder` / `ZRpcMessageDecoder` + `ZRpcProtocol` / `ZRpcProtocolImpl` / `ZRpcMessage` |
| `z-rpc-remoting` | ✅ | ✅ 1.0.4 | `RpcServer` / `RpcClient` + 10 字节帧的 `RpcMessageEncoder` / `RpcMessageDecoder` / `RpcServerHandler` / `RpcClientHandler` / `RpcRequest` / `RpcResponse` / `RpcClientHolder` |
| `z-rpc-registry` | ✅ | ✅ 1.0.4 | `RegistryService` + `InMemoryRegistryService`、`RpcRegistry` + `InMemoryRpcRegistry` + `ServiceInstance` + `NotifyListener`（`ZConfigRegistry` / `ZkNamingRpcRegistry` 源码是 `.java.disabled`） |
| `z-rpc-cluster` | ✅ | ✅ 1.0.4 | 5 个 `Cluster`、3 个 `LoadBalance`、`Directory` / `AbstractDirectory` / `StaticDirectory` / `Router`；另有 `discovery` 子包 |
| `z-rpc-filter` | ✅ | ✅ 1.0.4 | **4** 个 Filter：`ConsumerTraceFilter` / `MonitorFilter` / `ProviderContextFilter` / `ProviderMonitorFilter` |
| `z-rpc-mock` | ✅ | ✅ 1.0.4 | **1 个接口** `GenericService`，无实现、无代理工厂 |
| `z-rpc-metrics` | ✅ | ✅ 1.0.4 | `Histogram` / `MetricsCollector` / `MetricsReporter` |
| `z-rpc-async` | ✅ | ✅ 1.0.4 | `RpcContext`（traceId + attachments）/ `DefaultFuture` |
| `z-rpc-core` | ✅ | ✅ 1.0.4 | `ServiceConfig` / `ReferenceConfig` / `RegistryDirectory` + `com.zifang.z.rpc.cluster` 包的 5 份副本 |
| `z-rpc-spring-boot-starter` | ✅ | ✅ 1.0.4 | `@ZRpcService` / `@ZRpcReference` / `@EnableZRpc` / `ZRpcProperties` + 六个 AutoConfiguration + Exporter / Injector / FrameworkInitializer |
| `z-rpc-examples` | ❌ 注释掉 | ❌ 404 | 聚合 pom（子模块 `user-service` / `order-service`） |
| `z-rpc-admin` | ❌ 注释掉 | ❌ 404 | Spring Boot 后端，监听 **19090**（`server.port`） |
| `z-rpc-admin-frontend` | ❌ 不是 Maven 模块 | ❌ 无 Maven 坐标 | React 18 + AntD 5 + Vite 5，监听 **5173** |

> 三个"不在 reactor"的目录**都没有** `maven.deploy.skip`（本仓 `<modules>` 直接把它们排除掉，
> 反应堆不遍历、deploy 阶段自然不会走到它们；根 pom 的 `<dependencyManagement>` 注释里也明写
> "不给 `z-rpc-admin` 钉 `${project.version}`"）。

### 同名包 `com.zifang.z.rpc.cluster` 的重复份

`z-rpc-core/src/main/java/com/zifang/z/rpc/cluster/` 下有 `Cluster` / `Directory` / `AbstractDirectory` /
`Router` / `FailoverCluster` 五个类型，与 `z-rpc-cluster` 里的**同名同包**：core 那份是修复前的快照，
唯一有差异的正是 `Cluster.java` —— 它少了 `@SPI("failover")` 那两行（cluster 那份有）。
依赖 `z-rpc-core` 的进程里 core 自己的 classes 在 classpath 上排第一，加载到的就是**没注解的那份**，
于是 `ExtensionLoader.getExtensionLoader(Cluster.class)` 在 core 及其下游（含 starter）照样抛
`is not annotated with @SPI`（报告 N12，两条 `bug_` 用例钉着；删哪一边是模块边界决策，见报告 §8 第 7 条）。

---

## 🔧 技术栈

| 层级 | 技术 | 版本来源 |
|------|------|----------|
| 语言 / 运行时 | Java 8 | 根 pom `<compile.version>8</compile.version>` + `maven-compiler-plugin:3.8.0`（父链下发的是 `maven.compiler.source/target`，本仓这一格不删） |
| 框架 | Spring Boot 2.7.18 | 由 `z-boot-parent` → `z-boot-dependencies` 供；`z-rpc-spring-boot-starter` 与 `z-rpc-admin` 里字面写 `2.7.18` |
| 网络层 | Netty 4.1.138.Final | 由地板 import 的 `io.netty:netty-bom` 供；`z-rpc-remoting` 直引 `netty-all` |
| 序列化 | Hessian 4.0.66 · Kryo 5.5.0（+ `objenesis` 顶到 3.3）· protobuf-java 3.25.5 · Jackson 2.18.9 | `hessian` / `kryo` 本仓自钉（地板 0 条）；Jackson 由 `z-util-core` 传递来 2.18.9、地板 jackson-bom 是 2.18.6 更低 ⇒ 本仓按坐标写直接条目顶住；protobuf 走地板 |
| 日志 | slf4j 1.7.36 · log4j-api / log4j-core / log4j-to-slf4j **2.17.2**（本仓按坐标钉住） | 地板把 log4j 系列钉在 2.25.4，本仓基线 tree 是 2.17.2（`core` / `remoting` / `starter` 直引），抬 log4j 要连 Java 8 字节码兼容一起验 ⇒ 迁移零漂移 |
| SPI | 自研 `ExtensionLoader`，资源目录 `META-INF/z-rpc/`（带连字符） | `z-rpc-spi/ExtensionLoader.java` |
| 测试 | JUnit 5.8.2 + `junit-platform` 1.8.2 + `opentest4j` / `apiguardian`（跟着 jupiter 原值） | 地板 `junit-bom` 是 5.9.3，会动到用例发现与计数 ⇒ 逐坐标钉住基线；`maven-surefire-plugin:2.22.2` 显式钉版本（Maven 3.6.x 默认 2.12.4 不认识 JUnit 5） |
| 构建 | Maven（后端）· flatten-maven-plugin 1.5.0（`flattenMode=oss`，常开、不挂 `central` profile） | 本仓自声明补齐：`z-boot-parent` 的 flatten 声明带 `<inherited>false</inherited>`，父链传不下来 |
| 发布 profile | `-Pcentral`：补 `maven-source-plugin` / `maven-javadoc-plugin` / `maven-gpg-plugin` + `central-publishing-maven-plugin:0.7.0`，把 `maven-deploy-plugin` 关掉 | 日常 `mvn install` / `mvn test` **不需要**带这个 profile |
| 控制台前端 | React 18.3 · AntD 5.21 · Vite 5.4 · TypeScript 5.5 · zustand 4.5 · echarts 5.5 · dayjs · axios | `z-rpc-admin-frontend/package.json` |

---

## 🚀 快速开始

### 编译

```bash
mvn -B clean install -DskipTests
```

第三方版本走父链：`z-boot-parent:1.0.21` → `z-boot-dependencies`（地板）+ `z-boot-fleet`（兄弟仓权威表）。
本仓 `<properties>` 只保留五格自钉：`compile.version` / `hessian.version` / `kryo.version` / `log4j.version` / `junit.version`；
其余（netty / spring-boot / z-util / protobuf / slf4j / jackson）由父链下发。
若构建报找不到 parent，先确认本地/镜像能解析到 `io.github.yuku123:z-boot-parent:1.0.21`。

### 5 分钟接入：Spring Boot 注解驱动（最常见）

`z-rpc-spring-boot-starter` 只带 `spring-context`，**不含 Web**；下面的 `@RestController` 示例
需要自己引入 `spring-boot-starter-web`。

```xml
<dependency>
    <groupId>io.github.yuku123</groupId>
    <artifactId>z-rpc-spring-boot-starter</artifactId>
    <version>1.0.4</version>
</dependency>
```

**服务端（提供方）** —— `@ZRpcService` 本身已元标注 `@Component`，但**光打它还不够**：
注入器/导出器由 `@EnableZRpc` 装配，所以启动类上要有 `@EnableZRpc`（报告 §7 第 4/16 行）。

```java
// 1. 定义接口（提供方与消费方共享）
public interface UserService {
    User findById(long id);
    List<User> findByIds(List<Long> ids);
}

// 2. 服务端实现（`@ZRpcService` 的 11 个属性里，只有 `interfaceClass` 与 `version` 有落点）
@ZRpcService(interfaceClass = UserService.class, version = "1.0.0")
public class UserServiceImpl implements UserService {
    @Override public User findById(long id) { return new User(); }
    @Override public List<User> findByIds(List<Long> ids) { return new ArrayList<User>(); }
}

// 3. 启动类
@SpringBootApplication
@EnableZRpc(scanBasePackages = "com.example.user")
public class UserProviderApp {
    public static void main(String[] args) {
        SpringApplication.run(UserProviderApp.class, args);
    }
}
```

`application.yml` —— 下面这份**只列 `ZRpcProperties` 真实存在的 key**（前缀 `z.rpc`，
六个嵌套 `application` / `registry` / `server` / `consumer` / `provider` / `protocol`，共 33 个叶子）：

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
      name: z-rpc            # Protocol SPI 目前只有这一项
      port: 20880
      serialization: java    # 线上真正用的是 Java 原生序列化，见"协议"一节
    provider:
      timeout: 3000
      threads: 200
      delay: 0
      weight: 100
      async: false
      token: false
      serialization: java
```

> 上一版 README 列过的 `z.rpc.port` / `z.rpc.serialize` / `z.rpc.cluster` / `z.rpc.loadbalance` /
> `z.rpc.retries` / `z.rpc.timeout` / `z.rpc.group` / `z.rpc.filter.enabled` / `z.rpc.metrics.*` /
> `z.rpc.client.*` / `z.rpc.server.threads.boss|worker|business` / `z.rpc.server.channel.*` /
> `z.rpc.registry.group|username|password` / `z.rpc.mock-default` / `z.rpc.rest-port`
> **都不是 `ZRpcProperties` 的字段**；Spring Boot 的 relaxed binding 对未知 key 静默忽略，
> 照旧文档写不会报错、只会无声丢掉。上面这份 yml 与下面「配置项到底哪些有落点」那张表都是
> 反射 `ZRpcProperties` 逐 getter 读出来的。

**客户端（消费方）**

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

`url` 是**直连**，目前这是唯一被跑通的消费方式。注册中心那条路还没接线：
`z.rpc.registry.*` 四个 key 都真实存在（`address` / `namespace` / `type` / `enabled`），
但 core 侧 `ServiceConfig.registerToRegistry()` 里加载注册中心那一行是**注释掉的**
（源文件里那行 `// registryService = new com.zifang.z.rpc.registry.ZConfigRegistry(registry);`），
字段恒 null，随后那句 `registryService.register(..)` 必抛 NPE 并被外层 `catch (Exception)`
吞成一行日志，`export()` 照样"成功"（报告 N13 / §2）。
`z-config` 与 ZK 的实现类在本仓是 `.java.disabled` 后缀存在，源码里也依赖 `com.zifang:z-config-client`
（该构件在 Central 未发布，见 `z-rpc-admin/pom.xml` 里同样注释掉的那一段）。

端到端证据（两个真 JVM：Ubuntu 18.04 / JDK 1.8.0_362）：`order-service` 经
`@ZRpcReference(url = "zrpc://127.0.0.1:20880")` 取回**只存在于 `user-service` 进程内存里的**
`userName='Alice'`（报告 §9.5–§9.8）。

### API 直连（不依赖 Spring）

`RpcClient` 只发 `RpcRequest`、**不给接口代理**；要拿到能按接口调的 `Invoker`，走 `Protocol` SPI：

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
Object value = r.getValue();
invoker.destroy();
server.stop();
```

`registerService(Class, Object)` 注册的是**裸接口名**；要把版本并进路由键得用
`register(Class, Object, String version)`（键形如 `接口全名:version`，`RpcServer.serviceKey(..)`
就是这条规则的出处）。两条路都不合版本那条的坑见报告 N14。
`Invoker.invoke(..)` 声明 `throws Throwable` —— 业务异常不保证被包成 `RpcException`，
上层按 `RpcException` catch 会漏（报告 N16 最后一条）。

### 声明式 API（`ServiceConfig` / `ReferenceConfig`）

**所有 setter 都返回 `void`**（`ServiceConfig` 20 个 setter、`ReferenceConfig` 同理），
方法名也是 `setInterfaceClass` 而不是 `setInterface`，所以链式写法不可能成立：

```java
ServiceConfig<UserService> service = new ServiceConfig<UserService>();
service.setInterfaceClass(UserService.class);
service.setRef(new UserServiceImpl());
service.setPort(20880);
service.export();                      // ⚠ bind 失败也返回，见下方警告

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

### 跑示例（两个 Spring Boot 应用，跨进程 E2E）

`z-rpc-examples` 不在 reactor 里，且它的两个子模块都**不在 Maven Central**
（`io.github.yuku123:z-rpc-examples:1.0.4` 与 `z-rpc-examples/user-service` / `order-service` 的 pom 均 404）；
本仓根 pom 的 `<dependencyManagement>` 里也没有给 `z-rpc-examples` 钉 `${project.version}`。
在干净机器上 `mvn -B spring-boot:run` 需要先能解析到本仓构件。跑通后的形状（已实测）：

```bash
# 终端 1（提供方）
cd z-rpc-examples/user-service && mvn -B spring-boot:run     # HTTP 20881 · RPC 20880
# 终端 2（消费方）
cd z-rpc-examples/order-service && mvn -B spring-boot:run    # HTTP 20890，`z.rpc.server.enabled: false`

curl "http://localhost:20890/order/create?userId=1&amount=99.9"
curl "http://localhost:20890/order/user/1"
```

返回体里的 `userName` 只存在于 `user-service` 进程的内存中 —— 能取回即证明跨进程调用成立：

```json
{"id":1001,"userId":1,"userName":"Alice","amount":99.9,"status":"CREATED"}
```

### 控制台

```bash
cd z-rpc-admin && mvn -B spring-boot:run                     # http://localhost:19090
cd z-rpc-admin-frontend && pnpm install && pnpm dev          # http://localhost:5173
```

上一版 README 写 admin 在 9090 —— `z-rpc-admin/src/main/resources/application.yml` 里是 **19090**。
`z-rpc-admin` 不在 reactor，且它 pom 里的 `z-config-client` 依赖是**注释状态**（该构件未上 Central），
单独跑 admin 时不需要它也能起（`spring-boot-maven-plugin:2.7.18` 已声明 mainClass
`com.zifang.z.rpc.admin.ZRpcAdminApplication`）。

---

## 🧩 SPI 扩展机制

`@SPI` 只能标在**接口**上，值是该接口的默认实现 key（`@SPI("failover")`）；`Scope` 枚举与
`@Spi(scope = ..)` 这种写法在仓库里不存在（旧 README 的 Case 6 就是这么写的，编译不过）。

扩展点资源目录是 **`META-INF/z-rpc/`**（带连字符）。上一版在"微内核 + SPI"一节写的
`META-INF/zrpc/` 在仓库里不存在，两处混用会让自定义扩展静默加载不到。
`ExtensionLoader` **不走 JDK `ServiceLoader`**（`src/main` 里 `ServiceLoader` 命中 0 行），
它自己读上面那个目录下的清单文件；每一行形如 `key=全类名`。

| 扩展点 | 定义位置 | 清单文件所在 | 已注册实现 | key |
|---|---|---|---|---|
| `com.zifang.z.rpc.api.Protocol` | `z-rpc-api` | `z-rpc-protocol/src/main/resources/META-INF/z-rpc/` | **1** | `z-rpc` |
| `com.zifang.z.rpc.api.ProxyFactory` | `z-rpc-api` | `z-rpc-api/src/main/resources/META-INF/z-rpc/` | **1** | `jdk` |
| `com.zifang.z.rpc.cluster.Cluster` | `z-rpc-cluster` / `z-rpc-core`（两份同名，见上） | `z-rpc-cluster/src/main/resources/META-INF/z-rpc/` | **5** | `failover` / `failfast` / `failsafe` / `failback` / `broadcast` |
| `com.zifang.z.rpc.loadbalance.LoadBalance` | `z-rpc-cluster` | `z-rpc-cluster/src/main/resources/META-INF/z-rpc/` | **3** | `random` / `roundrobin` / `leastactive` |
| `com.zifang.z.rpc.filter.Filter` | `z-rpc-api` | `z-rpc-filter/src/main/resources/META-INF/z-rpc/` | **4** | `consumer-trace` / `monitor` / `provider-context` / `provider-monitor` |
| `com.zifang.z.rpc.serialize.Serialization` | `z-rpc-serialize` | `z-rpc-serialize/src/main/resources/META-INF/z-rpc/` | **5** | `java` / `json` / `hessian2` / `kryo` / `protobuf` |
| `com.zifang.z.rpc.registry.RegistryService` | `z-rpc-registry` | `z-rpc-registry/src/main/resources/META-INF/z-rpc/` | **1** | `in-memory` |

`RegistryService` 清单里 `#z-config` 与 `#zknaming` 两行是**注释状态**，
对应源码 `ZConfigRegistry.java.disabled` / `ZkNamingRpcRegistry.java.disabled` 也在磁盘上
（缺 `com.zifang:z-config-client` 依赖）；恢复编译只需去掉清单里那两行注释、把源码后缀改回 `.java`。

**加一个自定义 `RegistryService`**：

```java
public class MyRegistryService implements RegistryService {
    @Override public void register(URL url) {}
    @Override public void unregister(URL url) {}
    @Override public void subscribe(URL url, NotifyListener listener) {}
    @Override public void unsubscribe(URL url, NotifyListener listener) {}
    @Override public List<URL> lookup(URL url) { return new ArrayList<URL>(); }
    @Override public void destroy() {}
}
```

`META-INF/z-rpc/com.zifang.z.rpc.registry.RegistryService`：

```
in-memory=com.zifang.z.rpc.registry.InMemoryRegistryService
my-registry=com.example.MyRegistryService
```

`RegistryService` 接口上已有 `@SPI("in-memory")`，新增实现不需要改接口。加载侧一切正常
（`getExtension("my-registry")` 拿得到实例、按名字缓存成单例），**只是 core 侧不会去加载它** ——
要用得自己 `setRegistryService(..)` 手工注入 `ServiceConfig` / `ReferenceConfig`
（这正是报告 `registryModeWorksWhenHandWired` 跑通的方式）。

**加一个自定义 Filter**：

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

`META-INF/z-rpc/com.zifang.z.rpc.filter.Filter` 加一行 `my=com.example.filter.MyFilter`。

**但当前没有任何一处生产代码装配过滤器链**，所以这个 Filter 写出来不会被调用（报告 N19，10 条全绿钉着）：
`getActivateExtension` 在 `src/main` 的调用点是 **0**（唯一的定义在 `ExtensionLoader` 自己文件里）；
库里没有"链对象"，责任链只有一个返回 `List` 的加载器，没有任何东西按 order 把 `Invoker` 包起来；
真实服务端 `RpcServerHandler` 是拿 `RpcRequest` 直接反射调 POJO。另外 `getActivateExtension()` 还叠了一个
加载时序 bug：`cachedActivates` 只在 `loadExtensionClasses()` 里填，冷 loader 上它永远返回空，
必须先碰一次别的 API 才有内容（报告 H5）。`@Activate` 的三个属性（`group` / `value` / `order`）真实存在
且运行时读得到，只是没人用。

---

## 📡 协议与帧格式

**线上跑的帧是 10 字节**（`z-rpc-remoting/RpcMessageEncoder.java`）：
`ZRPC`(4B 魔数) + version(1B) + msgType(1B) + bodyLen(4B)，body 走 **Java 原生序列化**（`AC ED` 开头）；
`RpcMessageDecoder` 继承 `LengthFieldBasedFrameDecoder(maxFrame=10MB, lengthFieldOffset=6, lengthFieldLength=4)`，
`HEADER_SIZE = 10`。这是唯一被真实 socket 往返验证过的一套（报告编解码轮 / `RpcMessageCodecTest` 18 条）。

`z-rpc-protocol` 里另有**第二套离线编解码器**，定长头 **26 字节**
（`ProtocolConstants.HEADER_LENGTH = 26`；上一版写 24，那 2 字节的差会让半包停在 24/25 字节时
抛 `IndexOutOfBoundsException`，已修，报告 H1）：

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

别把两边读数混着读：`FrameCodecTest` 的 17 条钉的是 26 字节那套，
生产 `RpcClient` / `RpcServer` 走的是 10 字节那套。
**`dubbo` 与 `http` 两种协议没有实现**（`Protocol` SPI 只有 `z-rpc` 一个 key），
所以旧 README 的"多协议端口：单服务同时监听 zrpc + http"与"通过 Rest 调用（dubbo 兼容）"不成立。

---

## 🔍 配置项到底哪些有落点（33 个 key 逐个判）

`application.yml` 的 key **都真实存在**（`ZRpcProperties` 反射遍历得 33 个叶子），但"存在"和
"起作用"是两件事。这张表按**谁真的读了它**分三档，判据全在 `ConfigLandingContractTest` 里，不是数出来的：

- **真用**：值流进了装配决策或构造参数（`z.rpc.enabled` 与 `z.rpc.registry.enabled` / `server.enabled`
  走 `@ConditionalOnProperty` 与 `if` 分支决定 bean 在不在；`server.host` / `server.port` 是
  `new RpcServer(..)` 的实参）。
- **只进横幅**：只在 `ZRpcFrameworkInitializer` 的 `log.info` 里被读了一次，打印完就没人管 ——
  改它只会改变启动日志的字样。
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

**5 真用 / 10 只进横幅 / 18 没人读。**
这仓的 yaml 面比它的行为面大得多：上一版 README 那些**根本不存在**的 key 至少还有报错的机会，
而这一张表里"没人读"的 18 个是**真实存在、静默无效**，更难发现。
默认值与类型不是抄的，是用例里 `newInstance()` 之后逐个 getter 读出来的，写错一个字这条就红。

---

## 🔍 注解属性到底哪些有落点

这张表是照 `ZRpcServiceExporter` 与 `ZRpcReferenceInjector` 的实测写的，不是照注解声明写的
（报告 H8 / H9 / N15）：

| 注解 | 属性总数 | 有落点 | 没有落点 |
|---|---|---|---|
| `@ZRpcService` | 11 | `interfaceClass` / `version` | `interfaceName` / `group` / `weight` / `delay` / `timeout` / `retries` / `loadbalance` / `cluster` / `async` |
| `@ZRpcReference` | 17 | `interfaceName` / `version` / `group` / `timeout` / `retries` / `loadbalance` / `cluster` / `registry` / `url` / `async` / `oneway` | `interfaceClass` / `check` / `lazy` / `connections` / `client` / `serialization` |

判据（不是文字，是跑得动的）：**有落点 = 这个属性在对应处理器源码里以 `annotation.<属性>()` 被读到
至少一次；没有落点 = 0 次**。两列的名字合起来必须**正好等于** `ZRpcService.class.getDeclaredMethods()`
的名字集合（**逐个名字比，不比个数**），"属性总数"那一列也必须等于反射出来的数量 —— 全在
`ConfigLandingContractTest` 里。

- `@ZRpcService` 的 `interfaceClass` 算有落点：不写则取实现类的唯一接口，多接口全导。剩下 9 个放不进去 ——
  `RpcServer` 的三个 `register*` 重载（`registerService(Class, Object)` / `registerService(String, Object)` /
  `register(Class, Object, String)`）里**没有任何 int/long 参数位**可放，这条也是反射量的。
- **`@ZRpcReference` 的 `interfaceClass` 是本轮从"有落点"列挪出来的**：注入器调的是
  `buildReferenceConfig(annotation, field.getType())`，整个文件里 `annotation.interfaceClass()` 命中 **0** 次 ——
  接口取的是**字段声明类型**，注解里那个 `Class` 从来没被读过。证据用例：字段声明成 `Alpha`
  而注解写 `interfaceClass = Beta.class`，`ReferenceConfig.getInterfaceClass()` 回来的是 `Alpha`
  （`bug_annotationInterfaceClassIsNeverRead`）。
- `check` / `lazy` / `connections` / `client` / `serialization` 这 5 个在 `ReferenceConfig` 里
  **field / setter / getter 全 0 命中**（上一版这里写的是"5 个里 4 个"，实测是 5 个都没有）；
  搬过去的 `timeout` / `cluster` / `loadbalance` / `retries` 下游没人读。

`@ZRpcReference` 的 `check` 默认 `true` 完全不起作用：对一个必连不上的 `zrpc://127.0.0.1:1`，
注入器照样返回 JDK 代理，故障被推迟到首次调用（报告 `bug_deadUrlStillYieldsProxy`）。

**而"当场就失败"的那一类更安静**：`injectField` 的 `catch` 里只有一行 `log.error`，所以字段声明类型
不是接口（`ReferenceConfig.checkConfig()` 抛 `Interface class must be an interface`）、或 `url` 的端口
写成一个词（`URL.valueOf` 抛 `NumberFormatException`）时，**容器照样启动成功、bean 照样交付，
只有那个字段是 `null`** —— 注入器上没有任何地方留下"刚才有一次没注入上"的可编程痕迹
（`bug_injectionFailureIsSwallowedIntoANullField`、`bug_swallowedFailureLeavesNoProgrammaticTrace`）。
另一个方向是同接口的两个字段：两支各拿一个 JDK 代理，而 `registerAsBean` 按接口全名去重 ⇒
**容器里只有第一个**，`@Autowired` 到的和写在第二个字段里的不是同一个对象
（`bug_secondFieldOfSameTypeKeepsAProxyTheContainerNeverSees`）。

---

## 🔌 admin / examples HTTP 端点

`z-rpc-admin` 与 `z-rpc-examples` 都**不在 reactor、也不在 Central**（1.0.4 pom 均 404），
列在这里是为了让"跑控制台 / 跑示例"这两步能落到具体 URL 上。

| 路径 | 归属 | Controller |
|------|------|------------|
| `GET /api/admin/dashboard/overview` | `z-rpc-admin` | `AdminController` |
| `GET /api/admin/services` | 同上 | 同上 |
| `GET /api/admin/services/{serviceKey}` | 同上 | 同上 |
| `GET /api/admin/providers` | 同上 | 同上 |
| `GET /api/admin/services/{serviceKey}/metrics` | 同上 | 同上 |
| `GET /api/admin/traces` | 同上 | 同上 |
| `POST /api/admin/metrics/push` | 同上 | 接收 `MetricsPushRequest`（**库里没有代码往这里发**，见"还没做"） |
| `POST /api/admin/providers/register` | 同上 | 同上 |
| `POST /order/create` | `z-rpc-examples/order-service` | `OrderController` |
| `GET /order/user/{userId}` | 同上 | 同上 |

---

## 🧪 测试

```bash
mvn -B clean test                          # 反应堆 14 个模块
cd z-rpc-admin && mvn -B clean test        # admin 不在 reactor，要单独跑
```

**最近一次实跑读数**（2026-09-27 判别腿轮之后；本机 macOS/JDK 25/Maven 3.9.14 与
Ubuntu 18.04/JDK 1.8.0_362/Maven 3.6.0 各一跑，逐模块读数相同、只有耗时不同）：

- **571 条用例 = 反应堆 559 + admin 12**
- 55 个测试源文件（555 个 `@Test` / `@ParameterizedTest` 标注）/ 46 个 surefire 报告类
- `fail / error / skip` 全 0

> 上一版这里写的 "312 + 78 + 14 + 31 + 22 + 18 + 15 PASS" 在仓库里没有任何对应产物，已按实测替换。

自 2026-09-27 那轮收口（`647d6f5`）到本版所在线（`5dbae8d`）共 **15 次提交**，
全部落在 `pom.xml` 或 `_doc/`（迁入 `z-boot-parent` / 抬 parent 与仓版本 / 补 flatten 声明 /
`z-util` 抬到 1.0.13 / netty 与 spring-boot 口径对齐），
`*/src/main/**` 与测试代码 **0 处改动**，因此 571 / 55 / 555 三个读数在本版仍然成立；
下一次跑测试请复核。逐模块分布与被钉住的缺陷清单见
[`_doc/002_测试报告.md`](_doc/002_测试报告.md)。

**两条硬约束**（写进报告贡献规约）：

- **不要 `mvn install`**（会造出与反应堆分叉的构件）。
- **不要用 `-pl` 单模块跑测试**（会从 `~/.m2` 解析到旧构件，产生假红）。

---

## ❌ 还没做（这些是实测结论，不是计划）

下表每一条都是"旧 README 主张 → 现场搜索反证"的形状，编号对应
[`_doc/002_测试报告.md`](_doc/002_测试报告.md)：

| 旧 README 的主张 | 实测 |
|---|---|
| `@ZRpcReference(mock = "true")`、`mockReturn`、`MockFilter`、`mock-default` | `@ZRpcReference` 的 17 个属性里 `mock` 出现 **0** 次；`MockFilter` 类不存在；`ZRpcProperties` 没有 `mock-default` key；`z-rpc-mock` 整模块只有 `GenericService` 一个接口 |
| 泛化调用 `client.createGenericProxy(name, version)` | 没有任何 `createGenericProxy`；`GenericService` 有 `$invoke` / `default $invokeMap` 两个方法，但**没有生产实现、没有代理工厂产出它**；`$invokeMap` 的参数类型是取实参运行时类，子类会被报成子类、`null` 只能报成 `java.lang.Object`（报告 N22） |
| 注册中心 z-config / ZK Naming / "未启动时降级为 InMemoryRegistry" | `RegistryService` SPI 只有 `in-memory` 一项；`ZConfigRegistry` / `ZkNamingRpcRegistry` 源码是 `.java.disabled` 且已从清单里注释掉；core 侧加载注册中心的那行注释着，字段恒 null（N13）；没有任何"降级"代码路径 |
| Prometheus 集成（`z_rpc_*` 指标）、对接 SkyWalking / Jaeger | 全仓 `src/main` 里 `z_rpc` / `prometheus` 命中 **0** 行；链路追踪只有本地 `traceId` 塞 MDC，没有 exporter |
| 指标上报 `Filter → MetricsCollector → MetricsReporter → HTTP POST → z-rpc-admin` | `MetricsReporter` 的"上报"只打日志 —— 直接扫它的 class 字节常量池，`java/net`、`Socket`、`Http`、`admin` **一个都不出现**（N20 `bug_reportingHasNoTransport`）；且 `src/main` 里没有任何地方启动它。admin 侧确实有 `POST /api/admin/metrics/push`，但没人发 |
| 指标表里的 `ConnectionManager` / `HeartbeatReconnector` | 两个类都不存在。真实存在的只有 `Histogram` / `MetricsCollector` / `MetricsReporter`（`z-rpc-metrics`）与 `MonitorFilter` / `ProviderMonitorFilter`（`z-rpc-filter`） |
| 健康检查：心跳 + 离线剔除 | 目录层订阅到空通知时**旧 provider 全部留着**，服务下线在客户端永不生效（N17 `bug_emptyNotificationIsIgnored`）；一次失败把该 invoker 永久打掉且不重建（N17 `bug_oneFailureBricksTheInvoker`） |
| 连接池复用 / `ReferenceCountExchangeClient` / 时间轮 `HashedWheelTimer` | 三个名字在 `src/main` 都不存在。`z.rpc.consumer.connections` 属性有，注入器直接丢弃（H8 余下五项） |
| 灰度发布按 version / group 路由、`z.rpc.weight.<group>` 权重表 | `weight` 是个 map 形 key，`ZRpcProperties` 里只有 `provider.weight`（int）；`group` 在注册中心配置里不存在；声明式导出走的是不带版本的那条注册路径，**版本在两侧各丢一次**，而 `RegistryDirectory` 以 `host:port` 为键，同地址两个版本会塌成一条（N14） |
| 集群容错**六种** / 负载均衡**五种** | `Cluster` SPI 清单只有 5 条（**没有 `available`**）；`LoadBalance` SPI 清单只有 3 条（**没有 `SmoothWeightedRR` / `ConsistentHash`**，`forking` / `consistenthash` 在任何 SPI 清单里都搜不到）。`FailoverCluster` 语义有五处和名字不符，用之前先读报告 N16 |
| 单服务同时监听 `zrpc + http` / Rest 调用（dubbo 兼容） | `Protocol` SPI 只有 `z-rpc` 一个 key，**没有 `dubbo` 也没有 `http`** |
| 性能：单连接 24,000 QPS / P99 1.2ms / 异步 145,000 QPS | 仓内**没有任何基准产物**支撑这些数字（没有 JMH、没有压测脚本、没有结果文件），已从 README 删除；要性能数据请先在 `_doc/` 里放一份可复现的压测记录 |

---

## 🛠️ 贡献

改 `src/main` 请同步 `_doc/002_测试报告.md` 的台账：报告里每个哈希、行号、计数都要能用同一条命令复算。
新增 SPI 扩展名要同时补 `META-INF/z-rpc/<完整接口类名>` 清单与 `ConfigLandingContractTest` 的两张落点表，
否则 README 与代码会漂移。抬版本或改 `<parent>` 记得看根 pom 里那段
`<!-- 删掉的键 ... -->` 与 `<!-- ⚠ 这一条必须在本仓自己声明 ... -->` 注释——
`flatten-maven-plugin` 声明、`compile.version`、`log4j.version`、`junit.version`、`hessian.version`、`kryo.version`
这五格是**故意留**在本仓的，删掉就当场悬空。

---

## 📄 License

许可证见仓库根 [`LICENSE`](LICENSE)；`LICENSE` 首行即 `MIT License`，根 pom `<licenses>` 声明 MIT。
（上一版底部写的 "Apache License 2.0" 与徽章里的 Apache 是错的。）

_Maintained by the z-opc-foundation organization._

---

## 文档目录

本项目文档统一收口在 `_doc/` 下：

- [`_doc/001_arch/`](_doc/001_arch/) — 架构文档：
  - [`00-overview.md`](_doc/001_arch/00-overview.md) — 项目总览
  - [`01-module-structure.md`](_doc/001_arch/01-module-structure.md) — 模块结构
  - [`技术实现.md`](_doc/001_arch/技术实现.md) — 技术实现细节
  - [`z-rpc-admin-frontend.md`](_doc/001_arch/z-rpc-admin-frontend.md) — 控制台前端说明

- [`_doc/002_测试报告.md`](_doc/002_测试报告.md) — 缺陷台账（C/H/M/R/N 编号、37 处 `src/main` 改动、
  跨平台复跑读数）；这份文件位于 `_doc/` 根而不是 `002_deploy/` 之下，`002_` 前缀是历史遗留，
  本 README 各处 `报告 Nxx / Hx / §x.y` 指向的都是它。

- [`_doc/002_deploy/`](_doc/002_deploy/) — 目前为空目录：本仓没有需要收口在这里的部署 SQL / k8s 清单。

- [`_doc/003_script/`](_doc/003_script/) — 运维脚本：
  - [`deploy_maven_center.sh`](_doc/003_script/deploy_maven_center.sh) — 上传 Maven Central（走 `-Pcentral` profile）
  - [`install-settings.sh`](_doc/003_script/install-settings.sh) — 装 `~/.m2/settings.xml`
  - [`push.sh`](_doc/003_script/push.sh)

- [`_doc/004_skill/`](_doc/004_skill/) — 目前为空目录，暂无 AI skill 定义。

**没有 `deploy/` / `k8s/` / `Dockerfile` / `docker-compose.yml` / `Makefile`**
（本仓根目录实测：`find . -maxdepth 4 -iname 'Dockerfile*' -o -iname 'docker-compose*'` 空、
`ls -d deploy/ k8s/` 均不存在）。上一版 README 的 "🐳 Docker" 一节里那段 Dockerfile 是示意、
仓内没有落盘文件，本版删除该节；要容器化请先按 §贡献 那一节的规约补一份真实文件与说明。
