package com.zifang.z.rpc.remoting;

import com.zifang.z.rpc.registry.RpcRegistry;
import com.zifang.z.rpc.registry.ServiceInstance;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自动注册版 RpcServer (FEATURE) — 在原生 RpcServer 之上包一层:
 * <ul>
 *   <li>启动时向 {@link RpcRegistry} 上报本机实例 (serviceName → ip:port)</li>
 *   <li>后台心跳线程: 每 {@link #heartbeatIntervalSec}s 向 registry 发心跳, 失败重试 3 次</li>
 *   <li>关闭时主动 deregister (ephemeral=true 也兼容, 让收尾更干净)</li>
 * </ul>
 *
 * <p>这样 Server 启动后零配置即可被 DiscoveryRpcClient 发现.
 */
public class AutoRegisteringRpcServer extends RpcServer {

    private static final Logger log = LogManager.getLogger(AutoRegisteringRpcServer.class);

    private final RpcRegistry registry;
    private final ServiceInstance instance;
    private final long heartbeatIntervalSec;
    private ScheduledExecutorService heartbeat;
    private final AtomicLong heartbeatOkCount = new AtomicLong();
    private final AtomicLong heartbeatFailCount = new AtomicLong();
    /** 每个 serviceInterface → 对应注册的 serviceName */
    private final Map<Class<?>, String> classToServiceName = new ConcurrentHashMap<>();

    public AutoRegisteringRpcServer(RpcRegistry registry, ServiceInstance instance,
                                    long heartbeatIntervalSec) {
        super(instance.getPort());
        this.registry = registry;
        this.instance = instance;
        this.heartbeatIntervalSec = heartbeatIntervalSec;
    }

    /**
     * 注册一个服务实现：用一个明确 serviceName（业务级唯一）。
     */
    public void registerService(String serviceName, Object serviceImpl) {
        super.registerService(serviceName, serviceImpl);
        if (instance.getExposedInterfaces() == null) {
            instance.setExposedInterfaces(new String[]{serviceName});
        } else {
            String[] old = instance.getExposedInterfaces();
            String[] neu = new String[old.length + 1];
            System.arraycopy(old, 0, neu, 0, old.length);
            neu[old.length] = serviceName;
            instance.setExposedInterfaces(neu);
        }
        // 修正 serviceName (默认用业务名)
        instance.setServiceName(serviceName);
    }

    @Override
    public void registerService(Class<?> serviceInterface, Object serviceImpl) {
        super.registerService(serviceInterface, serviceImpl);
        classToServiceName.put(serviceInterface, serviceInterface.getName());
    }

    @Override
    public void start() throws InterruptedException {
        // 1. 注册到注册中心
        if (instance.getInstanceId() == null) {
            instance.setInstanceId(instance.endpoint());
        }
        boolean ok = registry.register(instance);
        if (!ok) {
            log.warn("auto-register failed for {}; continuing anyway", instance);
        }
        // 2. 启动心跳线程
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AutoRegister-HB");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat,
                2, heartbeatIntervalSec, TimeUnit.SECONDS);
        log.info("auto-register: heartbeat scheduled every {}s for {}",
                heartbeatIntervalSec, instance);
        // 3. 启动底层 netty server
        super.start();
    }

    @Override
    public void stop() {
        try {
            if (heartbeat != null) heartbeat.shutdownNow();
        } catch (Exception ignore) {}
        try {
            registry.deregister(instance.getServiceName(), instance.getInstanceId());
        } catch (Exception ex) {
            log.warn("deregister failed for {}: {}", instance, ex.getMessage());
        }
        super.stop();
    }

    /** 一次性强制心跳一次 (测试 helper). */
    public void sendHeartbeat() {
        try {
            long nxt = registry.heartbeat(instance.getServiceName(), instance.getInstanceId());
            heartbeatOkCount.incrementAndGet();
            if (nxt <= 0) {
                heartbeatFailCount.incrementAndGet();
                log.warn("heartbeat noop for {} (instance possibly expired)", instance);
            }
        } catch (Throwable t) {
            heartbeatFailCount.incrementAndGet();
            log.warn("heartbeat err for {}: {}", instance, t.getMessage());
        }
    }

    public long getHeartbeatOkCount() { return heartbeatOkCount.get(); }
    public long getHeartbeatFailCount() { return heartbeatFailCount.get(); }
    public ServiceInstance getInstance() { return instance; }
}
