package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册中心自动装配
 */
@Configuration
public class ZRpcRegistryAutoConfiguration {

    public ZRpcRegistryAutoConfiguration(ZRpcProperties properties) {
        if (properties.getRegistry().isEnabled()) {
            // 这里会初始化 z-config 客户端
        }
    }
}
