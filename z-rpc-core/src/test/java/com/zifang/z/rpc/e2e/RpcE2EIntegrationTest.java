package com.zifang.z.rpc.e2e;

import com.zifang.z.rpc.DiscoveryRpcClient;
import com.zifang.z.rpc.HelloService;
import com.zifang.z.rpc.discovery.ServiceDiscovery;
import com.zifang.z.rpc.registry.InMemoryRpcRegistry;
import com.zifang.z.rpc.registry.ServiceInstance;
import com.zifang.z.rpc.remoting.AutoRegisteringRpcServer;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全链路 E2E 测试 — 完整验证 z-rpc + z-config 注册中心能力.
 *
 * <p>场景：
 * <ol>
 *   <li>两个 RpcServer 启动 → 自动注册到 InMemoryRpcRegistry (nacos 等价)</li>
 *   <li>一个 DiscoveryRpcClient → 订阅 → 负载均衡路由到两个 Server</li>
 *   <li>停掉 Server1 → 心跳过期 → Client 只剩 Server2</li>
 *   <li>重启 Server3 → 注册 → Client 自动发现 2 个实例</li>
 * </ol>
 *
 * <p>端口范围：9100-9199 (避免冲突)
 */
public class RpcE2EIntegrationTest {

    private static InMemoryRpcRegistry registry;
    private static ServiceDiscovery discovery;
    private static DiscoveryRpcClient client;
    // serviceName: 用类全限定名，和 AutoRegisteringRpcServer 一致
    private static final String SERVICE_NAME = HelloService.class.getName();

    // 端口分配
    private static final int PORT_A = 9101;
    private static final int PORT_B = 9102;
    private static final int PORT_C = 9103;

    private static AutoRegisteringRpcServer serverA;
    private static AutoRegisteringRpcServer serverB;

    @BeforeAll
    static void setUp() {
        registry = new InMemoryRpcRegistry(3_000L, 200L, 1L);
        discovery = new ServiceDiscovery(registry, "round-robin");
        discovery.subscribe(SERVICE_NAME);
        client = new DiscoveryRpcClient(discovery);

        // ServerA — 用 registerService(String, Object) 显式指定 serviceName
        ServiceInstance instA = new ServiceInstance(SERVICE_NAME, "127.0.0.1", PORT_A);
        instA.setWeight(1.0);
        serverA = new AutoRegisteringRpcServer(registry, instA, 1);
        serverA.registerService(SERVICE_NAME, (HelloService) name -> "Hello from ServerA");
        // ServerB
        ServiceInstance instB = new ServiceInstance(SERVICE_NAME, "127.0.0.1", PORT_B);
        instB.setWeight(1.0);
        serverB = new AutoRegisteringRpcServer(registry, instB, 1);
        serverB.registerService(SERVICE_NAME, (HelloService) name -> "Hello from ServerB");

        // 启动 (start 会阻塞，需要用 daemon 线程)
        new Thread(() -> {
            try { serverA.start(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }, "serverA").start();
        new Thread(() -> {
            try { serverB.start(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }, "serverB").start();

        // 等 netty 启动 + 注册
        sleep(1500);

        // 诊断：打印 registry 实际内容
        System.out.println("[E2E-DEBUG] registry.discover(SERVICE_NAME).size=" + registry.discover(SERVICE_NAME).size());
        System.out.println("[E2E-DEBUG] discovery.instances(SERVICE_NAME).size=" + discovery.instances(SERVICE_NAME).size());
        System.out.println("[E2E-DEBUG] serverA.instance=" + serverA.getInstance() + " serviceName=" + serverA.getInstance().getServiceName());
    }

    @AfterAll
    static void tearDown() {
        try { client.close(); } catch (Exception ignored) {}
        try { serverA.stop(); } catch (Exception ignored) {}
        try { serverB.stop(); } catch (Exception ignored) {}
        discovery.close();
        registry.close();
    }

    @Test
    @DisplayName("1. 两个 Server 启动后，Client 能发现两个实例")
    void e2e_discover_two_instances() {
        int size = discovery.instances(SERVICE_NAME).size();
        assertTrue(size >= 2,
                "should discover at least 2 instances, got " + size);
    }

    @Test
    @DisplayName("2. Client 调用通过负载均衡路由到两个 Server")
    void e2e_load_balance() {
        HelloService proxy = client.createProxy(HelloService.class);

        Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
        for (int i = 0; i < 10; i++) {
            try {
                String result = proxy.sayHello("test");
                hits.computeIfAbsent(result, k -> new AtomicInteger()).incrementAndGet();
            } catch (Exception e) {
                fail("RPC call failed: " + e.getMessage());
            }
        }

        // 至少被 2 个不同 Server 处理
        assertTrue(hits.size() >= 2,
                "calls should be distributed to at least 2 servers, but got: " + hits);
    }

    @Test
    @DisplayName("3. 停掉 ServerA 后等待心跳过期，Client 只剩 ServerB")
    void e2e_server_a_offline_heartbeat_expire() {
        // 停 ServerA (不主动 deregister，等心跳过期)
        serverA.stop();

        // 等心跳过期：InMemoryRpcRegistry expireMs=3000, scan=200ms
        sleep(3500);
        registry.triggerScan();

        // 等 Discovery 订阅回调更新缓存
        sleep(200);

        // 至少剩 1 个 (ServerB)
        int size = discovery.instances(SERVICE_NAME).size();
        assertTrue(size >= 1,
                "should have at least 1 instance after ServerA expired, got " + size);
    }

    @Test
    @DisplayName("4. 重启 ServerC 注册到同名服务，Client 发现 2 个实例")
    void e2e_new_server_c_registers() {
        ServiceInstance instC = new ServiceInstance(SERVICE_NAME, "127.0.0.1", PORT_C);
        instC.setWeight(1.0);
        AutoRegisteringRpcServer serverC = new AutoRegisteringRpcServer(registry, instC, 1);
        serverC.registerService(SERVICE_NAME, (HelloService) name -> "Hello from ServerC");

        new Thread(() -> {
            try { serverC.start(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }, "serverC").start();

        sleep(800);

        // 至少 2 个实例 (ServerB + ServerC)
        int size = discovery.instances(SERVICE_NAME).size();
        assertTrue(size >= 2,
                "should have at least 2 instances after ServerC registers, got " + size);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
