package com.adam.vBedPlates.util;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Hypixel Bedwars scoreboard to extract map name and game mode.
 */
public class ScoreboardParser {

	// Patterns to match Bedwars map names in scoreboard
	private static final Pattern MAP_PATTERN = Pattern.compile("Map:\\s*(.+)");
	private static final Pattern MODE_PATTERN = Pattern.compile("Mode:\\s*(.+)");

	// Cache to avoid repeated parsing
	private static String cachedMapName = null;
	private static String cachedGameMode = null;
	private static long lastParseTime = 0;
	private static final long CACHE_DURATION = 5000; // 5 seconds

	/**
	 * Gets the current map name from the scoreboard.
	 * Returns null if not in a Bedwars game or map cannot be determined.
	 */
	public static String getCurrentMapName() {
		long currentTime = System.currentTimeMillis();

		// Use cached value if recent
		if (cachedMapName != null && (currentTime - lastParseTime) < CACHE_DURATION) {
			return cachedMapName;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			cachedMapName = null;
			return null;
		}

		Scoreboard scoreboard = mc.theWorld.getScoreboard();
		ScoreObjective sidebarObjective = scoreboard.getObjectiveInDisplaySlot(1);

		if (sidebarObjective == null) {
			cachedMapName = null;
			return null;
		}

		// Get all scoreboard lines
		Collection<Score> scores = scoreboard.getSortedScores(sidebarObjective);
		List<String> lines = new ArrayList<>();

		for (Score score : scores) {
			ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
			String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
			lines.add(stripColorCodes(line).trim());
		}

		// Look for map name in scoreboard lines
		for (String line : lines) {
			Matcher matcher = MAP_PATTERN.matcher(line);
			if (matcher.find()) {
				cachedMapName = matcher.group(1).trim();
				lastParseTime = currentTime;
				return cachedMapName;
			}

			// Alternative: Check if line contains map name directly
			// Some scoreboard formats show map without "Map:" prefix
			if (line.contains("BEDWARS") || line.contains("BED WARS")) {
				// Next non-empty line might be the map
				continue;
			}
		}

		cachedMapName = null;
		return null;
	}

	/**
	 * Gets the current game mode (e.g., "4v4v4v4", "8v8", etc.)
	 */
	public static String getCurrentGameMode() {
		long currentTime = System.currentTimeMillis();

		if (cachedGameMode != null && (currentTime - lastParseTime) < CACHE_DURATION) {
			return cachedGameMode;
		}

		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) {
			cachedGameMode = null;
			return null;
		}

		Scoreboard scoreboard = mc.theWorld.getScoreboard();
		ScoreObjective sidebarObjective = scoreboard.getObjectiveInDisplaySlot(1);

		if (sidebarObjective == null) {
			cachedGameMode = null;
			return null;
		}

		Collection<Score> scores = scoreboard.getSortedScores(sidebarObjective);

		for (Score score : scores) {
			ScorePlayerTeam team = scoreboard.getPlayersTeam(score.getPlayerName());
			String line = ScorePlayerTeam.formatPlayerName(team, score.getPlayerName());
			String stripped = stripColorCodes(line).trim();

			Matcher matcher = MODE_PATTERN.matcher(stripped);
			if (matcher.find()) {
				cachedGameMode = matcher.group(1).trim();
				lastParseTime = currentTime;
				return cachedGameMode;
			}
		}

		cachedGameMode = null;
		return null;
	}

	/**
	 * Checks if the player is currently in a Bedwars game
	 */
	public static boolean isInBedwarsGame() {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null) return false;

		Scoreboard scoreboard = mc.theWorld.getScoreboard();
		ScoreObjective sidebarObjective = scoreboard.getObjectiveInDisplaySlot(1);

		if (sidebarObjective == null) return false;

		String displayName = sidebarObjective.getDisplayName();
		String stripped = stripColorCodes(displayName).trim().toUpperCase();

		return stripped.contains("BED WARS") || stripped.contains("BEDWARS");
	}

	/**
	 * Strips Minecraft color codes from a string
	 */
	private static String stripColorCodes(String input) {
		if (input == null) return "";
		return input.replaceAll("§[0-9a-fk-or]", "");
	}

	/**
	 * Clears the cache (useful when switching games)
	 */
	public static void clearCache() {
		cachedMapName = null;
		cachedGameMode = null;
		lastParseTime = 0;
	}
}