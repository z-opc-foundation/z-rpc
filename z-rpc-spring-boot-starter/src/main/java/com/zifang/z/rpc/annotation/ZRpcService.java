package com.zifang.z.rpc.annotation;

import java.lang.annotation.*;

/**
 * Z-RPC 服务注解
 * 标记一个类为 RPC 服务实现
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZRpcService {

    /**
     * 服务接口类
     * 如果不指定，则使用该类实现的所有接口
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 服务接口名称
     * 如果不指定，则使用 interfaceClass 的名称
     */
    String interfaceName() default "";

    /**
     * 服务版本
     */
    String version() default "1.0.0";

    /**
     * 服务分组
     */
    String group() default "";

    /**
     * 服务权重
     */
    int weight() default 100;

    /**
     * 延迟暴露时间（毫秒）
     */
    int delay() default 0;

    /**
     * 超时时间（毫秒）
     */
    int timeout() default 3000;

    /**
     * 重试次数
     */
    int retries() default 2;

    /**
     * 负载均衡策略
     */
    String loadbalance() default "random";

    /**
     * 集群容错策略
     */
    String cluster() default "failover";

    /**
     * 是否异步执行
     */
    boolean async() default false;
}
