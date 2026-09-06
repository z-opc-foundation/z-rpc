package com.zifang.z.rpc.demo;

import com.zifang.z.rpc.config.ReferenceConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.zifang.util.core.lang.RandomUtil;

/**
 * 服务消费者示例
 */
public class ConsumerDemo {

    private static final Logger log = LogManager.getLogger(ConsumerDemo.class);

    public static void main(String[] args) throws Exception {

        // 创建引用配置
        ReferenceConfig<HelloService> referenceConfig = new ReferenceConfig<>();

        // 设置服务接口
        referenceConfig.setInterfaceClass(HelloService.class);

        // 设置版本和分组（需要与服务提供者匹配）
        referenceConfig.setVersion("1.0.0");
        referenceConfig.setGroup("default");

        // 设置调用参数
        referenceConfig.setTimeout(3000);
        referenceConfig.setRetries(2);

        // 设置负载均衡策略
        referenceConfig.setLoadbalance("random");

        // 设置集群容错策略
        referenceConfig.setCluster("failover");

        // 设置注册中心（如果服务注册到了注册中心）
        // referenceConfig.setRegistry("127.0.0.1:8084");

        // 如果不使用注册中心，需要手动指定服务地址
        // 这里假设服务运行在本地 20880 端口
        referenceConfig.setRegistry(null); // 不使用注册中心

        // 获取服务代理
        // 注意：这里需要手动创建直接连接的 invoker，因为 ReferenceConfig 目前只支持注册中心模式
        // 为了简化演示，我们先使用直接连接的方式

        log.info("Creating direct connection to provider at localhost:20880");

        // 使用直接调用方式
        HelloService helloService = createDirectProxy("localhost", 20880);

        log.info("Service proxy created successfully!");

        try {
            // 测试 sayHello 方法
            log.info("Testing sayHello...");
            String result = helloService.sayHello("Z-RPC");
            log.info("sayHello result: {}", result);

            // 测试 add 方法
            log.info("Testing add...");
            int sum = helloService.add(10, 20);
            log.info("add result: {}", sum);

            // 测试 getUser 方法
            log.info("Testing getUser...");
            User user = helloService.getUser(1001L);
            log.info("getUser result: {}", user);

            // 测试 createUser 方法
            log.info("Testing createUser...");
            User newUser = new User();
            newUser.setName("New User");
            newUser.setAge(30);
            newUser.setEmail("newuser@example.com");
            User createdUser = helloService.createUser(newUser);
            log.info("createUser result: {}", createdUser);

            log.info("All tests passed!");

        } catch (Exception e) {
            log.error("Test failed: {}", e.getMessage(), e);
        }

        log.info("Press any key to exit...");
        System.in.read();
    }

    /**
     * 创建直接连接的代理（不经过注册中心）
     */
    private static HelloService createDirectProxy(String host, int port) {
        // 这里简化实现，实际应该使用动态代理
        // 为了演示，我们返回一个手动实现的代理
        return new HelloService() {
            @Override
            public String sayHello(String name) {
                // 直接通过 RPC 客户端调用
                try {
                    com.zifang.z.rpc.remoting.RpcClient client =
                            new com.zifang.z.rpc.remoting.RpcClient(host, port);

                    com.zifang.z.rpc.remoting.RpcRequest request =
                            new com.zifang.z.rpc.remoting.RpcRequest();
                    request.setRequestId(RandomUtil.uuid());
                    request.setInterfaceName(HelloService.class.getName());
                    request.setMethodName("sayHello");
                    request.setParameterTypes(new Class[]{String.class});
                    request.setArguments(new Object[]{name});

                    com.zifang.z.rpc.remoting.RpcResponse response = client.sendRequest(request);
                    client.close();

                    if (response.getException() != null) {
                        throw response.getException();
                    }
                    return (String) response.getResult();
                } catch (Exception e) {
                    throw new RuntimeException("RPC call failed: " + e.getMessage(), e);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public int add(int a, int b) {
                return callMethod("add", new Class[]{int.class, int.class}, new Object[]{a, b});
            }

            @Override
            public User getUser(Long id) {
                return callMethod("getUser", new Class[]{Long.class}, new Object[]{id});
            }

            @Override
            public User createUser(User user) {
                return callMethod("createUser", new Class[]{User.class}, new Object[]{user});
            }

            @SuppressWarnings("unchecked")
            private <T> T callMethod(String methodName, Class<?>[] paramTypes, Object[] args) {
                try {
                    com.zifang.z.rpc.remoting.RpcClient client =
                            new com.zifang.z.rpc.remoting.RpcClient(host, port);

                    com.zifang.z.rpc.remoting.RpcRequest request =
                            new com.zifang.z.rpc.remoting.RpcRequest();
                    request.setRequestId(RandomUtil.uuid());
                    request.setInterfaceName(HelloService.class.getName());
                    request.setMethodName(methodName);
                    request.setParameterTypes(paramTypes);
                    request.setArguments(args);

                    com.zifang.z.rpc.remoting.RpcResponse response = client.sendRequest(request);
                    client.close();

                    if (response.getException() != null) {
                        throw response.getException();
                    }
                    return (T) response.getResult();
                } catch (Exception e) {
                    throw new RuntimeException("RPC call failed: " + e.getMessage(), e);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
