package com.zifang.z.rpc.starter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用 Z-RPC 框架
 * <p>
 * 标注在 Spring Boot 启动类上，激活 Z-RPC 自动装配。
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableZRpc(scanBasePackages = "com.zifang.demo")
 * public class OrderServiceApplication { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EnableZRpc {

    /**
     * 扫描的基础包路径，用于发现 @ZRpcService 和 @ZRpcReference 注解
     */
    String[] scanBasePackages() default {};
}
