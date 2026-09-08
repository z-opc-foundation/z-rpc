package com.zifang.z.rpc.mock;

import java.util.Map;

/**
 * 泛化调用接口
 * <p>
 * 适用于没有接口类的场景（如网关、测试平台），通过方法名+参数类型+参数值调用。
 */
public interface GenericService {

    /**
     * 泛化调用
     *
     * @param method 方法名
     * @param paramTypes 参数类型数组
     * @param args 参数值数组
     * @return 调用结果
     */
    Object $invoke(String method, String[] paramTypes, Object[] args);

    /**
     * 泛化调用（带 Map 形式参数）
     */
    default Object $invokeMap(String method, Map<String, Object> params) {
        if (params == null) {
            return $invoke(method, null, null);
        }
        Object[] args = params.values().toArray();
        String[] paramTypes = new String[args.length];
        int i = 0;
        for (Object arg : args) {
            paramTypes[i++] = arg == null ? "java.lang.Object" : arg.getClass().getName();
        }
        return $invoke(method, paramTypes, args);
    }
}
