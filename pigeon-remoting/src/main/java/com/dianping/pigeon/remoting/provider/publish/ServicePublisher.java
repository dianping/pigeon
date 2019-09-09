/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.provider.publish;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;

import com.dianping.pigeon.config.ConfigManager;
import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.log.Logger;
import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.registry.RegistryManager;
import com.dianping.pigeon.registry.exception.RegistryException;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.provider.ProviderBootStrap;
import com.dianping.pigeon.remoting.provider.Server;
import com.dianping.pigeon.remoting.provider.config.ProviderConfig;
import com.dianping.pigeon.remoting.provider.listener.HeartBeatListener;
import com.dianping.pigeon.remoting.provider.service.DisposableService;
import com.dianping.pigeon.remoting.provider.service.InitializingService;
import com.dianping.pigeon.remoting.provider.service.method.ServiceMethodFactory;
import com.dianping.pigeon.util.VersionUtils;

/**
 * @author xiangwu
 * @Sep 30, 2013
 */
public final class ServicePublisher {

	private static Logger logger = LoggerLoader.getLogger(ServicePublisher.class);

	private static ConcurrentHashMap<String, ProviderConfig<?>> serviceCache = new ConcurrentHashMap<String, ProviderConfig<?>>();

	private static ConfigManager configManager = ConfigManagerLoader.getConfigManager();

	private static ServiceChangeListener serviceChangeListener = new DefaultServiceChangeListener();

	private static boolean DEFAULT_HEARTBEAT_ENABLE = true;

	private static ConcurrentHashMap<String, Integer> serverWeightCache = new ConcurrentHashMap<String, Integer>();

	private static final int UNPUBLISH_WAITTIME = configManager.getIntValue(Constants.KEY_UNPUBLISH_WAITTIME,
			Constants.DEFAULT_UNPUBLISH_WAITTIME);

	private static final boolean THROW_EXCEPTION_IF_FORBIDDEN = configManager.getBooleanValue(
			"pigeon.publish.forbidden.throwexception", false);

	private static final boolean GROUP_FORBIDDEN = configManager.getBooleanValue("pigeon.publish.forbidden.group",
			false);

	private static final String registryBlackList = configManager.getStringValue("pigeon.registry.blacklist", "");

	private static final String registryWhiteList = configManager.getStringValue("pigeon.registry.whitelist", "");

	private static final boolean canRegisterDefault = configManager.getBooleanValue(
			"pigeon.registry.canregister.default", true);

	public static String getServiceUrlWithVersion(String url, String version) {
		String newUrl = url;
		if (!StringUtils.isBlank(version)) {
			newUrl = url + "_" + version;
		}
		return newUrl;
	}

	public static <T> void addService(ProviderConfig<T> providerConfig) throws Exception {
		if (logger.isInfoEnabled()) {
			logger.info("add service:" + providerConfig);
		}
		String version = providerConfig.getVersion();
		String url = providerConfig.getUrl();
		if (StringUtils.isBlank(version)) {// default version
			serviceCache.put(url, providerConfig);
		} else {
			String urlWithVersion = getServiceUrlWithVersion(url, version);
			if (serviceCache.containsKey(url)) {
				serviceCache.put(urlWithVersion, providerConfig);
				ProviderConfig<?> providerConfigDefault = serviceCache.get(url);
				String defaultVersion = providerConfigDefault.getVersion();
				if (!StringUtils.isBlank(defaultVersion)) {
					if (VersionUtils.compareVersion(defaultVersion, providerConfig.getVersion()) < 0) {
						// replace existing service with this newer service as
						// the default provider
						serviceCache.put(url, providerConfig);
					}
				}
			} else {
				serviceCache.put(urlWithVersion, providerConfig);
				// use this service as the default provider
				serviceCache.put(url, providerConfig);
			}
		}
		T service = providerConfig.getService();
		if (service instanceof InitializingService) {
			((InitializingService) service).initialize();
		}
		ServiceMethodFactory.init(url);
	}

	public static <T> void publishService(ProviderConfig<T> providerConfig) throws RegistryException {
		publishService(providerConfig, true);
	}

	// atom
	public static <T> void publishService(ProviderConfig<T> providerConfig, boolean forcePublish)
			throws RegistryException {
		String url = providerConfig.getUrl();
		boolean existingService = false;
		for (ProviderConfig<?> value : serviceCache.values()) {
			if (StringUtils.equals(value.getUrl(),url)){
				existingService = true;
				break;
			}
		} 
		if (logger.isInfoEnabled()) {
			logger.info("try to publish service to registry:" + providerConfig + ", existing service:"
					+ existingService);
		}
		if (existingService) {
			boolean autoPublishEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
					Constants.KEY_AUTOPUBLISH_ENABLE, true);
			if (autoPublishEnable || forcePublish) {
				List<Server> servers = ProviderBootStrap.getServers(providerConfig);
				int registerCount = 0;
				for (Server server : servers) {
					publishServiceToRegistry(url, server.getRegistryUrl(url), server.getPort(),
							RegistryManager.getInstance().getGroup(url), providerConfig.isSupported());
					registerCount++;
				}
				if (registerCount > 0) {
					boolean isHeartbeatEnable = configManager.getBooleanValue(Constants.KEY_HEARTBEAT_ENABLE,
							DEFAULT_HEARTBEAT_ENABLE);
					if (isHeartbeatEnable) {
						HeartBeatListener.registerHeartBeat(providerConfig);
					}

					boolean isNotify = configManager
							.getBooleanValue(Constants.KEY_NOTIFY_ENABLE, false);
					if (isNotify && serviceChangeListener != null) {
						serviceChangeListener.notifyServicePublished(providerConfig);
					}

					boolean autoRegisterEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
							Constants.KEY_AUTOREGISTER_ENABLE, true);
					if (autoRegisterEnable) {
						ServiceOnlineTask.start();
					} else {
						logger.info("auto register is disabled");
					}

					providerConfig.setPublished(true);
				}
			} else {
				logger.info("auto publish is disabled");
			}
		}
	}

	public static boolean isAutoPublish() {
		boolean autoPublishEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
				Constants.KEY_AUTOPUBLISH_ENABLE, true);
		boolean autoRegisterEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
				Constants.KEY_AUTOREGISTER_ENABLE, true);
		return autoPublishEnable && autoRegisterEnable;
	}

	public static void publishService(String url) throws RegistryException {
		if (logger.isInfoEnabled()) {
			logger.info("publish service:" + url);
		}
		ProviderConfig<?> providerConfig = serviceCache.get(url);
		if (providerConfig != null) {
			for (String key : serviceCache.keySet()) {
				ProviderConfig<?> pc = serviceCache.get(key);
				if (pc.getUrl().equals(url)) {
					publishService(pc, true);
				}
			}
		}
	}

	private synchronized static <T> void publishServiceToRegistry(String url, String registryUrl, int port, String group, boolean support)
			throws RegistryException {
		String ip = configManager.getLocalIp();
		if (!canRegister(ip)) {
			boolean canRegister = false;
			if (StringUtils.isNotBlank(group) && !GROUP_FORBIDDEN) {
				canRegister = true;
			}
			if (!canRegister) {
				if (THROW_EXCEPTION_IF_FORBIDDEN) {
					throw new SecurityException("service registration of " + ip + " is not allowed!");
				} else {
					logger.warn("service registration of " + ip + " is not allowed, url:" + registryUrl + ", port:"
							+ port + ", group:" + group);
					return;
				}
			}
		}
		String serverAddress = ip + ":" + port;
		int weight = Constants.WEIGHT_INITIAL;
		boolean autoRegisterEnable = ConfigManagerLoader.getConfigManager().getBooleanValue(
				Constants.KEY_AUTOREGISTER_ENABLE, true);
		if (!autoRegisterEnable) {
			weight = 0;
		}
		/*boolean enableOnlineTask = ConfigManagerLoader.getConfigManager().getBooleanValue("pigeon.online.task.enable",
				true);
		if (!enableOnlineTask) {
			weight = Constants.WEIGHT_DEFAULT;
		}*/
		if (serverWeightCache.containsKey(serverAddress)) {
			weight = -1;
		}
		if (logger.isInfoEnabled()) {
			logger.info("publish service to registry, url:" + registryUrl + ", port:" + port + ", group:" + group
					+ ", address:" + serverAddress + ", weight:" + weight + ", support: " + support);
		}
		RegistryManager.getInstance().registerService(registryUrl, group, serverAddress, weight);
		RegistryManager.getInstance().registerSupportNewProtocol(serverAddress, registryUrl, support);

		if (weight >= 0) {
			if (!serverWeightCache.containsKey(serverAddress)) {
				RegistryManager.getInstance().setServerApp(serverAddress, configManager.getAppName());
				RegistryManager.getInstance().setServerVersion(serverAddress, VersionUtils.VERSION);
			}
			serverWeightCache.put(serverAddress, weight);
		}
	}

	public static Map<String, Integer> getServerWeight() {
		return serverWeightCache;
	}

	public synchronized static void setServerWeight(int weight) throws RegistryException {
		if (weight < 0 || weight > 100) {
			throw new IllegalArgumentException("The weight must be within the range of 0 to 100:" + weight);
		}
		for (String serverAddress : serverWeightCache.keySet()) {
			if (logger.isInfoEnabled()) {
				logger.info("set weight, address:" + serverAddress + ", weight:" + weight);
			}
			RegistryManager.getInstance().setServerWeight(serverAddress, weight);
			if (!serverWeightCache.containsKey(serverAddress)) {
				RegistryManager.getInstance().setServerApp(serverAddress, configManager.getAppName());
				RegistryManager.getInstance().setServerVersion(serverAddress, VersionUtils.VERSION);
			}
			serverWeightCache.put(serverAddress, weight);
		}
	}

	public synchronized static <T> void unpublishService(ProviderConfig<T> providerConfig) throws RegistryException {
		String url = providerConfig.getUrl();
		boolean existingService = false;
		for (String key : serviceCache.keySet()) {
			ProviderConfig<?> pc = serviceCache.get(key);
			if (pc.getUrl().equals(url)) {
				existingService = true;
				break;
			}
		}
		if (logger.isInfoEnabled()) {
			logger.info("try to unpublish service from registry:" + providerConfig + ", existing service:"
					+ existingService);
		}
		if (existingService) {
			List<Server> servers = ProviderBootStrap.getServers(providerConfig);
			for (Server server : servers) {
				String serverAddress = configManager.getLocalIp() + ":" + server.getPort();
				String registryUrl = server.getRegistryUrl(providerConfig.getUrl());
				RegistryManager.getInstance().unregisterService(registryUrl,
						RegistryManager.getInstance().getGroup(url), serverAddress);
				// unregister protocol, include http server
				RegistryManager.getInstance().unregisterSupportNewProtocol(serverAddress, registryUrl,
						providerConfig.isSupported());

				Integer weight = serverWeightCache.remove(serverAddress);
				if (weight != null) {
					RegistryManager.getInstance().unregisterServerApp(serverAddress);
					RegistryManager.getInstance().unregisterServerVersion(serverAddress);
				}
			}

			boolean isHeartbeatEnable = configManager.getBooleanValue(Constants.KEY_HEARTBEAT_ENABLE,
					DEFAULT_HEARTBEAT_ENABLE);
			if (isHeartbeatEnable) {
				HeartBeatListener.unregisterHeartBeat(providerConfig);
			}

			boolean isNotify = configManager.getBooleanValue(Constants.KEY_NOTIFY_ENABLE, false);
			if (isNotify && serviceChangeListener != null) {
				serviceChangeListener.notifyServiceUnpublished(providerConfig);
			}
			providerConfig.setPublished(false);
			if (logger.isInfoEnabled()) {
				logger.info("unpublished service from registry:" + providerConfig);
			}
		}
	}

	public static void unpublishService(String url) throws RegistryException {
		if (logger.isInfoEnabled()) {
			logger.info("unpublish service:" + url);
		}
		ProviderConfig<?> providerConfig = serviceCache.get(url);
		if (providerConfig != null) {
			for (String key : serviceCache.keySet()) {
				ProviderConfig<?> pc = serviceCache.get(key);
				if (pc.getUrl().equals(url)) {
					unpublishService(pc);
				}
			}
		}
	}

	public static ProviderConfig<?> getServiceConfig(String url) {
		ProviderConfig<?> providerConfig = serviceCache.get(url);
		return providerConfig;
	}

	public static void removeService(String url) throws RegistryException {
		if (logger.isInfoEnabled()) {
			logger.info("remove service:" + url);
		}
		List<String> toRemovedUrls = new ArrayList<String>();
		for (String key : serviceCache.keySet()) {
			ProviderConfig<?> pc = serviceCache.get(key);
			if (pc.getUrl().equals(url)) {
				unpublishService(pc);
				toRemovedUrls.add(key);
				Object service = pc.getService();
				if (service instanceof DisposableService) {
					try {
						((DisposableService) service).destroy();
					} catch (Throwable e) {
						logger.warn("error while destroy service:" + url + ", caused by " + e.getMessage());
					}
				}
			}
		}
		for (String key : toRemovedUrls) {
			serviceCache.remove(key);
		}
	}

	public static void removeAllServices() throws RegistryException {
		if (logger.isInfoEnabled()) {
			logger.info("remove all services");
		}
		unpublishAllServices();
		serviceCache.clear();
	}

	public static void unpublishAllServices() throws RegistryException {
		if (logger.isInfoEnabled()) {
			logger.info("unpublish all services");
		}
		ServiceOnlineTask.stop();
		setServerWeight(0);
		try {
			Thread.sleep(UNPUBLISH_WAITTIME);
		} catch (InterruptedException e) {
		}
		for (String url : serviceCache.keySet()) {
			ProviderConfig<?> providerConfig = serviceCache.get(url);
			if (providerConfig != null) {
				unpublishService(providerConfig);
			}
		}
	}

	public static void publishAllServices() throws RegistryException {
		publishAllServices(true);
	}

	public static void publishAllServices(boolean forcePublish) throws RegistryException {
		if (logger.isInfoEnabled()) {
			logger.info("publish all services, " + forcePublish);
		}
		for (String url : serviceCache.keySet()) {
			ProviderConfig<?> providerConfig = serviceCache.get(url);
			if (providerConfig != null) {
				publishService(providerConfig, forcePublish);
			}
		}
	}

	public static Map<String, ProviderConfig<?>> getAllServiceProviders() {
		return serviceCache;
	}

	public static void notifyServiceOnline() {
		for (String url : serviceCache.keySet()) {
			ProviderConfig<?> providerConfig = serviceCache.get(url);
			if (providerConfig != null) {
				// do notify
				if (serviceChangeListener != null) {
					serviceChangeListener.notifyServiceOnline(providerConfig);
				}
			}
		}
	}

	public static void notifyServiceOffline() {
		for (String url : serviceCache.keySet()) {
			ProviderConfig<?> providerConfig = serviceCache.get(url);
			if (providerConfig != null) {
				// do notify
				if (serviceChangeListener != null) {
					serviceChangeListener.notifyServiceOffline(providerConfig);
				}
			}
		}
	}

	public static boolean canRegister(String ip) {
		String[] whiteArray = registryWhiteList.split(",");
		for (String addr : whiteArray) {
			if (StringUtils.isBlank(addr)) {
				continue;
			}
			if (ip.startsWith(addr)) {
				return true;
			}
		}
		String[] blackArray = registryBlackList.split(",");
		for (String addr : blackArray) {
			if (StringUtils.isBlank(addr)) {
				continue;
			}
			if (ip.startsWith(addr)) {
				return false;
			}
		}
		return canRegisterDefault;
	}

    public static Class<?> getInterface(String url) {
        ProviderConfig<?> config = getServiceConfig(url);
        return config.getServiceInterface();
    }

}
