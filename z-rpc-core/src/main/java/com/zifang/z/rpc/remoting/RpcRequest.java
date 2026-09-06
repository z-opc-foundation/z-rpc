package com.zifang.z.rpc.remoting;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class RpcRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求 ID
     */
    private String requestId;

    /**
     * 服务接口名
     */
    private String interfaceName;

    /**
     * 方法名
     */
    private String methodName;

    /**
     * 参数类型
     */
    private Class<?>[] parameterTypes;

    /**
     * 参数值
     */
    private Object[] arguments;

    /**
     * 附加信息
     */
    private Map<String, String> attachments = new HashMap<>();

    /**
     * 获取服务版本
     */
    public String getVersion() {
        return attachments.getOrDefault("version", "1.0.0");
    }

    /**
     * 获取服务分组
     */
    public String getGroup() {
        return attachments.getOrDefault("group", "");
    }


    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getArguments() {
        return arguments;
    }

    public void setArguments(Object[] arguments) {
        this.arguments = arguments;
    }

    public Map<String, String> getAttachments() {
        return attachments;
    }

    public void setAttachments(Map<String, String> attachments) {
        this.attachments = attachments;
    }
}
