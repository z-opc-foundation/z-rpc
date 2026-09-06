package com.zifang.z.rpc.discovery;

import com.zifang.z.rpc.registry.InMemoryRpcRegistry;
import com.zifang.z.rpc.registry.ServiceInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceDiscovery 单元测试
 */
public class ServiceDiscoveryTest {

    private InMemoryRpcRegistry registry;
    private ServiceDiscovery discovery;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRpcRegistry(30_000L, 1_000L, 5L);
        discovery = new ServiceDiscovery(registry, "weighted-round-robin");
    }

    @AfterEach
    void tearDown() {
        discovery.close();
        registry.close();
    }

    @Test
    void subscribe_and_select() {
        ServiceInstance ins1 = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        ServiceInstance ins2 = new ServiceInstance("HelloService", "127.0.0.1", 8002);
        registry.register(ins1);
        registry.register(ins2);

        discovery.subscribe("HelloService");

        // 订阅后应有 2 个实例
        assertEquals(2, discovery.instances("HelloService").size());

        // select 应该返回其中一个
        ServiceInstance selected = discovery.select("HelloService");
        assertNotNull(selected);
        assertTrue(selected.getPort() == 8001 || selected.getPort() == 8002);
    }

    @Test
    void subscribe_receives_new_instance() {
        discovery.subscribe("HelloService");
        assertEquals(0, discovery.instances("HelloService").size());

        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);

        // 订阅回调会同步更新缓存
        assertEquals(1, discovery.instances("HelloService").size());
    }

    @Test
    void subscribe_receives_deregister() {
        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);
        discovery.subscribe("HelloService");
        assertEquals(1, discovery.instances("HelloService").size());

        registry.deregister("HelloService", ins.getInstanceId());
        assertEquals(0, discovery.instances("HelloService").size());
    }

    @Test
    void load_balance_round_robin() {
        ServiceInstance ins1 = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        ServiceInstance ins2 = new ServiceInstance("HelloService", "127.0.0.1", 8002);
        registry.register(ins1);
        registry.register(ins2);

        ServiceDiscovery rrDiscovery = new ServiceDiscovery(registry, "round-robin");
        rrDiscovery.subscribe("HelloService");

        // 轮询：调 100 次，应均匀分到 8001 和 8002
        Map<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();
        for (int i = 0; i < 100; i++) {
            ServiceInstance s = rrDiscovery.select("HelloService");
            counts.computeIfAbsent(s.getPort(), k -> new AtomicInteger()).incrementAndGet();
        }
        rrDiscovery.close();

        assertTrue(counts.containsKey(8001));
        assertTrue(counts.containsKey(8002));
        // 各 50 次 (误差 ±5)
        assertEquals(50, counts.get(8001).get(), 5);
        assertEquals(50, counts.get(8002).get(), 5);
    }

    @Test
    void load_balance_weighted() {
        // ins1 权重 3, ins2 权重 1 → 比例 3:1
        ServiceInstance ins1 = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        ins1.setWeight(3.0);
        ServiceInstance ins2 = new ServiceInstance("HelloService", "127.0.0.1", 8002);
        ins2.setWeight(1.0);
        registry.register(ins1);
        registry.register(ins2);

        discovery.subscribe("HelloService");

        Map<Integer, AtomicInteger> counts = new ConcurrentHashMap<>();
        for (int i = 0; i < 1000; i++) {
            ServiceInstance s = discovery.select("HelloService");
            counts.computeIfAbsent(s.getPort(), k -> new AtomicInteger()).incrementAndGet();
        }

        int c1 = counts.getOrDefault(8001, new AtomicInteger()).get();
        int c2 = counts.getOrDefault(8002, new AtomicInteger()).get();
        assertTrue(c1 > c2, "ins1 (weight=3) should be selected more than ins2 (weight=1)");
    }

    @Test
    void select_no_instances_returns_null() {
        discovery.subscribe("EmptyService");
        assertNull(discovery.select("EmptyService"));
    }

    @Test
    void change_count_increments_on_register() {
        discovery.subscribe("HelloService");
        long initial = discovery.getChangeCount(); // subscribe 会触发 FULL_SNAPSHOT

        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);

        assertTrue(discovery.getChangeCount() > initial, "change count should increment after register");
    }
}
