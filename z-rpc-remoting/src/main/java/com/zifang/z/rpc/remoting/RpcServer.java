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

    private final String host;
    private final int port;
    private final Map<String, Object> serviceMap = new ConcurrentHashMap<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;
    private volatile boolean started = false;
    private Thread serverThread;

    public RpcServer(int port) {
        this("0.0.0.0", port);
    }

    public RpcServer(String host, int port) {
        this.host = host == null || host.isEmpty() ? "0.0.0.0" : host;
        this.port = port;
    }

    /**
     * 注册服务（按接口全名）
     */
    public void registerService(Class<?> serviceInterface, Object serviceImpl) {
        registerService(serviceInterface.getName(), serviceImpl);
    }

    /**
     * 注册服务（按 serviceName）
     */
    public void registerService(String serviceName, Object serviceImpl) {
        serviceMap.put(serviceName, serviceImpl);
        log.info("Registered service: {}", serviceName);
    }

    /**
     * 注册服务（带版本）：
     * 默认同时按 {serviceInterface}:{version} 与 {serviceInterface} 两种 key 注册，便于客户端按版本或非版本查找。
     */
    public void register(Class<?> serviceInterface, Object serviceImpl, String version) {
        registerService(serviceInterface, serviceImpl);
        if (version != null && !version.isEmpty() && !"1.0.0".equals(version)) {
            // 暂不区分多版本；保留接口以便未来扩展
        }
    }

    /**
     * 启动服务器（阻塞）
     */
    public void start() throws InterruptedException {
        start(false);
    }

    /**
     * 启动服务器
     * @param daemon true 表示以守护线程异步启动（不阻塞当前线程）
     */
    public synchronized void start(boolean daemon) throws InterruptedException {
        if (started) {
            return;
        }

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

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

        ChannelFuture future = bootstrap.bind(host, port).sync();
        channel = future.channel();
        started = true;
        log.info("RPC Server started on {}:{}", host, port);

        if (daemon) {
            // 守护线程：把 closeFuture 移到独立线程，避免阻塞调用方
            serverThread = new Thread(() -> {
                try {
                    channel.closeFuture().await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    stop();
                }
            }, "z-rpc-server-shutdown-hook");
            serverThread.setDaemon(true);
            serverThread.start();
        } else {
            // 阻塞模式
            channel.closeFuture().await();
            stop();
        }
    }

    /**
     * 停止服务器
     */
    public synchronized void stop() {
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

    public String getHost() {
        return host;
    }
}
