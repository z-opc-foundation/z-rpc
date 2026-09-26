package com.zifang.demo.order.consumer;

import com.zifang.demo.order.api.OrderDTO;
import com.zifang.demo.user.api.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Order 控制器（演示 HTTP API）
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderConsumer orderConsumer;

    // 参数名必须写死：examples 的 parent 不是 spring-boot-starter-parent，
    // 编译时没有 -parameters，靠字节码里的形参名反射会在运行时抛
    // "Name for argument of type [java.lang.Long] not specified"（250 机实跑复现，README 的 curl 500）。
    @PostMapping("/create")
    public OrderDTO createOrder(@RequestParam("userId") Long userId,
                               @RequestParam("amount") BigDecimal amount) {
        return orderConsumer.createOrder(userId, amount);
    }

    @GetMapping("/user/{userId}")
    public UserDTO getUser(@PathVariable("userId") Long userId) {
        return orderConsumer.queryUser(userId);
    }
}
