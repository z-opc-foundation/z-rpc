package com.zifang.demo.order.consumer;

import com.zifang.demo.order.api.OrderDTO;
import com.zifang.demo.user.api.UserDTO;
import com.zifang.demo.user.api.UserService;
import com.zifang.z.meta.api.annotation.BizMethod;
import com.zifang.z.rpc.annotation.ZRpcReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 订单服务（消费者）
 * <p>
 * 通过 @ZRpcReference 注入 UserService 代理，调用远端 user-service。
 */
@Service
public class OrderConsumer {

    @ZRpcReference(version = "1.0.0", timeout = 3000, retries = 2, loadbalance = "random",
            url = "zrpc://127.0.0.1:20880")
    private UserService userService;

    private final AtomicLong orderIdGen = new AtomicLong(1000);

    /**
     * 创建订单（远程调用 user-service）
     */
    @BizMethod(name = "创建订单（远程调用 user-service）", module = "z-rpc", description = "创建订单（远程调用 user-service）", operation = BizMethod.Operation.CREATE)
    public OrderDTO createOrder(Long userId, BigDecimal amount) {
        // RPC 调用获取用户信息
        UserDTO user = userService.getUser(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }

        OrderDTO order = new OrderDTO(
                orderIdGen.incrementAndGet(),
                user.getId(),
                user.getName(),
                amount,
                "CREATED"
        );
        System.out.println("[OrderConsumer] Created order: " + order);
        return order;
    }

    @BizMethod(name = "queryUser", module = "z-rpc", description = "", operation = BizMethod.Operation.QUERY)
    public UserDTO queryUser(Long userId) {
        return userService.getUser(userId);
    }
}



