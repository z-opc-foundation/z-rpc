package com.zifang.z.rpc.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Z-RPC Admin 启动入口（端口 9090）
 */
@SpringBootApplication
public class ZRpcAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZRpcAdminApplication.class, args);
    }
}
