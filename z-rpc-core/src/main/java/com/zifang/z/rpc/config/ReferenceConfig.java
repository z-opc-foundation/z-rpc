package com.zifang.z.rpc.config;

import com.zifang.z.rpc.cluster.Directory;
import com.zifang.z.rpc.cluster.FailoverCluster;
import com.zifang.z.rpc.cluster.RegistryDirectory;
import com.zifang.z.rpc.common.URL;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.proxy.ProxyFactory;
import com.zifang.z.rpc.registry.RegistryService;
import com.zifang.z.rpc.registry.ZConfigRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务引用配置
 * 用于配置和获取远程服务代理
 */
public class ReferenceConfig<T> {

    private final Logger log = LogManager.getLogger(this.getClass());

    // ========== 服务基本信息 ==========
    /**
     * 是否已销毁
     */
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    /**
     * 服务接口类
     */
    private Class<T> interfaceClass;

    // ========== 版本与分组 ==========
    /**
     * 服务接口名
     */
    private String interfaceName;
    /**
     * 服务版本
     */
    private String version = "1.0.0";

    // ========== 调用参数 ==========
    /**
     * 服务分组
     */
    private String group = "";
    /**
     * 超时时间（毫秒）
     */
    private int timeout = 3000;

    // ========== 集群与负载均衡 ==========
    /**
     * 重试次数
     */
    private int retries = 2;
    /**
     * 负载均衡策略
     */
    private String loadbalance = "random";

    // ========== 注册中心 ==========
    /**
     * 集群容错策略
     */
    private String cluster = "failover";

    // ========== 内部状态 ==========
    /**
     * 注册中心地址
     */
    private String registry = "127.0.0.1:8084";
    /**
     * 是否已初始化
     */
    private volatile boolean initialized = false;
    /**
     * 服务代理
     */
    private volatile T ref;

    // ========== 内部组件 ==========

    /**
     * 注册中心服务
     */
    private RegistryService registryService;

    /**
     * 集群 Invoker
     */
    private Invoker<T> clusterInvoker;
    private boolean async;
    private boolean oneway;

    // ========== 公共方法 ==========

    /**
     * 获取服务代理
     */
    public synchronized T get() {
        if (destroyed.get()) {
            throw new IllegalStateException("ReferenceConfig has been destroyed");
        }
        if (ref == null) {
            init();
        }
        return ref;
    }

    /**
     * 销毁
     */
    public synchronized void destroy() {
        if (destroyed.compareAndSet(false, true)) {
            if (clusterInvoker != null) {
                try {
                    clusterInvoker.destroy();
                } catch (Exception e) {
                    log.error("Failed to destroy cluster invoker", e);
                }
            }
            if (registryService != null) {
                try {
                    registryService.destroy();
                } catch (Exception e) {
                    log.error("Failed to destroy registry service", e);
                }
            }
            ref = null;
            initialized = false;
            log.info("ReferenceConfig destroyed: {}", interfaceName);
        }
    }

    // ========== 私有方法 ==========

    private void init() {
        if (initialized) {
            return;
        }

        // 检查配置
        checkConfig();

        // 连接注册中心
        connectRegistry();

        // 创建服务代理
        createProxy();

        initialized = true;
        log.info("ReferenceConfig initialized: {}", interfaceName);
    }

    private void checkConfig() {
        if (interfaceClass == null && (interfaceName == null || interfaceName.isEmpty())) {
            throw new IllegalStateException("interfaceClass or interfaceName is required");
        }

        // 解析接口类
        if (interfaceClass == null) {
            try {
                interfaceClass = (Class<T>) Class.forName(interfaceName);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Interface class not found: " + interfaceName, e);
            }
        }

        if (!interfaceClass.isInterface()) {
            throw new IllegalStateException("Interface class must be an interface: " + interfaceClass.getName());
        }

        if (interfaceName == null || interfaceName.isEmpty()) {
            interfaceName = interfaceClass.getName();
        }
    }

    private void connectRegistry() {
        if (registry == null || registry.isEmpty()) {
            throw new IllegalStateException("Registry address is required");
        }

        registryService = new ZConfigRegistry(registry);
        log.info("Connected to registry: {}", registry);
    }

    private void createProxy() {
        // 创建目录
        Directory<T> directory = createDirectory();

        // 创建集群 Invoker
        FailoverCluster cluster = new FailoverCluster();
        clusterInvoker = cluster.join(directory);

        // 创建代理
        ProxyFactory proxyFactory = new ProxyFactory();
        ref = proxyFactory.getProxy(interfaceClass, clusterInvoker);

        log.info("Service proxy created: {}", interfaceName);
    }

    private Directory<T> createDirectory() {
        URL consumerUrl = new URL();
        consumerUrl.setProtocol("consumer");
        consumerUrl.setHost(getLocalHost());
        consumerUrl.setPort(0);
        consumerUrl.setServiceInterface(interfaceName);
        consumerUrl.setGroup(group);
        consumerUrl.setVersion(version);
        consumerUrl.addParameter("timeout", String.valueOf(timeout));
        consumerUrl.addParameter("retries", String.valueOf(retries));
        consumerUrl.addParameter("loadbalance", loadbalance);
        consumerUrl.addParameter("cluster", cluster);
        consumerUrl.addParameter("side", "consumer");

        return new RegistryDirectory<>(interfaceClass, consumerUrl, registryService);
    }

    private String getLocalHost() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    public Logger getLog() {
        return log;
    }

    public Class<T> getInterfaceClass() {
        return interfaceClass;
    }

    public void setInterfaceClass(Class<T> interfaceClass) {
        this.interfaceClass = interfaceClass;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getRetries() {
        return retries;
    }

    public void setRetries(int retries) {
        this.retries = retries;
    }

    public String getLoadbalance() {
        return loadbalance;
    }

    public void setLoadbalance(String loadbalance) {
        this.loadbalance = loadbalance;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getRegistry() {
        return registry;
    }

    public void setRegistry(String registry) {
        this.registry = registry;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public AtomicBoolean getDestroyed() {
        return destroyed;
    }

    public T getRef() {
        return ref;
    }

    public void setRef(T ref) {
        this.ref = ref;
    }

    public RegistryService getRegistryService() {
        return registryService;
    }

    public void setRegistryService(RegistryService registryService) {
        this.registryService = registryService;
    }

    public Invoker<T> getClusterInvoker() {
        return clusterInvoker;
    }

    public void setClusterInvoker(Invoker<T> clusterInvoker) {
        this.clusterInvoker = clusterInvoker;
    }

    public boolean isAsync() {
        return async;
    }

    public void setAsync(boolean async) {
        this.async = async;
    }

    public boolean isOneway() {
        return oneway;
    }

    public void setOneway(boolean oneway) {
        this.oneway = oneway;
    }
}
