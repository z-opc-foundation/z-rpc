package com.zifang.z.rpc.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展点注解
 * <p>
 * 标记一个接口为可扩展 SPI 接口。
 * <pre>
 * &#64;SPI("failover")
 * public interface Cluster {
 *     ...
 * }
 *
 * // 实现
 * public class FailoverCluster implements Cluster { ... }
 * </pre>
 * <p>
 * 资源文件路径：META-INF/z-rpc/com.zifang.z.rpc.cluster.Cluster
 * <pre>
 * failover=com.zifang.z.rpc.cluster.FailoverCluster
 * failfast=com.zifang.z.rpc.cluster.FailfastCluster
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SPI {

    /**
     * 默认实现 key
     */
    String value() default "";
}
