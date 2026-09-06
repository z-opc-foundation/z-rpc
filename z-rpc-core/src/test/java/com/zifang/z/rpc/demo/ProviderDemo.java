package com.zifang.z.rpc.demo;

import com.zifang.z.rpc.config.ServiceConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 服务提供者示例
 */
public class ProviderDemo {


    public static void main(String[] args) throws Exception {

        Logger log = LogManager.getLogger(ProviderDemo.class);

        // 创建服务配置
        ServiceConfig<HelloService> serviceConfig = new ServiceConfig<>();

        // 设置服务接口和实现
        serviceConfig.setInterfaceClass(HelloService.class);
        serviceConfig.setRef(new HelloServiceImpl());

        // 设置版本和分组
        serviceConfig.setVersion("1.0.0");
        serviceConfig.setGroup("default");

        // 设置服务参数
        serviceConfig.setWeight(100);
        serviceConfig.setTimeout(3000);
        serviceConfig.setRetries(2);

        // 设置网络参数
        serviceConfig.setPort(20880);

        // 设置注册中心（可选，如果不设置则不注册到注册中心）
        // serviceConfig.setRegistry("127.0.0.1:8084");

        // 导出服务
        serviceConfig.export();

        log.info("Service exported successfully!");
        log.info("Press any key to stop...");

        // 等待用户输入
        System.in.read();

        // 取消导出
        serviceConfig.unexport();
        log.info("Service unexported.");
    }
}
