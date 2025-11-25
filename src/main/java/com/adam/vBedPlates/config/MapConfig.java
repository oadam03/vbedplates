package com.adam.vBedPlates.config;

import com.google.gson.annotations.SerializedName;

/**
 * Represents map configuration data from the Pinkulu API.
 * Corresponds to the JSON structure for Hypixel Bedwars maps.
 */
public class MapConfig {

	@SerializedName("gameType")
	public String gameType;

	@SerializedName("pool")
	public String pool;

	@SerializedName("name")
	public String name;

	@SerializedName("festival")
	public String festival;

	@SerializedName("minBuild")
	public int minBuild;

	@SerializedName("maxBuild")
	public int maxBuild;

	@SerializedName("buildRadius")
	public int buildRadius;

	@SerializedName("reskinOf")
	public String reskinOf;

	/**
	 * Checks if this is a Bedwars map
	 */
	public boolean isBedwarsMap() {
		return "BEDWARS".equalsIgnoreCase(gameType);
	}

	/**
	 * Gets the number of teams based on pool
	 * e.g., "BEDWARS_4TEAMS_FAST" -> 4
	 */
	public int getTeamCount() {
		if (pool == null) return 4; // Default assumption

		if (pool.contains("4TEAMS")) return 4;
		if (pool.contains("8TEAMS")) return 8;
		if (pool.contains("2TEAMS")) return 2; // Doubles

		return 4; // Default
	}

	/**
	 * Checks if this is a fast mode map
	 */
	public boolean isFastMode() {
		return pool != null && pool.contains("FAST");
	}

	/**
	 * Checks if this is a slow mode map
	 */
	public boolean isSlowMode() {
		return pool != null && pool.contains("SLOW");
	}

	/**
	 * Gets scan parameters for bed detection.
	 * Returns optimal scan area based on map configuration.
	 */
	public ScanParameters getScanParameters() {
		// Calculate scan area from build limits
		int scanRadiusXZ = (buildRadius > 0) ? (buildRadius + 10) : 40; // Add buffer
		int heightUp = Math.max(maxBuild - 64, 15); // From typical spawn Y
		int heightDown = Math.max(64 - minBuild, 5);

		return new ScanParameters(scanRadiusXZ, heightUp, heightDown);
	}

	@Override
	public String toString() {
		return String.format("MapConfig{name='%s', pool='%s', minBuild=%d, maxBuild=%d, buildRadius=%d}",
				name, pool, minBuild, maxBuild, buildRadius);
	}

	/**
	 * Scan parameters for bed detection
	 */
	public static class ScanParameters {
		public final int radiusXZ;
		public final int heightUp;
		public final int heightDown;

		public ScanParameters(int radiusXZ, int heightUp, int heightDown) {
			this.radiusXZ = radiusXZ;
			this.heightUp = heightUp;
			this.heightDown = heightDown;
		}

		@Override
		public String toString() {
			return String.format("ScanParams{XZ=%d, Up=%d, Down=%d}", radiusXZ, heightUp, heightDown);
		}
	}
}
