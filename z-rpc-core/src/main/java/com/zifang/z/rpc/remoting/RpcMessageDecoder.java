package com.zifang.z.rpc.remoting;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

/**
 * RPC 消息解码器
 * 将字节流解码为 RPC 请求/响应对象
 */
public class RpcMessageDecoder extends LengthFieldBasedFrameDecoder {

    // 魔数
    private static final byte[] MAGIC = new byte[]{'Z', 'R', 'P', 'C'};

    // 消息类型：请求
    private static final byte MSG_TYPE_REQUEST = 1;

    // 消息类型：响应
    private static final byte MSG_TYPE_RESPONSE = 2;

    // 头部大小：魔数(4) + 版本(1) + 消息类型(1) + 数据长度(4) = 10
    private static final int HEADER_SIZE = 10;

    // 最大帧长度 10MB
    private static final int MAX_FRAME_LENGTH = 10 * 1024 * 1024;

    public RpcMessageDecoder() {
        super(MAX_FRAME_LENGTH, 6, 4, 0, 0, true);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        // 先检查是否有足够的字节读取头部
        if (in.readableBytes() < HEADER_SIZE) {
            return null;
        }

        // 标记当前读位置
        in.markReaderIndex();

        // 读取魔数
        byte[] magic = new byte[4];
        in.readBytes(magic);
        if (!isMagicValid(magic)) {
            // 魔数不匹配，关闭连接
            in.resetReaderIndex();
            throw new IllegalArgumentException("Invalid magic number");
        }

        // 读取版本号
        byte version = in.readByte();
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported version: " + version);
        }

        // 读取消息类型
        byte msgType = in.readByte();

        // 读取数据长度
        int dataLength = in.readInt();
        if (dataLength < 0 || dataLength > MAX_FRAME_LENGTH) {
            throw new IllegalArgumentException("Invalid data length: " + dataLength);
        }

        // 检查是否有足够的数据
        if (in.readableBytes() < dataLength) {
            // 数据不完整，重置读位置
            in.resetReaderIndex();
            return null;
        }

        // 读取数据
        byte[] data = new byte[dataLength];
        in.readBytes(data);

        // 反序列化
        Object obj = deserialize(data);

        // 根据消息类型返回
        if (msgType == MSG_TYPE_REQUEST && obj instanceof RpcRequest) {
            return obj;
        } else if (msgType == MSG_TYPE_RESPONSE && obj instanceof RpcResponse) {
            return obj;
        } else {
            throw new IllegalArgumentException("Message type mismatch");
        }
    }

    /**
     * 验证魔数
     */
    private boolean isMagicValid(byte[] magic) {
        if (magic == null || magic.length != 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (magic[i] != MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Java 原生反序列化
     */
    private Object deserialize(byte[] data) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            return ois.readObject();
        }
    }
}
