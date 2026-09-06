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
            // 获取服务实例
            String serviceName = request.getInterfaceName();
            Object service = serviceMap.get(serviceName);

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

    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) {
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
