package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.remoting.RpcServer;
import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ZRpcServerAutoConfiguration} 的装配测试。
 * <p>
 * 一律走真实容器 + 真实 socket：这一族错一个字符，日志里的
 * "RPC Server started" 照样打，但客户端连不进去。
 */
class ZRpcServerAutoConfigurationTest {

    private ZRpcServerAutoConfiguration newConfig(ZRpcProperties properties) throws Exception {
        ZRpcServerAutoConfiguration cfg = new ZRpcServerAutoConfiguration();
        Field f = ZRpcServerAutoConfiguration.class.getDeclaredField("properties");
        f.setAccessible(true);
        f.set(cfg, properties);
        return cfg;
    }

    /** 起一个只装 properties + 自动装配类的容器；refresh() 会触发 ContextRefreshedEvent。 */
    private AnnotationConfigApplicationContext context(ZRpcProperties properties) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.addBeanFactoryPostProcessor(bf -> bf.registerSingleton("zRpcProperties", properties));
        ctx.register(ZRpcServerAutoConfiguration.class);
        ctx.refresh();
        return ctx;
    }

    @Test
    @DisplayName("host=0.0.0.0（默认值）必须原样绑定，不能被换成本机 hostname 解析出的单地址")
    void wildcardHostIsBoundAsConfigured() throws Exception {
        ZRpcProperties props = new ZRpcProperties();
        props.getServer().setHost("0.0.0.0");
        props.getServer().setPort(0);

        RpcServer server = newConfig(props).rpcServer();
        try {
            assertEquals("0.0.0.0", server.getHost(),
                    "配置写的是所有网卡，装配出来的 bind 地址却是别的东西");
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("显式 host 也原样透传，装配层不做二次解析")
    void explicitHostIsPassedThrough() throws Exception {
        ZRpcProperties props = new ZRpcProperties();
        props.getServer().setHost("127.0.0.1");
        props.getServer().setPort(0);

        RpcServer server = newConfig(props).rpcServer();
        try {
            assertEquals("127.0.0.1", server.getHost());
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("绑 0.0.0.0 之后，127.0.0.1 上的客户端必须连得进来")
    void wildcardBindAcceptsLoopbackConnections() throws Exception {
        ZRpcProperties props = new ZRpcProperties();
        props.getServer().setHost("0.0.0.0");
        props.getServer().setPort(0);

        RpcServer server = newConfig(props).rpcServer();
        try {
            server.start(true);
            assertTrue(server.isStarted(), "start(true) 返回时 bind 应当已经同步完成");
            int port = server.getPort();
            assertTrue(port > 0, "绑定端口应当回读得到，实际: " + port);

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
                assertTrue(socket.isConnected(),
                        "服务端声称 started，但 127.0.0.1:" + port + " 连不进去");
            }
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("容器里 RpcServer 是单例：事件监听器启动的就是 getBean 拿到的那一台")
    void serverStartedByTheListenerIsTheSingleton() {
        ZRpcProperties props = new ZRpcProperties();
        props.getServer().setHost("0.0.0.0");
        props.getServer().setPort(0);

        AnnotationConfigApplicationContext ctx = context(props);
        try {
            RpcServer bean = ctx.getBean(RpcServer.class);
            assertTrue(bean.isStarted(), "server.enabled 默认真，refresh 之后这台不该是停的");
            int port = bean.getPort();
            assertTrue(port > 0, "真实绑定端口应当回读得到，实际: " + port);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
                assertTrue(socket.isConnected());
            } catch (java.io.IOException e) {
                throw new AssertionError("单例已 started 但 127.0.0.1:" + port + " 连不进", e);
            }
        } finally {
            ctx.close();
        }
    }

    @Test
    @DisplayName("server.enabled=false 时监听器不该启动这台 bean")
    void disabledFlagKeepsTheBeanUnstarted() {
        ZRpcProperties props = new ZRpcProperties();
        props.getServer().setEnabled(false);
        props.getServer().setHost("0.0.0.0");
        props.getServer().setPort(0);

        AnnotationConfigApplicationContext ctx = context(props);
        try {
            RpcServer bean = ctx.getBean(RpcServer.class);
            assertFalse(bean.isStarted(), "server.enabled=false 却把端口开了");
        } finally {
            ctx.close();
        }
    }
}
