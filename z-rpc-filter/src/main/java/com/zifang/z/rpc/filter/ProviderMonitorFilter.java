package com.zifang.z.rpc.filter;

import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.invoke.Result;
import com.zifang.z.rpc.metrics.MetricsCollector;
import com.zifang.z.rpc.spi.Activate;

/**
 * Provider 端监控过滤器
 */
@Activate(group = "provider", order = 100)
public class ProviderMonitorFilter implements Filter {

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        boolean success = false;
        try {
            Result result = invoker.invoke(invocation);
            success = !result.hasException();
            return result;
        } finally {
            long rt = System.currentTimeMillis() - start;
            MetricsCollector.getInstance().record(
                    invoker.getInterface().getName(),
                    invocation.getMethodName(),
                    rt, success);
        }
    }
}
