package com.adam.vBedPlates.util;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Detects bed team colors by analyzing surrounding wool/terracotta blocks.
 * In 1.8.9, beds themselves don't have color data, but teams use colored
 * blocks to protect them, which we can use to infer team affiliation.
 */
public class TeamDetector {

	public enum BedTeam {
		RED(14, "§c", "Red"),
		BLUE(11, "§9", "Blue"),
		GREEN(13, "§a", "Green"),
		YELLOW(4, "§e", "Yellow"),
		AQUA(9, "§b", "Aqua"),
		WHITE(0, "§f", "White"),
		PINK(6, "§d", "Pink"),
		GRAY(7, "§7", "Gray"),
		UNKNOWN(-1, "§7", "Unknown");

		public final int woolMeta;
		public final String colorCode;
		public final String displayName;

		BedTeam(int woolMeta, String colorCode, String displayName) {
			this.woolMeta = woolMeta;
			this.colorCode = colorCode;
			this.displayName = displayName;
		}

		public static BedTeam fromWoolMeta(int meta) {
			for (BedTeam team : values()) {
				if (team.woolMeta == meta) return team;
			}
			return UNKNOWN;
		}
	}

	private static final int SCAN_RADIUS = 8; // Area around bed to check for team blocks

	/**
	 * Detects the team of a bed by analyzing surrounding colored blocks.
	 *
	 * @param world The world
	 * @param bedPos The bed position (foot block)
	 * @return The detected team
	 */
	public static BedTeam detectBedTeam(World world, BlockPos bedPos) {
		Map<Integer, Integer> colorCounts = new HashMap<>();

		// Scan area around the bed
		for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++) {
			for (int y = -2; y <= 4; y++) { // Focus on bed level and above
				for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
					BlockPos checkPos = bedPos.add(x, y, z);
					Block block = world.getBlockState(checkPos).getBlock();

					// Check for colored blocks (wool and stained glass are most common)
					if (block == Blocks.wool || block == Blocks.stained_glass ||
							block == Blocks.stained_hardened_clay) {

						int meta = world.getBlockState(checkPos).getBlock()
								.getMetaFromState(world.getBlockState(checkPos));

						// Increment count for this color
						colorCounts.put(meta, colorCounts.getOrDefault(meta, 0) + 1);
					}
				}
			}
		}

		// Find the most common color (excluding white/gray as they're generic)
		int maxCount = 0;
		int dominantColor = -1;

		for (Map.Entry<Integer, Integer> entry : colorCounts.entrySet()) {
			int color = entry.getKey();
			int count = entry.getValue();

			// Prioritize team colors (not white/gray)
			if (color != 0 && color != 7 && color != 8 && count > maxCount) {
				maxCount = count;
				dominantColor = color;
			}
		}

		// Need at least 3 blocks of a color to be confident
		if (maxCount >= 3) {
			return BedTeam.fromWoolMeta(dominantColor);
		}

		// Fallback: check white/gray if no other color found
		for (Map.Entry<Integer, Integer> entry : colorCounts.entrySet()) {
			if (entry.getValue() > maxCount) {
				return BedTeam.fromWoolMeta(entry.getKey());
			}
		}

		return BedTeam.UNKNOWN;
	}

	/**
	 * Gets a display-friendly team indicator
	 */
	public static String getTeamIndicator(BedTeam team) {
		if (team == BedTeam.UNKNOWN) return "";
		return team.colorCode + "■ " + team.displayName + " §f";
	}

	/**
	 * Gets just the colored square for compact display
	 */
	public static String getTeamIcon(BedTeam team) {
		if (team == BedTeam.UNKNOWN) return "§7■";
		return team.colorCode + "■";
	}
}