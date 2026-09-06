package com.zifang.z.rpc.annotation;

import org.springframework.beans.factory.annotation.Autowired;

import java.lang.annotation.*;

/**
 * Z-RPC 服务引用注解
 * 用于注入远程服务代理
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Autowired
public @interface ZRpcReference {

    /**
     * 服务接口类
     */
    Class<?> interfaceClass() default void.class;

    /**
     * 服务接口名称
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
     * 连接超时（毫秒）
     */
    int timeout() default 3000;

    /**
     * 重试次数
     */
    int retries() default 2;

    /**
     * 负载均衡策略
     * random: 随机
     * roundrobin: 轮询
     * leastactive: 最少活跃调用
     * consistenthash: 一致性哈希
     */
    String loadbalance() default "random";

    /**
     * 集群容错策略
     * failover: 失败重试
     * failfast: 快速失败
     * failsafe: 失败安全
     * failback: 失败恢复
     * broadcast: 广播
     * forking: 并行调用
     */
    String cluster() default "failover";

    /**
     * 是否异步调用
     */
    boolean async() default false;

    /**
     * 是否单向调用（oneway）
     */
    boolean oneway() default false;

    /**
     * 服务注册中心地址
     */
    String registry() default "127.0.0.1:8084";

    /**
     * 检查服务提供者是否存在
     */
    boolean check() default true;

    /**
     * 延迟初始化
     */
    boolean lazy() default false;

    /**
     * 连接数限制
     */
    int connections() default 0;

    /**
     * 客户端类型
     */
    String client() default "netty";

    /**
     * 序列化方式
     */
    String serialization() default "hessian2";
}
