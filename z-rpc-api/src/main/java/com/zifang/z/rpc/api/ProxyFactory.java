package com.zifang.z.rpc.api;

import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.invoke.Result;
import com.zifang.z.rpc.common.RpcException;
import com.zifang.z.rpc.spi.SPI;

import java.util.List;

/**
 * 代理工厂
 * <p>
 * 用于创建远程服务的本地代理（消费端），或把本地服务包装为 Invoker（服务端）。
 */
@SPI("jdk")
public interface ProxyFactory {

    /**
     * 创建服务代理
     *
     * @param invoker 目标 Invoker
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    <T> T getProxy(Invoker<T> invoker);

    /**
     * 创建泛化代理（不依赖接口类的代理）
     */
    @SuppressWarnings("unchecked")
    <T> T getProxy(Invoker<T> invoker, boolean generic);

    /**
     * 把代理对象包装为 Invoker（仅服务端导出时使用）
     */
    <T> Invoker<T> getInvoker(T proxy, Class<T> type, java.net.URL url);
}
