package com.ligg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * @author Ligg
 * @create_time 2025/12/21 12:40
 * @update_time 2025/12/21 12:40
 **/
@Slf4j
@Component
public class NetworkAddressPrinter implements ApplicationRunner {

    @Value("${server.port}")
    private String port;

    @Override
    public void run(ApplicationArguments args) {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            String hostAddress = localHost.getHostAddress();
            System.out.println("===============================================");
            System.out.println("局域网访问地址: http://" + hostAddress + ":" + this.port);
            System.out.println("本地访问地址: http://localhost:" + this.port);
            System.out.println("===============================================");
        } catch (UnknownHostException e) {
            log.error("无法获取本机IP地址", e);
        }
    }
}
