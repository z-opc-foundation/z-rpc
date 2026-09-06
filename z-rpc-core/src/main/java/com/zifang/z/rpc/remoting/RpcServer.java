package com.zifang.z.rpc.remoting;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC 服务器
 * 基于 Netty 实现
 */
public class RpcServer {
    private final Logger log = LogManager.getLogger(this.getClass());

    private final int port;
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;
    private volatile boolean started = false;

    public RpcServer(int port) {
        this.port = port;
    }

    /**
     * 注册服务
     */
    public void registerService(Class<?> serviceInterface, Object serviceImpl) {
        String serviceName = serviceInterface.getName();
        serviceMap.put(serviceName, serviceImpl);
        log.info("Registered service: {}", serviceName);
    }

    /**
     * 注册服务（带版本）
     */
    public void registerService(String serviceName, Object serviceImpl) {
        serviceMap.put(serviceName, serviceImpl);
        log.info("Registered service: {}", serviceName);
    }

    /**
     * 启动服务器
     */
    public void start() throws InterruptedException {
        if (started) {
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new RpcMessageDecoder());
                            pipeline.addLast(new RpcMessageEncoder());
                            pipeline.addLast(new RpcServerHandler(serviceMap));
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true);

            ChannelFuture future = bootstrap.bind(port).sync();
            channel = future.channel();
            started = true;

            log.info("RPC Server started on port {}", port);

            // 等待关闭
            channel.closeFuture().await();
        } finally {
            stop();
        }
    }

    /**
     * 停止服务器
     */
    public void stop() {
        if (!started) {
            return;
        }
        started = false;

        if (channel != null) {
            channel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }

        log.info("RPC Server stopped");
    }

    /**
     * 是否已启动
     */
    public boolean isStarted() {
        return started;
    }

    /**
     * 获取服务映射
     */
    public Map<String, Object> getServiceMap() {
        return new HashMap<>(serviceMap);
    }

    /**
     * 获取端口
     */
    public int getPort() {
        return port;
    }
}
