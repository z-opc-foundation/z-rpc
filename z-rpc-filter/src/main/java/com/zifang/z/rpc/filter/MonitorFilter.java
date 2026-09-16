package com.zifang.z.rpc.filter;

import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.invoke.Result;
import com.zifang.z.rpc.metrics.MetricsCollector;
import com.zifang.z.rpc.spi.Activate;

/**
 * 监控过滤器（消费端）
 * <p>
 * 记录每次调用的 QPS、RT、错误数。
 */
@Activate(group = "consumer", order = 100)
public class MonitorFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) {
        long start = System.currentTimeMillis();
        boolean success = false;
        Result result = null;
        try {
            result = invoker.invoke(invocation);
            success = !result.hasException();
            return result;
        } catch (Throwable t) {
            success = false;
            throw new RuntimeException(t);
        } finally {
            long rt = System.currentTimeMillis() - start;
            String service = invoker.getInterface().getName();
            String method = invocation.getMethodName();
            MetricsCollector.getInstance().record(service, method, rt, success);
        }
    }
}
