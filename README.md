# Z-RPC

一个参考 Dubbo 设计的高性能 Java RPC 框架，与 z-config 服务注册中心深度集成。

## 特性

- **Dubbo 风格设计**：参考 Dubbo 的分层架构设计
- **服务注册发现**：集成 z-config 服务注册中心
- **负载均衡**：支持随机、轮询、最少活跃调用等策略
- **集群容错**：支持 Failover、Failfast、Failsafe 等策略
- **Spring Boot 集成**：提供 Spring Boot Starter，注解驱动开发

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.zifang</groupId>
    <artifactId>z-rpc-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置服务提供者

```java
@Service
public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        return "Hello, " + name + "!";
    }
}

// 使用编程式 API
@Service
public class RpcProvider {

    @Autowired
    private HelloService helloService;

    @PostConstruct
    public void export() {
        ServiceConfig<HelloService> config = new ServiceConfig<>();
        config.setInterfaceClass(HelloService.class);
        config.setRef(helloService);
        config.setVersion("1.0.0");
        config.setPort(20880);
        config.setRegistry("127.0.0.1:8084"); // z-config 注册中心
        config.export();
    }
}
```

### 3. 配置服务消费者

```java
@Service
public class RpcConsumer {

    @Autowired
    private HelloService helloService;

    public void test() {
        String result = helloService.sayHello("World");
        System.out.println(result);
    }
}
```

### 4. Spring Boot 配置

```yaml
z:
  rpc:
    enabled: true
    application: my-rpc-app
    registry:
      address: 127.0.0.1:8084
      enabled: true
    server:
      port: 20880
      enabled: true
    consumer:
      timeout: 3000
      retries: 2
      loadbalance: random
      cluster: failover
```

## 项目结构

```
z-rpc/
├── z-rpc-core              # 核心模块
│   ├── annotation          # 注解定义
│   ├── cluster             # 集群容错
│   ├── common              # 公共类
│   ├── config              # 配置类
│   ├── invoke              # 调用抽象
│   ├── loadbalance         # 负载均衡
│   ├── proxy               # 代理工厂
│   ├── registry            # 注册中心
│   └── remoting            # 网络传输
├── z-rpc-spring-boot-starter  # Spring Boot 集成
│   ├── autoconfigure       # 自动配置
│   └── properties          # 配置属性
└── README.md
```

## 核心概念

### 1. Invoker

调用者抽象，代表一个可调用实体。无论是本地调用还是远程调用，都通过 Invoker 接口统一处理。

### 2. Directory

目录接口，维护可调用服务列表。负责从注册中心动态获取服务提供者列表。

### 3. Router

路由接口，用于过滤 invoker 列表。可以实现灰度发布、同机房优先等功能。

### 4. LoadBalance

负载均衡接口，负责从多个服务提供者中选择一个。

### 5. Cluster

集群接口，负责将多个 Invoker 组合成一个可高可用的 Invoker，提供容错能力。

## 与 Dubbo 的对比

| 特性          | Z-RPC    | Dubbo                 |
|-------------|----------|-----------------------|
| 注册中心        | z-config | Nacos/ZooKeeper/Etcd  |
| 协议          | 自定义 TCP  | Dubbo/REST/gRPC       |
| 序列化         | Java 原生  | Hessian/FastJSON/Kryo |
| 负载均衡        | 3种       | 5种                    |
| 集群容错        | 1种       | 6种                    |
| Spring Boot | 原生支持     | 原生支持                  |

## 许可证

MIT License
