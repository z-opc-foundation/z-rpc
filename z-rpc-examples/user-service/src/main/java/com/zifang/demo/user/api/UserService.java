package com.zifang.demo.user.api;

import java.util.List;

/**
 * 用户服务 RPC 接口
 */
public interface UserService {

    UserDTO getUser(Long id);

    List<UserDTO> listUsers();

    UserDTO createUser(String name, String email, Integer age);
}
