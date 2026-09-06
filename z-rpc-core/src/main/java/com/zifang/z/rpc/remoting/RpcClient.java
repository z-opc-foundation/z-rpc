package com.zifang.z.rpc.remoting;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * RPC 客户端
 * 基于 Netty 实现
 */
public class RpcClient {

    private final Logger log = LogManager.getLogger(this.getClass());


    private final String host;
    private final int port;
    private final Map<String, CompletableFuture<RpcResponse>> pendingRequests = new ConcurrentHashMap<>();
    private Channel channel;
    private EventLoopGroup group;
    private volatile boolean connected = false;
    // 超时时间（毫秒）
    private long timeout = 3000;

    public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        connect();
    }

    /**
     * 连接服务器
     */
    private void connect() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new RpcMessageDecoder());
                            pipeline.addLast(new RpcMessageEncoder());
                            pipeline.addLast(new RpcClientHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect(host, port).sync();
            channel = future.channel();
            connected = true;
            log.info("Connected to RPC server: {}:{}", host, port);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to connect to server", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to server: " + host + ":" + port, e);
        }
    }

    /**
     * 发送请求
     */
    public RpcResponse sendRequest(RpcRequest request) throws Exception {
        if (!connected || !channel.isActive()) {
            throw new IllegalStateException("Not connected to server");
        }

        CompletableFuture<RpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(request.getRequestId(), future);

        try {
            channel.writeAndFlush(request).addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    future.completeExceptionally(f.cause());
                    pendingRequests.remove(request.getRequestId());
                }
            });

            // 等待响应，带超时
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            pendingRequests.remove(request.getRequestId());
            throw e;
        }
    }

    /**
     * 关闭连接
     */
    public void close() {
        connected = false;
        if (channel != null) {
            channel.close();
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        // 清理未完成的请求
        for (CompletableFuture<RpcResponse> future : pendingRequests.values()) {
            future.completeExceptionally(new RuntimeException("Client closed"));
        }
        pendingRequests.clear();
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return connected && channel != null && channel.isActive();
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    /**
     * RPC 客户端处理器
     */
    @ChannelHandler.Sharable
    private class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
        private final Logger log = LogManager.getLogger(this.getClass());

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcResponse response) {
            CompletableFuture<RpcResponse> future = pendingRequests.remove(response.getRequestId());
            if (future != null) {
                future.complete(response);
            } else {
                log.warn("Received unknown response: {}", response.getRequestId());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("Client exception: {}", cause.getMessage(), cause);
            ctx.close();
        }
    }
}
