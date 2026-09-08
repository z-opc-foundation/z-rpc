package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 消费端自动装配
 */
@Configuration
public class ZRpcConsumerAutoConfiguration {

    public ZRpcConsumerAutoConfiguration(ZRpcProperties properties) {
        // 这里会在 BeanPostProcessor 中扫描 @ZRpcReference 注解
    }
}
