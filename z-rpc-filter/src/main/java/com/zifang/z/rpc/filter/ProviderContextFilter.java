package com.zifang.z.rpc.filter;

import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.invoke.Result;
import com.zifang.z.rpc.invoke.RpcInvocation;
import com.zifang.z.rpc.spi.Activate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * Provider 端 Context 过滤器
 * <p>
 * 从 invocation 还原 traceId 等上下文。
 */
@Activate(group = "provider", order = 10)
public class ProviderContextFilter implements Filter {

    private static final Logger log = LogManager.getLogger(ProviderContextFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws Throwable {
        String traceId = invocation.getAttachment("trace-id");
        if (traceId != null) {
            ThreadContext.put("traceId", traceId);
            log.debug("[Z-RPC] Provider invoke with traceId={}: {}.{}",
                    traceId, invoker.getInterface().getName(), invocation.getMethodName());
        }
        try {
            Result result = invoker.invoke(invocation);
            return result;
        } finally {
            ThreadContext.clearAll();
        }
    }
}
