package com.zifang.demo.user;

import com.zifang.z.rpc.starter.annotation.EnableZRpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * User Service 启动类
 */
@SpringBootApplication(scanBasePackages = {"com.zifang.demo.user", "com.zifang.z.rpc.starter"})
@EnableZRpc(scanBasePackages = "com.zifang.demo.user")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
