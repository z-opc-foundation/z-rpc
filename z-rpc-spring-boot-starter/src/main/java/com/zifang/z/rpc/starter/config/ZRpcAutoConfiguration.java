package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.zifang.z.rpc.spi.ExtensionLoader;
import com.zifang.z.rpc.api.Protocol;

/**
 * Z-RPC 自动装配入口
 */
@Configuration
@ConditionalOnProperty(prefix = "z.rpc", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(ZRpcProperties.class)
@Import({ZRpcServerAutoConfiguration.class, ZRpcConsumerAutoConfiguration.class, ZRpcRegistryAutoConfiguration.class})
public class ZRpcAutoConfiguration {

    private static final Logger log = LogManager.getLogger(ZRpcAutoConfiguration.class);

    public ZRpcAutoConfiguration() {
        log.info("Z-RPC framework initialized");
        // 验证 SPI 加载
        try {
            Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getDefaultExtension();
            log.info("Default Protocol SPI loaded: {}", protocol.getClass().getName());
        } catch (Exception e) {
            log.warn("Protocol SPI not loaded: {}", e.getMessage());
        }
    }

    @Bean
    public ZRpcFrameworkInitializer zRpcFrameworkInitializer(ZRpcProperties properties) {
        return new ZRpcFrameworkInitializer(properties);
    }
}
