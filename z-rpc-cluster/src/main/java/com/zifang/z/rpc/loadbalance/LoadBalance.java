package com.zifang.z.rpc.loadbalance;

import com.zifang.z.rpc.common.URL;
import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;

import java.util.List;

/**
 * 负载均衡接口
 * 参考 Dubbo 的 LoadBalance 设计
 */
public interface LoadBalance {

    /**
     * 从 invokers 列表中选择一个
     *
     * @param invokers   可调用的服务列表
     * @param url        引用 URL
     * @param invocation 调用信息
     * @return 选中的 invoker
     */
    <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation);

    /**
     * 获取负载均衡名称
     */
    String getName();
}
