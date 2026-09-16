package com.zifang.demo.order;

import com.zifang.z.rpc.starter.annotation.EnableZRpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Order Service 启动类（消费端）
 */
@SpringBootApplication(scanBasePackages = {"com.zifang.demo.order", "com.zifang.z.rpc.starter"})
@EnableZRpc(scanBasePackages = "com.zifang.demo.order")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
