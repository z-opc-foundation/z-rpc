package com.zifang.z.rpc.filter;

import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.invoke.Result;
import com.zifang.z.rpc.spi.Activate;
import com.zifang.z.rpc.spi.SPI;

/**
 * 拦截器接口
 * <p>
 * Dubbo 风格的责任链扩展点。
 * <p>
 * 实现示例：
 * <pre>
 * &#64;Activate(group = "consumer", order = 100)
 * public class MonitorFilter implements Filter {
 *     public Result invoke(Invoker&lt;?&gt; invoker, Invocation inv) {
 *         long start = System.currentTimeMillis();
 *         try {
 *             return invoker.invoke(inv);
 *         } finally {
 *             MetricsCollector.record(inv, System.currentTimeMillis() - start);
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * 资源文件：META-INF/z-rpc/com.zifang.z.rpc.filter.Filter
 */
@SPI
public interface Filter {

    /**
     * 拦截调用
     *
     * @param invoker 目标调用者
     * @param invocation 调用信息
     * @return 调用结果
     * @throws Throwable 任何异常
     */
    Result invoke(Invoker<?> invoker, Invocation invocation) throws Throwable;
}
