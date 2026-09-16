package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Z-RPC 框架初始化器
 * <p>
 * 启动时打印框架配置信息，验证 SPI 加载。
 */
public class ZRpcFrameworkInitializer {

    private static final Logger log = LogManager.getLogger(ZRpcFrameworkInitializer.class);

    public ZRpcFrameworkInitializer(ZRpcProperties properties) {
        log.info("===========================================");
        log.info("Z-RPC Framework Configuration:");
        log.info("  Application: {} v{}", properties.getApplication().getName(), properties.getApplication().getVersion());
        log.info("  Organization: {}", properties.getApplication().getOrganization());
        log.info("  Registry: {} ({})", properties.getRegistry().getType(), properties.getRegistry().getAddress());
        log.info("  Server: {}:{}", properties.getServer().getHost(), properties.getServer().getPort());
        log.info("  Protocol: {}, Serialization: {}",
                properties.getProtocol().getName(), properties.getProtocol().getSerialization());
        log.info("  Consumer: cluster={}, loadbalance={}, timeout={}ms",
                properties.getConsumer().getCluster(), properties.getConsumer().getLoadbalance(),
                properties.getConsumer().getTimeout());
        log.info("===========================================");
    }
}
