package com.zifang.demo.user.impl;

import com.zifang.demo.user.api.UserDTO;
import com.zifang.demo.user.api.UserService;
import com.zifang.z.rpc.annotation.ZRpcService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 用户服务实现
 */
@Service
@ZRpcService(interfaceClass = UserService.class, version = "1.0.0", weight = 100)
public class UserServiceImpl implements UserService {

    private final ConcurrentHashMap<Long, UserDTO> userMap = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(100);

    public UserServiceImpl() {
        // 预置测试数据
        userMap.put(1L, new UserDTO(1L, "Alice", "alice@example.com", 25));
        userMap.put(2L, new UserDTO(2L, "Bob", "bob@example.com", 30));
        userMap.put(3L, new UserDTO(3L, "Charlie", "charlie@example.com", 28));
    }

    @Override
    public UserDTO getUser(Long id) {
        return userMap.get(id);
    }

    @Override
    public List<UserDTO> listUsers() {
        return new ArrayList<>(userMap.values());
    }

    @Override
    public UserDTO createUser(String name, String email, Integer age) {
        Long id = idGen.incrementAndGet();
        UserDTO user = new UserDTO(id, name, email, age);
        userMap.put(id, user);
        return user;
    }
}
