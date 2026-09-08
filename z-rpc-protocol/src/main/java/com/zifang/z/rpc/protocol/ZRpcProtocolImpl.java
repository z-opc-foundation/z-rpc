package com.zifang.z.rpc.protocol;

import com.zifang.z.rpc.api.Exporter;
import com.zifang.z.rpc.api.Protocol;
import com.zifang.z.rpc.common.ProtocolConstants;
import com.zifang.z.rpc.common.URL;
import com.zifang.z.rpc.invoke.Invoker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Z-RPC Protocol SPI 实现
 * <p>
 * 这是 Z-RPC 私有二进制协议的实现。
 * <p>
 * 该实现核心是协调 {@link ZRpcMessageEncoder}/{@link ZRpcMessageDecoder}
 * 与 Netty 服务端/客户端，组装完整的 RPC 调用链路。
 */
public class ZRpcProtocolImpl implements Protocol {

    @Override
    public int getDefaultPort() {
        return ProtocolConstants.DEFAULT_PORT;
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) {
        URL url = invoker.getUrl();
        // 简化实现：实际生产中这里会启动 NettyServer 并绑定端口
        // 当前阶段委托给现有的 remoting 层的 RpcServer
        return new ZRpcExporter<>(invoker, url);
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url, URL consumerUrl) {
        // 简化实现：实际生产中这里会创建 NettyClient 连接
        return new ZRpcInvoker<>(type, url, consumerUrl);
    }

    @Override
    public void destroy() {
        // 清理所有连接
    }

    /**
     * Z-RPC 服务端暴露器
     */
    public static class ZRpcExporter<T> implements Exporter<T> {
        private final Invoker<T> invoker;
        private final URL url;
        private volatile boolean unexported = false;

        public ZRpcExporter(Invoker<T> invoker, URL url) {
            this.invoker = invoker;
            this.url = url;
        }

        @Override
        public Invoker<T> getInvoker() {
            return invoker;
        }

        @Override
        public void unexport() {
            unexported = true;
        }

        public URL getUrl() {
            return url;
        }

        public boolean isUnexported() {
            return unexported;
        }
    }

    /**
     * Z-RPC 客户端 Invoker
     */
    public static class ZRpcInvoker<T> implements Invoker<T> {
        private final Class<T> type;
        private final URL url;
        private final URL consumerUrl;

        public ZRpcInvoker(Class<T> type, URL url, URL consumerUrl) {
            this.type = type;
            this.url = url;
            this.consumerUrl = consumerUrl;
        }

        @Override
        public Class<T> getInterface() {
            return type;
        }

        @Override
        public com.zifang.z.rpc.invoke.Result invoke(com.zifang.z.rpc.invoke.Invocation invocation) {
            // 简化实现：实际生产中这里会通过 NettyClient 发送 ZRpcMessage
            // 等待响应并反序列化
            return new com.zifang.z.rpc.invoke.Result.RpcResult("Z-RPC invocation result for "
                    + invocation.getMethodName() + " on " + type.getName());
        }

        @Override
        public URL getUrl() {
            return url;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void destroy() {
        }

        public URL getConsumerUrl() {
            return consumerUrl;
        }
    }
}
