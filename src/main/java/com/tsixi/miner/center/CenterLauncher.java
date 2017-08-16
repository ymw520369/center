package com.tsixi.miner.center;

import org.alan.mars.MarsContext;
import org.alan.mars.config.NodeConfig;
import org.alan.mars.netty.NettyServer;
import org.alan.mars.protobuf.LengthFieldChannelInitializer;
import org.alan.mars.protobuf.PbMessageDispatcher;
import org.alan.mars.protobuf.ServerHandler;
import org.alan.mars.uid.UidDao;
import org.alan.mars.uid.impl.UidDaoImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.annotation.Order;

@SpringBootApplication
@ComponentScan({"org.alan","com.tsixi"})
@Order(value = 1)
public class CenterLauncher implements CommandLineRunner {

    private NodeConfig nodeConfig;

    @Autowired
    public CenterLauncher(NodeConfig nodeConfig) {
        this.nodeConfig = nodeConfig;
    }

    public static void main(String[] args) {
        SpringApplication.run(CenterLauncher.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        PbMessageDispatcher messageDispatcher = MarsContext
                .getBean("serverDispatcher", PbMessageDispatcher.class);

        LengthFieldChannelInitializer initializer = new LengthFieldChannelInitializer(messageDispatcher);
        NettyServer nettyServer = new NettyServer(
                nodeConfig.getTcpAddress().getPort(), initializer);
        nettyServer.start();
    }

    @Bean("serverDispatcher")
    public PbMessageDispatcher createServerMessageDispatcher() {
        return new PbMessageDispatcher(ServerHandler.class);
    }

    @Bean
    public UidDao createUidDao(){
        return new UidDaoImpl();
    }
}
