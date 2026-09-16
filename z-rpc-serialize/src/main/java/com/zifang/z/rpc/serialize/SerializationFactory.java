package com.zifang.z.rpc.serialize;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器工厂
 * <p>
 * 按 contentTypeId 获取对应的序列化器实例，单例缓存。
 */
public class SerializationFactory {

    private static final Logger log = LogManager.getLogger(SerializationFactory.class);

    private static final Map<Byte, Serialization> SERIALIZERS = new ConcurrentHashMap<>();

    static {
        // 默认注册 5 种序列化器
        register(new JavaSerialization());
        register(new JsonSerialization());
        register(new Hessian2Serialization());
        register(new KryoSerialization());
        register(new ProtobufSerialization());
    }

    /**
     * 注册一个序列化器
     */
    public static void register(Serialization serialization) {
        if (serialization == null) return;
        SERIALIZERS.put(serialization.getContentTypeId(), serialization);
        log.info("Registered serialization: id=0x{}, class={}",
                String.format("%02X", serialization.getContentTypeId()),
                serialization.getClass().getSimpleName());
    }

    /**
     * 按 contentTypeId 获取序列化器
     *
     * @param id 协议头中的序列化器 ID
     * @return 序列化器实例
     */
    public static Serialization get(byte id) {
        Serialization ser = SERIALIZERS.get(id);
        if (ser == null) {
            // fallback 到 Hessian2
            log.warn("No serialization found for id 0x{}, fallback to Hessian2", String.format("%02X", id));
            ser = SERIALIZERS.get(com.zifang.z.rpc.common.ProtocolConstants.SERIALIZE_HESSIAN2);
        }
        return ser;
    }

    /**
     * 按名称获取（如 "hessian2" / "json"）
     */
    public static Serialization getByName(String name) {
        if (name == null) return null;
        switch (name.toLowerCase()) {
            case "java":     return SERIALIZERS.get(com.zifang.z.rpc.common.ProtocolConstants.SERIALIZE_JAVA);
            case "json":     return SERIALIZERS.get(com.zifang.z.rpc.common.ProtocolConstants.SERIALIZE_JSON);
            case "hessian2": return SERIALIZERS.get(com.zifang.z.rpc.common.ProtocolConstants.SERIALIZE_HESSIAN2);
            case "kryo":     return SERIALIZERS.get(com.zifang.z.rpc.common.ProtocolConstants.SERIALIZE_KRYO);
            case "protobuf": return SERIALIZERS.get(com.zifang.z.rpc.common.ProtocolConstants.SERIALIZE_PROTOBUF);
            default:
                log.warn("Unknown serialization name: {}", name);
                return null;
        }
    }
}
