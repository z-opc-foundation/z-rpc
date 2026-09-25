package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.annotation.ZRpcService;
import com.zifang.z.rpc.remoting.RpcServer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

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
        // 必须先看"真实类"：CGLIB 代理子类的 getAnnotation 读不到父类上的注解，
        // 而它的 getInterfaces() 返回的是 SpringProxy/Advised 这类标记接口，
        // 拿它当服务名会把业务接口导出成一个没人认识的键。
        Class<?> userClass = ClassUtils.getUserClass(bean);
        ZRpcService annotation = AnnotatedElementUtils.findMergedAnnotation(userClass, ZRpcService.class);
        if (annotation == null) return bean;

        List<Class<?>> interfaces = new ArrayList<Class<?>>();
        if (annotation.interfaceClass() != void.class) {
            interfaces.add(annotation.interfaceClass());
        } else {
            // javadoc 承诺"不指定则使用该类实现的所有接口"，所以这里是全量而非 [0]
            for (Class<?> iface : ClassUtils.getAllInterfacesForClass(userClass)) {
                if (iface.getName().startsWith("java.") || iface.getName().startsWith("org.springframework.")) {
                    continue;
                }
                interfaces.add(iface);
            }
        }
        if (interfaces.isEmpty()) {
            log.warn("[ZRpcService] {} has no interfaces, skipping", beanName);
            return bean;
        }

        for (Class<?> iface : interfaces) {
            export(bean, beanName, iface, annotation.version());
        }
        return bean;
    }

    private void export(final Object bean, final String beanName,
                        final Class<?> interfaceClass, final String version) {
        Runnable task = () -> {
            try {
                rpcServer.register(interfaceClass, bean, version);
                log.info("[ZRpcService] exported {} -> {}", beanName, interfaceClass.getName());
            } catch (Exception e) {
                log.error("[ZRpcService] failed to export " + beanName, e);
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
