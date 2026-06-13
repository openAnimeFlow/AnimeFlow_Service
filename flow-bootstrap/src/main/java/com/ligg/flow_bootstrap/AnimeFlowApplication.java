package com.ligg.flow_bootstrap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import java.net.InetAddress;
import java.net.UnknownHostException;

@SpringBootApplication
@ComponentScan(value = "com.ligg")
@MapperScan("com.ligg.**.mapper")
public class AnimeFlowApplication implements ApplicationRunner {

    private static final Log logger = LogFactory.getLog(AnimeFlowApplication.class);

    @Value("${server.port}")
    private String port;

    public static void main(String[] args) {
        SpringApplication.run(AnimeFlowApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String hostAddress = localHost.getHostAddress();
            logger.info("局域网访问地址: http://" + hostAddress + ":" + this.port);
            logger.info("本地访问地址: http://localhost:" + this.port);
        } catch (UnknownHostException e) {
            logger.error("无法获取本机IP地址", e);
        }
    }
}
