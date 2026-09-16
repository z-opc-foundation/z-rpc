package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.annotation.ZRpcService;
import com.zifang.z.rpc.remoting.RpcServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Z-RPC 服务导出器
 * 扫描所有标了 {@link ZRpcService} 的 Bean，调用 RpcServer.register 注册为 RPC 服务。
 */
@Component
public class ZRpcServiceExporter implements BeanPostProcessor {

    private static final Logger log = LogManager.getLogger(ZRpcServiceExporter.class);

    @Autowired(required = false)
    private RpcServer rpcServer;

    private final List<Runnable> pendingRegistrations = new ArrayList<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        ZRpcService annotation = bean.getClass().getAnnotation(ZRpcService.class);
        if (annotation == null) return bean;

        Class<?> interfaceClass = annotation.interfaceClass();
        if (interfaceClass == void.class) {
            Class<?>[] ifs = bean.getClass().getInterfaces();
            if (ifs.length > 0) {
                interfaceClass = ifs[0];
            } else {
                log.warn("[ZRpcService] {} has no interfaces, skipping", beanName);
                return bean;
            }
        }

        final Class<?> finalInterfaceClass = interfaceClass;
        final String finalBeanName = beanName;
        Runnable task = () -> {
            try {
                rpcServer.register(finalInterfaceClass, bean, annotation.version());
                log.info("[ZRpcService] exported {} -> {}", finalBeanName, finalInterfaceClass.getName());
            } catch (Exception e) {
                log.error("[ZRpcService] failed to export " + finalBeanName, e);
            }
        };

        if (rpcServer != null) {
            task.run();
        } else {
            // 暂存，等 RPC Server 启动后再注册
            synchronized (pendingRegistrations) {
                pendingRegistrations.add(task);
            }
        }
        return bean;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        if (rpcServer == null) {
            log.info("[ZRpcService] no RpcServer available, skipping pending registrations");
            return;
        }
        synchronized (pendingRegistrations) {
            for (Runnable task : pendingRegistrations) {
                task.run();
            }
            pendingRegistrations.clear();
        }
    }
}
