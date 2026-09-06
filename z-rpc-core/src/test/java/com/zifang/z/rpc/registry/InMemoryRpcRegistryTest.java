package com.zifang.z.rpc.registry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InMemoryRpcRegistry 单元测试
 */
public class InMemoryRpcRegistryTest {

    private InMemoryRpcRegistry registry;

    @BeforeEach
    void setUp() {
        // expireMs=200ms, scanIntervalMs=50ms, heartbeatIntervalSec=1
        registry = new InMemoryRpcRegistry(200L, 50L, 1L);
    }

    @AfterEach
    void tearDown() {
        registry.close();
    }

    @Test
    void register_and_discover() {
        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        assertTrue(registry.register(ins));

        List<ServiceInstance> list = registry.discover("HelloService");
        assertEquals(1, list.size());
        assertEquals("127.0.0.1", list.get(0).getIp());
        assertEquals(8001, list.get(0).getPort());
    }

    @Test
    void register_duplicate_instance_returns_false() {
        ServiceInstance ins1 = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        ServiceInstance ins2 = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        assertTrue(registry.register(ins1));
        assertFalse(registry.register(ins2));
    }

    @Test
    void deregister_removes_instance() {
        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);
        assertEquals(1, registry.discover("HelloService").size());

        registry.deregister("HelloService", ins.getInstanceId());
        assertEquals(0, registry.discover("HelloService").size());
    }

    @Test
    void heartbeat_refreshes_and_keeps_alive() {
        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);

        // 等 150ms (还没过期 200ms)
        sleep(150);
        // 心跳续约
        long next = registry.heartbeat("HelloService", ins.getInstanceId());
        assertTrue(next > 0);

        // 再等 150ms (总共 300ms，但刚续约过，还活着)
        sleep(150);
        assertTrue(registry.isHealthy("HelloService", ins.getInstanceId()));
    }

    @Test
    void heartbeat_expire_marks_unhealthy_and_removes() {
        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);

        // 等 300ms > 200ms 过期窗口，且扫描器会触发 (每 50ms)
        sleep(350);

        // 手动触发一次扫描
        registry.triggerScan();

        // 应该已经被标 unhealthy 并触发 DEREGISTER 事件
        assertFalse(registry.isHealthy("HelloService", ins.getInstanceId()));
        assertEquals(0, registry.discover("HelloService").size());
    }

    @Test
    void subscribe_receives_register_event() {
        List<RpcRegistry.RegistryEvent> events = new CopyOnWriteArrayList<>();
        registry.subscribe("HelloService", events::add);

        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);

        // subscribe 时会给一个 FULL_SNAPSHOT，注册时再给一个 REGISTER
        // 过滤出 REGISTER
        long registerCount = events.stream()
                .filter(e -> e.getType() == RpcRegistry.Type.REGISTER)
                .count();
        assertTrue(registerCount >= 1, "should have at least 1 REGISTER event");
    }

    @Test
    void subscribe_snapshot_has_initial_instances() {
        // 先注册
        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);

        // 再订阅 → 立即收到 FULL_SNAPSHOT
        List<RpcRegistry.RegistryEvent> events = new CopyOnWriteArrayList<>();
        registry.subscribe("HelloService", events::add);

        long snapshotCount = events.stream()
                .filter(e -> e.getType() == RpcRegistry.Type.FULL_SNAPSHOT)
                .count();
        assertEquals(1, snapshotCount);

        // snapshot 里应该有 1 个实例
        RpcRegistry.RegistryEvent snap = events.stream()
                .filter(e -> e.getType() == RpcRegistry.Type.FULL_SNAPSHOT)
                .findFirst().orElse(null);
        assertNotNull(snap);
        assertEquals(1, snap.getInstances().size());
    }

    @Test
    void subscribe_deregister_event() {
        ServiceInstance ins = new ServiceInstance("HelloService", "127.0.0.1", 8001);
        registry.register(ins);

        List<RpcRegistry.RegistryEvent> events = new CopyOnWriteArrayList<>();
        registry.subscribe("HelloService", events::add);

        registry.deregister("HelloService", ins.getInstanceId());

        long deregisterCount = events.stream()
                .filter(e -> e.getType() == RpcRegistry.Type.DEREGISTER)
                .count();
        assertTrue(deregisterCount >= 1, "should have at least 1 DEREGISTER event");
    }

    @Test
    void discover_empty_service_returns_empty_list() {
        List<ServiceInstance> list = registry.discover("NonExistService");
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
