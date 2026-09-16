package com.zifang.z.rpc.filter;

import com.zifang.z.rpc.async.RpcContext;
import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.invoke.Result;
import com.zifang.z.rpc.spi.Activate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Trace 过滤器（消费端）
 * <p>
 * 在 attachment 中注入 traceId。
 */
@Activate(group = "consumer", order = 10)
public class ConsumerTraceFilter implements Filter {

    private static final Logger log = LogManager.getLogger(ConsumerTraceFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws Throwable {
        String traceId = RpcContext.getContext().getTraceId();
        if (traceId != null) {
            invocation.setAttachment("trace-id", traceId);
        }
        log.debug("[Z-RPC] Consumer invoke: {}.{}", invoker.getInterface().getName(), invocation.getMethodName());
        return invoker.invoke(invocation);
    }
}
