package com.zifang.z.rpc.starter.config;

import com.zifang.z.rpc.annotation.ZRpcReference;
import com.zifang.z.rpc.config.ReferenceConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Z-RPC 引用注入器
 * 扫描所有 Bean 的字段和方法，发现 {@link ZRpcReference} 后注入代理
 */
@Component
public class ZRpcReferenceInjector implements InstantiationAwareBeanPostProcessor, ApplicationContextAware, BeanFactoryAware {

    private static final Logger log = LogManager.getLogger(ZRpcReferenceInjector.class);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setBeanFactory(org.springframework.beans.factory.BeanFactory beanFactory) throws BeansException {
        // no-op
    }

    @Override
    public PropertyValues postProcessPropertyValues(PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {
        Class<?> targetClass = bean.getClass();
        ReflectionUtils.doWithFields(targetClass, field -> injectField(field, bean));
        return pvs;
    }

    private void injectField(Field field, Object bean) {
        ZRpcReference annotation = field.getAnnotation(ZRpcReference.class);
        if (annotation == null) return;
        if (!field.canAccess(bean)) {
            field.setAccessible(true);
        }
        try {
            ReferenceConfig<?> config = buildReferenceConfig(annotation, field.getType());
            Object proxy = config.get();
            field.set(bean, proxy);
            log.info("[ZRpcReference] injected {} -> {}", field.getName(), field.getType().getName());
        } catch (Exception e) {
            log.error("Failed to inject ZRpcReference: " + field.getName(), e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ReferenceConfig<?> buildReferenceConfig(ZRpcReference annotation, Class interfaceClass) {
        ReferenceConfig config = new ReferenceConfig();
        config.setInterfaceClass(interfaceClass);
        config.setInterfaceName(annotation.interfaceName().isEmpty() ? interfaceClass.getName() : annotation.interfaceName());
        config.setVersion(annotation.version());
        config.setGroup(annotation.group());
        config.setTimeout(annotation.timeout());
        config.setRetries(annotation.retries());
        config.setLoadbalance(annotation.loadbalance());
        config.setCluster(annotation.cluster());
        config.setRegistry(annotation.registry());
        if (annotation.url() != null && !annotation.url().isEmpty()) {
            config.setUrl(annotation.url());
        }
        return config;
    }
}
