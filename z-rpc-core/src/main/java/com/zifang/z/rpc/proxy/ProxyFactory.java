package com.zifang.z.rpc.proxy;

import com.zifang.z.rpc.common.URL;
import com.zifang.z.rpc.invoke.Invocation;
import com.zifang.z.rpc.invoke.Invoker;
import com.zifang.z.rpc.invoke.Result;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 代理工厂
 * 用于创建远程服务的本地代理
 */
public class ProxyFactory {

    /**
     * 获取服务代理
     *
     * @param interfaceClass 服务接口类
     * @param invoker        集群 Invoker
     * @return 服务代理
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass, Invoker<T> invoker) {
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                new InvokerInvocationHandler<>(invoker, interfaceClass)
        );
    }

    /**
     * 代理调用处理器
     */
    private static class InvokerInvocationHandler<T> implements InvocationHandler {

        private final Invoker<T> invoker;
        private final Class<T> interfaceClass;

        InvokerInvocationHandler(Invoker<T> invoker, Class<T> interfaceClass) {
            this.invoker = invoker;
            this.interfaceClass = interfaceClass;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            Class<?>[] paramTypes = method.getParameterTypes();

            // 处理 Object 方法
            if ("toString".equals(methodName) && paramTypes.length == 0) {
                return invoker.toString();
            }
            if ("hashCode".equals(methodName) && paramTypes.length == 0) {
                return invoker.hashCode();
            }
            if ("equals".equals(methodName) && paramTypes.length == 1) {
                return proxy == args[0];
            }

            // 构建调用信息
            RpcInvocation invocation = new RpcInvocation();
            invocation.setServiceInterface(interfaceClass.getName());
            invocation.setMethodName(methodName);
            invocation.setParameterTypes(paramTypes);
            invocation.setArguments(args != null ? args : new Object[0]);
            invocation.setInvokerUrl(invoker.getUrl());

            // 执行调用
            Result result = invoker.invoke(invocation);

            // 处理结果
            if (result.hasException()) {
                throw result.getException();
            }
            return result.getValue();
        }
    }

    /**
     * RPC 调用实现
     */
    private static class RpcInvocation implements Invocation {

        private String serviceInterface;
        private String methodName;
        private Class<?>[] parameterTypes;
        private Object[] arguments;
        private URL invokerUrl;

        @Override
        public String getServiceInterface() {
            return serviceInterface;
        }

        public void setServiceInterface(String serviceInterface) {
            this.serviceInterface = serviceInterface;
        }

        @Override
        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }

        @Override
        public Class<?>[] getParameterTypes() {
            return parameterTypes;
        }

        public void setParameterTypes(Class<?>[] parameterTypes) {
            this.parameterTypes = parameterTypes;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        public void setArguments(Object[] arguments) {
            this.arguments = arguments;
        }

        @Override
        public java.util.Map<String, String> getAttachments() {
            return java.util.Collections.emptyMap();
        }

        @Override
        public String getAttachment(String key) {
            return null;
        }

        @Override
        public String getAttachment(String key, String defaultValue) {
            return defaultValue;
        }

        @Override
        public void setAttachment(String key, String value) {
        }

        @Override
        public URL getInvokerUrl() {
            return invokerUrl;
        }

        public void setInvokerUrl(URL invokerUrl) {
            this.invokerUrl = invokerUrl;
        }
    }
}
