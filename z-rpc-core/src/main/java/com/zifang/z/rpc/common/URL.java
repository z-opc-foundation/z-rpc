package com.zifang.z.rpc.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * URL 统一资源定位符
 * 参考 Dubbo 设计，用于描述服务地址和参数
 * <p>
 * 格式：protocol://host:port/serviceName?key1=value1&key2=value2
 */
public class URL implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 协议
     */
    private String protocol;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口
     */
    private int port;

    /**
     * 服务接口名
     */
    private String serviceInterface;

    /**
     * 服务分组
     */
    private String group;

    /**
     * 服务版本
     */
    private String version;

    /**
     * 附加参数
     */
    private Map<String, String> parameters = new HashMap<>();

    private int weight;

    public URL() {
    }

    public URL(String protocol, String host, int port) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
    }

    public URL(String protocol, String host, int port, String serviceInterface) {
        this.protocol = protocol;
        this.host = host;
        this.port = port;
        this.serviceInterface = serviceInterface;
    }

    /**
     * 从 URL 字符串解析
     */
    public static URL valueOf(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        URL result = new URL();

        // 解析协议
        int protocolEnd = url.indexOf("://");
        if (protocolEnd > 0) {
            result.protocol = url.substring(0, protocolEnd);
            url = url.substring(protocolEnd + 3);
        }

        // 解析路径和参数
        int pathStart = url.indexOf("/");
        String address;
        String pathAndParams;

        if (pathStart > 0) {
            address = url.substring(0, pathStart);
            pathAndParams = url.substring(pathStart + 1);
        } else {
            address = url;
            pathAndParams = "";
        }

        // 解析 host:port
        int colonIndex = address.indexOf(":");
        if (colonIndex > 0) {
            result.host = address.substring(0, colonIndex);
            result.port = Integer.parseInt(address.substring(colonIndex + 1));
        } else {
            result.host = address;
        }

        // 解析 serviceInterface 和参数
        if (!pathAndParams.isEmpty()) {
            int paramStart = pathAndParams.indexOf("?");
            if (paramStart > 0) {
                result.serviceInterface = pathAndParams.substring(0, paramStart);
                String paramStr = pathAndParams.substring(paramStart + 1);
                parseParams(result, paramStr);
            } else {
                result.serviceInterface = pathAndParams;
            }
        }

        return result;
    }

    private static void parseParams(URL url, String paramStr) {
        String[] pairs = paramStr.split("&");
        for (String pair : pairs) {
            int eqIndex = pair.indexOf("=");
            if (eqIndex > 0) {
                String key = pair.substring(0, eqIndex);
                String value = pair.substring(eqIndex + 1);
                url.addParameter(key, value);
            }
        }
    }

    /**
     * 添加参数
     */
    public URL addParameter(String key, String value) {
        if (value != null) {
            parameters.put(key, value);
        }
        return this;
    }

    /**
     * 获取参数
     */
    public String getParameter(String key) {
        return parameters.get(key);
    }

    /**
     * 获取参数，带默认值
     */
    public String getParameter(String key, String defaultValue) {
        String value = parameters.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取服务唯一标识：group/service:version
     */
    public String getServiceKey() {
        StringBuilder sb = new StringBuilder();
        if (group != null && !group.isEmpty()) {
            sb.append(group).append("/");
        }
        sb.append(serviceInterface);
        if (version != null && !version.isEmpty()) {
            sb.append(":").append(version);
        }
        return sb.toString();
    }

    /**
     * 获取地址：ip:port
     */
    public String getAddress() {
        return host + ":" + port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (protocol != null) {
            sb.append(protocol).append("://");
        }
        sb.append(host).append(":").append(port);
        if (serviceInterface != null) {
            sb.append("/").append(serviceInterface);
        }
        if (!parameters.isEmpty()) {
            sb.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
        }
        return sb.toString();
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }
}
