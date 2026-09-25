package com.zifang.z.rpc.starter.annotation;

import com.zifang.z.rpc.starter.config.ZRpcAutoConfiguration;
import com.zifang.z.rpc.starter.config.ZRpcReferenceInjector;
import org.springframework.context.annotation.Import;

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
@Import({ZRpcAutoConfiguration.class, ZRpcReferenceInjector.class})
public @interface EnableZRpc {

    /**
     * 扫描的基础包路径，用于发现 @ZRpcService 和 @ZRpcReference 注解。
     * <p>
     * 注意：{@code @ZRpcService} 的导出与 {@code @ZRpcReference} 的注入都由
     * {@link ZRpcAutoConfiguration} 注册的 BeanPostProcessor / Bean 生命周期钩子完成，
     * 它们会遍历容器里全部 Bean，因此本属性当前<b>不参与</b>任何扫描；
     * 保留它只是为了与 {@code @SpringBootApplication} 的书写习惯兼容。
     */
    String[] scanBasePackages() default {};
}
