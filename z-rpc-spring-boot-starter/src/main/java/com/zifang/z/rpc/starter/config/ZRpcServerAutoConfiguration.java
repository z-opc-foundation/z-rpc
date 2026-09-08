package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.remoting.RpcServer;
import com.zifang.z.rpc.starter.properties.ZRpcProperties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.net.InetAddress;

/**
 * 服务端自动装配
 *
 * 在 Spring 上下文启动完成后，启动 Netty RPC Server。
 */
@Configuration
public class ZRpcServerAutoConfiguration {

    private static final Logger log = LogManager.getLogger(ZRpcServerAutoConfiguration.class);

    @Autowired
    private ZRpcProperties properties;

    @Bean(destroyMethod = "stop")
    public RpcServer rpcServer() {
        String host = properties.getServer().getHost();
        int port = properties.getServer().getPort();
        String bindHost = "0.0.0.0".equals(host) ? resolveLocalIp() : host;
        return new RpcServer(bindHost, port);
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        if (!properties.getServer().isEnabled()) {
            return;
        }
        try {
            rpcServer().start(true);
            log.info("[ZRpcServer] Netty RPC server started at {}:{}",
                    rpcServer().getHost(), rpcServer().getPort());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to start RPC server", e);
        }
    }

    private String resolveLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
