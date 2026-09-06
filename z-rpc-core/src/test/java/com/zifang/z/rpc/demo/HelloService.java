package com.zifang.z.rpc.demo;

/**
 * 示例服务接口
 */
public interface HelloService {

    /**
     * 打招呼
     *
     * @param name 名字
     * @return 问候语
     */
    String sayHello(String name);

    /**
     * 加法
     *
     * @param a 第一个数
     * @param b 第二个数
     * @return 和
     */
    int add(int a, int b);

    /**
     * 获取用户信息
     *
     * @param id 用户ID
     * @return 用户信息
     */
    User getUser(Long id);

    /**
     * 创建用户
     *
     * @param user 用户信息
     * @return 创建后的用户
     */
    User createUser(User user);
}
