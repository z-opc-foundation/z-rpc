package com.zifang.z.rpc;

import com.zifang.z.rpc.discovery.ServiceDiscovery;
import com.zifang.z.rpc.registry.ServiceInstance;
import com.zifang.z.rpc.remoting.RpcRequest;
import com.zifang.z.rpc.remoting.RpcResponse;
import com.zifang.z.rpc.remoting.RpcClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.zifang.util.core.lang.RandomUtil;

/**
 * 自动发现的 RpcClient + 动态代理 (FEATURE) — nacos-style 调用方式.
 *
 * <p>客户端调用方式:
 * <pre>
 *     DiscoveryRpcClient client = new DiscoveryRpcClient(discovery);
 *     HelloService hello = client.createProxy(HelloService.class);
 *     String result = hello.sayHello("World");
 * </pre>
 *
 * <p>内部:
 * <ul>
 *   <li>每个 serviceName 维护一个 channel pool (key → ChannelFuture)</li>
 *   <li>调用前用 {@link ServiceDiscovery#select(String)} 选 instance</li>
 *   <li>如果该 endpoint 还没有连接就 build + connect; 如果连接断了就剔除并下次重连</li>
 * </ul>
 */
public class DiscoveryRpcClient implements AutoCloseable {

    private static final Logger log = LogManager.getLogger(DiscoveryRpcClient.class);

    private final ServiceDiscovery discovery;
    private final EventLoopGroup group;
    /** ip:port → ChannelFuture (复用 channel; 失败时移除) */
    private final ConcurrentHashMap<String, ChannelFuture> channels = new ConcurrentHashMap<>();

    public DiscoveryRpcClient(ServiceDiscovery discovery) {
        this.discovery = discovery;
        this.group = new NioEventLoopGroup();
    }

    /** 每条 channel 对应一个 handler，handler 自己维护 futures map */
    private final ConcurrentHashMap<String, RpcClientHandler> handlers = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> serviceInterface) {
        return (T) Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                new ServiceInvocationHandler(serviceInterface.getName()));
    }

    private class ServiceInvocationHandler implements InvocationHandler {
        private final String serviceName;
        ServiceInvocationHandler(String serviceName) { this.serviceName = serviceName; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
            if ("toString".equals(name)) return "Proxy[" + serviceName + "]";
            if ("equals".equals(name)) return proxy == args[0];

            ServiceInstance ins = discovery.select(serviceName);
            if (ins == null) {
                throw new RuntimeException("no live instance for " + serviceName
                        + " (instances=" + discovery.instances(serviceName).size() + ")");
            }
            Channel ch = acquireChannel(ins);
            RpcClientHandler handler = handlers.get(ins.endpoint());

            RpcRequest req = new RpcRequest();
            req.setRequestId(com.zifang.util.core.lang.RandomUtil.uuid());
            req.setInterfaceName(serviceName);
            req.setMethodName(name);
            req.setParameterTypes(method.getParameterTypes());
            req.setArguments(args);

            CompletableFuture<RpcResponse> future = new CompletableFuture<>();
            handler.setFuture(req.getRequestId(), future);

            ch.writeAndFlush(req).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    future.completeExceptionally(f.cause());
                    handler.setFuture(req.getRequestId(), null);
                }
            });

            RpcResponse resp = future.get(15, TimeUnit.SECONDS);
            if (resp == null) throw new RuntimeException("rpc null response from " + ins.endpoint());
            if (resp.getException() != null) throw resp.getException();
            return resp.getResult();
        }
    }

    private Channel acquireChannel(ServiceInstance ins) {
        String ep = ins.endpoint();
        ChannelFuture cf = channels.get(ep);
        if (cf != null && cf.channel() != null && cf.channel().isActive()) return cf.channel();

        synchronized (channels) {
            cf = channels.get(ep);
            if (cf != null && cf.channel() != null && cf.channel().isActive()) return cf.channel();
            log.info("rpc-client: connecting to {} ({})", ep, ins.getInstanceId());
            try {
                Bootstrap b = new Bootstrap();
                RpcClientHandler handler = new RpcClientHandler();
                b.group(group)
                        .channel(NioSocketChannel.class)
                        .handler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ChannelPipeline pipeline = ch.pipeline();
                                pipeline.addLast(new com.zifang.z.rpc.remoting.RpcMessageDecoder());
                                pipeline.addLast(new com.zifang.z.rpc.remoting.RpcMessageEncoder());
                                pipeline.addLast(handler);
                            }
                        });
                ChannelFuture ncf = b.connect(ins.getIp(), ins.getPort()).sync();
                channels.put(ep, ncf);
                handlers.put(ep, handler);
                return ncf.channel();
            } catch (Exception e) {
                channels.remove(ep);
                handlers.remove(ep);
                throw new RuntimeException("connect failed to " + ep, e);
            }
        }
    }

    @Override
    public void close() {
        try {
            for (ChannelFuture f : channels.values()) {
                try { f.channel().close(); } catch (Exception ignore) {}
            }
            channels.clear();
            handlers.clear();
        } finally {
            group.shutdownGracefully();
        }
    }
}
