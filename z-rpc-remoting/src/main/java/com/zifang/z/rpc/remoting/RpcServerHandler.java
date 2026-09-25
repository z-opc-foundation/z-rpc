package com.zifang.z.rpc.remoting;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * RPC 服务器处理器
 */
public class RpcServerHandler extends SimpleChannelInboundHandler<RpcRequest> {

    private final Logger log = LogManager.getLogger(this.getClass());

    private final Map<String, Object> serviceMap;

    public RpcServerHandler(Map<String, Object> serviceMap) {
        this.serviceMap = serviceMap;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest request) {
        RpcResponse response = handleRequest(request);
        ctx.writeAndFlush(response);
    }

    private RpcResponse handleRequest(RpcRequest request) {
        RpcResponse response = new RpcResponse();
        response.setRequestId(request.getRequestId());

        try {
            // 服务查找必须与 RpcServer.register 用同一个拼键函数。
            // 先按 {interface}:{version} 精确匹配；只有该接口没有任何带版本登记时，
            // 才退回 registerService() 留下的裸接口名条目。
            String serviceName = request.getInterfaceName();
            String version = request.getVersion();
            Object service = serviceMap.get(RpcServer.serviceKey(serviceName, version));
            // 退回裸键不等于"精确键没查到就能退"：任何一条 registerService() 留下的
            // 裸键条目替该接口的所有版本冒充应答，版本维度就直接消失了。放行的两种情形是
            // "该接口没有任何带版本的登记"（没有别的候选），或请求版本是 RpcRequest 的
            // 缺省值 —— 不带 version 附件的请求在服务端与"要 1.0.0"不可区分，砍掉这一条
            // 会让裸键 Provider 对所有不显式设版本的消费端永久不可达。
            if (service == null && version != null && !version.isEmpty()
                    && (RpcRequest.DEFAULT_VERSION.equals(version)
                            || !hasVersionedRegistration(serviceName))) {
                service = serviceMap.get(serviceName);
            }

            if (service == null) {
                throw new RuntimeException("Service not found: " + serviceName);
            }

            // 获取方法
            Class<?> serviceClass = service.getClass();
            Method method = findMethod(serviceClass, request.getMethodName(), request.getParameterTypes());

            if (method == null) {
                throw new RuntimeException("Method not found: " + request.getMethodName());
            }

            // 调用方法
            method.setAccessible(true);
            Object result = method.invoke(service, request.getArguments());

            response.setResult(result);
            log.debug("RPC call success: {}.{}", serviceName, request.getMethodName());

        } catch (Exception e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException
                    ? ((java.lang.reflect.InvocationTargetException) e).getTargetException()
                    : e;
            response.setException(cause);
            response.setErrorMessage(cause.getMessage());
            log.error("RPC call failed: {}", cause.getMessage(), cause);
        }

        return response;
    }

    /**
     * 该接口是否存在任何一条带版本的登记（键形如 {@code {interface}:{version}}）。
     */
    private boolean hasVersionedRegistration(String serviceName) {
        String prefix = serviceName + ":";
        for (String key : serviceMap.keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
        // parameterTypes 是从线上请求里取来的，属于系统边界：整组为 null、或数组里含
        // null 元素时，签名一律视为不可用，让它干净地落到 "Method not found"。
        // 不挡的话兜底扫描会在这两处踩空 —— getMethod(name, null) 在 JDK 9+ 只抛
        // NoSuchMethodException（不再 NPE），于是走进下面的循环，比较参数个数时对 null
        // 数组取 .length 抛一条 NPE，参数数组里含 null 元素时是对 null 调
        // isAssignableFrom 抛一条 message 为 null 的 NPE —— 消费端拿到的错误说明
        // 里既没有方法名也没有签名，认不出是哪一次调用出的问题。
        if (paramTypes == null) {
            return null;
        }
        for (Class<?> paramType : paramTypes) {
            if (paramType == null) {
                return null;
            }
        }
        try {
            return clazz.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // 尝试匹配父类方法
            for (Method method : clazz.getMethods()) {
                if (method.getName().equals(methodName)) {
                    Class<?>[] methodParamTypes = method.getParameterTypes();
                    if (methodParamTypes.length == paramTypes.length) {
                        boolean match = true;
                        for (int i = 0; i < methodParamTypes.length; i++) {
                            if (!methodParamTypes[i].isAssignableFrom(paramTypes[i])) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            return method;
                        }
                    }
                }
            }
            return null;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("RPC server exception: {}", cause.getMessage(), cause);
        ctx.close();
    }
}
