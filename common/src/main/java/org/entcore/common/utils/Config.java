package org.entcore.common.utils;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class Config {
	private static final long NINETY_DAYS = 90 * 24 * 3600 * 1000L;
	public static final long defaultDeleteUserDelay = NINETY_DAYS;
	public static final long defaultPreDeleteUserDelay = NINETY_DAYS;

	private final Map<String, JsonObject> moduleConfigs = new HashMap<>();
	private JsonObject globalConfig;

	private Config() {}

	private static class ConfigHolder {
		private static final Config instance = new Config();
	}

	public static Config getInstance() {
		return ConfigHolder.instance;
	}

	// Gestion de la configuration par module
	public JsonObject getModuleConfig(String moduleName) {
		return moduleConfigs.getOrDefault(moduleName, new JsonObject());
	}

	public void setModuleConfig(String moduleName, JsonObject config) {
		moduleConfigs.put(moduleName, config);
	}

	public boolean hasModuleConfig(String moduleName) {
		return moduleConfigs.containsKey(moduleName);
	}
}
