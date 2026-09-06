package com.zifang.z.rpc.demo;

import com.zifang.z.rpc.annotation.ZRpcService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 示例服务实现
 */
@ZRpcService(
        interfaceClass = HelloService.class,
        version = "1.0.0",
        group = "default",
        weight = 100,
        timeout = 3000,
        retries = 2
)
public class HelloServiceImpl implements HelloService {

    private final Logger log = LogManager.getLogger(this.getClass());

    @Override
    public String sayHello(String name) {
        log.info("Received sayHello request, name={}", name);
        return "Hello, " + name + "! Welcome to Z-RPC!";
    }

    @Override
    public int add(int a, int b) {
        log.info("Received add request, a={}, b={}", a, b);
        return a + b;
    }

    @Override
    public User getUser(Long id) {
        log.info("Received getUser request, id={}", id);

        User user = new User();
        user.setName("User_" + id);
        user.setAge(25);
        user.setEmail("user" + id + "@example.com");
        user.setPhone("13800138000");
        user.setAddress("Beijing, China");


        return user;
    }

    @Override
    public User createUser(User user) {
        log.info("Received createUser request, user={}", user);
        if (user.getId() == null) {
            user.setId(System.currentTimeMillis());
        }
        return user;
    }
}
