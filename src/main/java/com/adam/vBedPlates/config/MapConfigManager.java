package com.adam.vBedPlates.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages loading and caching of map configurations from Pinkulu API.
 */
public class MapConfigManager {

	private static final String MAP_DATA_URL = "https://maps.pinkulu.com/trans-rights-are-human-rights.json";
	private static final String CACHE_FILE = "config/vBedPlates/map_cache.json";

	private static MapConfigManager instance;
	private final Map<String, MapConfig> mapCache = new HashMap<>();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
	private boolean isLoaded = false;

	private MapConfigManager() {}

	public static MapConfigManager getInstance() {
		if (instance == null) {
			instance = new MapConfigManager();
		}
		return instance;
	}

	/**
	 * Initializes the manager by loading map data.
	 * First tries local cache, then fetches from API if needed.
	 */
	public void initialize() {
		if (isLoaded) return;

		System.out.println("[vBedPlates] Loading map configurations...");

		// Try loading from cache first
		if (loadFromCache()) {
			System.out.println("[vBedPlates] Loaded " + mapCache.size() + " maps from cache");
			isLoaded = true;

			// Asynchronously update from API in background
			new Thread(this::updateFromAPI).start();
			return;
		}

		// No cache available, fetch from API (blocking)
		if (updateFromAPI()) {
			System.out.println("[vBedPlates] Loaded " + mapCache.size() + " maps from API");
			isLoaded = true;
		} else {
			System.err.println("[vBedPlates] Failed to load map configurations");
		}
	}

	/**
	 * Gets map configuration by name (case-insensitive)
	 */
	public MapConfig getMapConfig(String mapName) {
		if (mapName == null) return null;

		// Normalize map name
		String normalized = mapName.trim().toLowerCase();

		// Direct lookup
		MapConfig config = mapCache.get(normalized);
		if (config != null) return config;

		// Fuzzy match - try to find similar names
		for (Map.Entry<String, MapConfig> entry : mapCache.entrySet()) {
			if (entry.getKey().contains(normalized) || normalized.contains(entry.getKey())) {
				return entry.getValue();
			}
		}

		return null;
	}

	/**
	 * Gets all loaded map configurations
	 */
	public Map<String, MapConfig> getAllConfigs() {
		return new HashMap<>(mapCache);
	}

	/**
	 * Checks if map data is loaded
	 */
	public boolean isLoaded() {
		return isLoaded;
	}

	/**
	 * Loads map data from local cache file
	 */
	private boolean loadFromCache() {
		File cacheFile = new File(Minecraft.getMinecraft().mcDataDir, CACHE_FILE);
		if (!cacheFile.exists()) return false;

		try (FileReader reader = new FileReader(cacheFile)) {
			Type listType = new TypeToken<List<MapConfig>>(){}.getType();
			List<MapConfig> maps = gson.fromJson(reader, listType);

			if (maps != null && !maps.isEmpty()) {
				mapCache.clear();
				for (MapConfig map : maps) {
					if (map.isBedwarsMap()) {
						mapCache.put(map.name.toLowerCase(), map);
					}
				}
				return true;
			}
		} catch (Exception e) {
			System.err.println("[vBedPlates] Error loading cache: " + e.getMessage());
		}

		return false;
	}

	/**
	 * Fetches and caches map data from API
	 */
	private boolean updateFromAPI() {
		try {
			System.out.println("[vBedPlates] Fetching map data from API...");

			URL url = new URL(MAP_DATA_URL);
			StringBuilder response = new StringBuilder();

			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					response.append(line);
				}
			}

			// Parse JSON
			Type listType = new TypeToken<List<MapConfig>>(){}.getType();
			List<MapConfig> maps = gson.fromJson(response.toString(), listType);

			if (maps != null && !maps.isEmpty()) {
				mapCache.clear();
				for (MapConfig map : maps) {
					if (map.isBedwarsMap()) {
						mapCache.put(map.name.toLowerCase(), map);
					}
				}

				// Save to cache
				saveToCache(maps);
				return true;
			}

		} catch (Exception e) {
			System.err.println("[vBedPlates] Error fetching from API: " + e.getMessage());
		}

		return false;
	}

	/**
	 * Saves map data to local cache file
	 */
	private void saveToCache(List<MapConfig> maps) {
		try {
			File cacheFile = new File(Minecraft.getMinecraft().mcDataDir, CACHE_FILE);
			cacheFile.getParentFile().mkdirs();

			try (FileWriter writer = new FileWriter(cacheFile)) {
				gson.toJson(maps, writer);
			}

			System.out.println("[vBedPlates] Saved map data to cache");
		} catch (Exception e) {
			System.err.println("[vBedPlates] Error saving cache: " + e.getMessage());
		}
	}

	/**
	 * Forces a refresh from the API
	 */
	public void refresh() {
		new Thread(() -> {
			if (updateFromAPI()) {
				System.out.println("[vBedPlates] Map data refreshed successfully");
			}
		}).start();
	}
}
