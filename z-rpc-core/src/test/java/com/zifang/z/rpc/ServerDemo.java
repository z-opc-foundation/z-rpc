package com.zifang.z.rpc;

public class ServerDemo {
    public static void main(String[] args) throws InterruptedException {
        RpcServer server = new RpcServer(RpcConstants.PORT);
        server.registerService(HelloService.class, new HelloServiceImpl());
        server.start();
    }
}