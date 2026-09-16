package com.zifang.z.rpc.api;

import com.zifang.z.rpc.common.URL;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.spi.SPI;

/**
 * 协议接口
 * <p>
 * 一个协议对应一种远程通信方式（如 z-rpc、Triple、HTTP）。
 * <p>
 * 资源文件：META-INF/z-rpc/com.zifang.z.rpc.api.Protocol
 *
 * @param <T> 服务接口类型
 */
@SPI("z-rpc")
public interface Protocol {

    /**
     * 默认端口
     */
    int getDefaultPort();

    /**
     * 服务端导出服务
     *
     * @param invoker 服务实现 Invoker
     * @return 暴露器
     */
    <T> Exporter<T> export(Invoker<T> invoker);

    /**
     * 客户端引用服务
     *
     * @param type      服务接口
     * @param url       远程 URL
     * @param consumerUrl 消费方 URL
     * @return 调用 Invoker
     */
    <T> Invoker<T> refer(Class<T> type, URL url, URL consumerUrl);

    /**
     * 销毁协议（释放所有资源）
     */
    void destroy();
}
