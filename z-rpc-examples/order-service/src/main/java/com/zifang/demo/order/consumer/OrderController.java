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

    @PostMapping("/create")
    public OrderDTO createOrder(@RequestParam Long userId, @RequestParam BigDecimal amount) {
        return orderConsumer.createOrder(userId, amount);
    }

    @GetMapping("/user/{userId}")
    public UserDTO getUser(@PathVariable Long userId) {
        return orderConsumer.queryUser(userId);
    }
}
