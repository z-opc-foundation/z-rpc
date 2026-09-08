package com.zifang.z.rpc.common;

/**
 * Z-RPC 协议常量
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    /**
     * 协议魔数: 0x5A525043 = "ZRPC" 的大端整数表示
     */
    public static final int MAGIC_NUMBER = 0x5A525043;

    /**
     * 协议版本
     */
    public static final byte VERSION = 1;

    /**
     * 消息头长度（字节）
     */
    public static final int HEADER_LENGTH = 24;

    // ====================== 消息类型 ======================

    /** 请求消息 */
    public static final byte MSG_TYPE_REQUEST = 1;
    /** 响应消息 */
    public static final byte MSG_TYPE_RESPONSE = 2;
    /** 心跳请求 */
    public static final byte MSG_TYPE_HEARTBEAT_REQ = 3;
    /** 心跳响应 */
    public static final byte MSG_TYPE_HEARTBEAT_RES = 4;

    // ====================== 压缩方式 ======================

    /** 不压缩 */
    public static final byte COMPRESS_NONE = 0;
    /** Gzip 压缩 */
    public static final byte COMPRESS_GZIP = 1;

    // ====================== 响应状态码 ======================

    /** 成功 */
    public static final short STATUS_OK = 0;
    /** 超时 */
    public static final short STATUS_TIMEOUT = 1;
    /** 连接断开 */
    public static final short STATUS_CONN_LOST = 2;
    /** 业务异常 */
    public static final short STATUS_BIZ_ERROR = 3;
    /** 无 Provider */
    public static final short STATUS_NO_PROVIDER = 4;
    /** 错误请求 */
    public static final short STATUS_BAD_REQUEST = 5;
    /** 序列化失败 */
    public static final short STATUS_SERIALIZATION = 6;
    /** 协议错误 */
    public static final short STATUS_PROTOCOL = 7;
    /** 限流 */
    public static final short STATUS_LIMIT = 8;
    /** 禁止 */
    public static final short STATUS_FORBIDDEN = 9;
    /** 未知错误 */
    public static final short STATUS_UNKNOWN = 99;

    // ====================== 序列化器 ID ======================

    /** Java 原生序列化 */
    public static final byte SERIALIZE_JAVA = 1;
    /** JSON（基于 z-util） */
    public static final byte SERIALIZE_JSON = 2;
    /** Hessian2 */
    public static final byte SERIALIZE_HESSIAN2 = 3;
    /** Kryo */
    public static final byte SERIALIZE_KRYO = 4;
    /** Protobuf */
    public static final byte SERIALIZE_PROTOBUF = 5;

    // ====================== 默认参数 ======================

    public static final int DEFAULT_TIMEOUT_MS = 3000;
    public static final int DEFAULT_HEARTBEAT_INTERVAL_MS = 20000;
    public static final int DEFAULT_HEARTBEAT_TIMEOUT_MS = 60000;
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3000;
    public static final int DEFAULT_PORT = 20880;
    public static final int DEFAULT_PAYLOAD = 8 * 1024 * 1024; // 8MB
    public static final int DEFAULT_COMPRESS_THRESHOLD = 1024;
    public static final int DEFAULT_IO_THREADS = 8;
    public static final int DEFAULT_WORKER_THREADS = 200;
    public static final String DEFAULT_PROTOCOL = "z-rpc";
    public static final String DEFAULT_SERIALIZATION = "hessian2";
    public static final String DEFAULT_LOADBALANCE = "random";
    public static final String DEFAULT_CLUSTER = "failover";
}
