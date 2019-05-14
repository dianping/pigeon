/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.netty.provider;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerBossPool;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;

import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.remoting.common.domain.Disposable;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.provider.AbstractServer;
import com.dianping.pigeon.remoting.provider.config.ProviderConfig;
import com.dianping.pigeon.remoting.provider.config.ServerConfig;
import com.dianping.pigeon.threadpool.NamedThreadFactory;
import com.dianping.pigeon.util.NetUtils;
import org.jboss.netty.channel.socket.nio.NioWorkerPool;
import org.jboss.netty.util.ThreadNameDeterminer;

public class NettyServer extends AbstractServer implements Disposable {

    private String ip = null;
    private int port = ServerConfig.DEFAULT_PORT;
    private ServerBootstrap bootstrap;
    private ChannelGroup channelGroup = new DefaultChannelGroup();
    private Channel channel;
    private volatile boolean started = false;

    private static ExecutorService bossExecutor = Executors.newCachedThreadPool(new NamedThreadFactory(
            "Pigeon-Netty-Server-Boss", true));

    private static ExecutorService workerExecutor = Executors.newCachedThreadPool(new NamedThreadFactory(
            "Pigeon-Netty-Server-Worker", true));

    private static final int workerCount = ConfigManagerLoader.getConfigManager().getIntValue(
            "pigeon.provider.netty.workercount", Runtime.getRuntime().availableProcessors() * 2);

    private static ChannelFactory channelFactory = new NioServerSocketChannelFactory(
            new NioServerBossPool(bossExecutor,1, ThreadNameDeterminer.CURRENT),
            new NioWorkerPool(workerExecutor,workerCount,ThreadNameDeterminer.CURRENT)
    );

    public NettyServer() {
        this.bootstrap = new ServerBootstrap(channelFactory);
        this.bootstrap.setPipelineFactory(new NettyServerPipelineFactory(this));
        this.bootstrap.setOption("child.tcpNoDelay", true);
        this.bootstrap.setOption("child.keepAlive", true);
        this.bootstrap.setOption("child.reuseAddress", true);
        this.bootstrap.setOption("child.connectTimeoutMillis", 1000);
    }

    @Override
    public boolean support(ServerConfig serverConfig) {
        if (serverConfig.getProtocol().equals(this.getProtocol())) {
            return true;
        }
        return false;
    }

    @Override
    public void doStart(ServerConfig serverConfig) {
        if (!started) {
            if (serverConfig.isAutoSelectPort()) {
                int availablePort = getAvailablePort(serverConfig.getPort());
                this.port = availablePort;
            } else {
                if (NetUtils.isPortInUse(serverConfig.getPort())) {
                    logger.error("unable to start netty server on port " + serverConfig.getPort()
                            + ", the port is in use");
                    System.exit(0);
                }
                this.port = serverConfig.getPort();
            }
            InetSocketAddress address = null;
            if (this.ip == null) {
                address = new InetSocketAddress(this.port);
            } else {
                address = new InetSocketAddress(this.ip, this.port);
            }
            channel = this.bootstrap.bind(address);
            serverConfig.setActualPort(this.port);
            this.started = true;
        }
    }

    @Override
    public void doStop() {
        if (this.started) {
            // this.channelGroup.close().awaitUninterruptibly();
            // this.bootstrap.releaseExternalResources();
            doClose();
            this.started = false;
        }
    }

    protected void doClose() {
        try {
            if (channelGroup != null) {
                // unbind.
                channelGroup.unbind().awaitUninterruptibly();
                channelGroup.close().awaitUninterruptibly();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        if (channel != null) {
            channel.unbind();
        }
        try {
            if (bootstrap != null) {
                // release external resource.
                bootstrap.releaseExternalResources();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            if (channelGroup != null) {
                channelGroup.clear();
            }
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void destroy() throws Exception {
        this.stop();
    }

    public ChannelGroup getChannelGroup() {
        return channelGroup;
    }

    @Override
    public <T> void doAddService(ProviderConfig<T> providerConfig) {
    }

    @Override
    public <T> void doRemoveService(ProviderConfig<T> providerConfig) {
    }

    @Override
    public String toString() {
        return "netty server-" + this.port;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public String getRegistryUrl(String url) {
        return url;
    }

    public String getRemoteAddress(Channel channel) {
        InetSocketAddress address = (InetSocketAddress) channel.getRemoteAddress();
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }

    @Override
    public List<String> getInvokerMetaInfo() {
        if (channelGroup != null) {
            List<String> results = new ArrayList<String>();
            for (Channel channel : channelGroup) {
                results.add("from:" + getRemoteAddress(channel) + ",to:" + this.getPort());
            }
            return results;
        }
        return null;
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public String getProtocol() {
        return Constants.PROTOCOL_DEFAULT;
    }

}
