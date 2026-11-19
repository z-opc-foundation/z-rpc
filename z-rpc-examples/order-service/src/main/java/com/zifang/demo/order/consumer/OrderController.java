package com.zifang.demo.order.consumer;

import com.zifang.demo.order.api.OrderDTO;
import com.zifang.demo.user.api.UserDTO;
import com.zifang.z.meta.api.annotation.BizApi;
import com.zifang.z.meta.api.annotation.BizField;
import com.zifang.z.meta.api.annotation.BizParam;
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
    @BizApi(summary = "createOrder", description = "", tags = { "z-rpc" })
    public OrderDTO createOrder(@RequestParam("userId") @BizParam(name = "userId", type = BizField.FieldType.INTEGER, description = "", position = BizParam.Position.QUERY) Long userId,
                               @RequestParam("amount")
                               @BizParam(name = "amount", type = BizField.FieldType.DECIMAL, description = "", position = BizParam.Position.QUERY) BigDecimal amount) {
        return orderConsumer.createOrder(userId, amount);
    }

    @GetMapping("/user/{userId}")
    @BizApi(summary = "getUser", description = "", tags = { "z-rpc" })
    public UserDTO getUser(@PathVariable("userId") @BizParam(name = "userId", type = BizField.FieldType.INTEGER, description = "", position = BizParam.Position.PATH) Long userId) {
        return orderConsumer.queryUser(userId);
    }
}



