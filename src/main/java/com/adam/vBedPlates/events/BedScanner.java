package com.adam.vBedPlates.events;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;

import net.minecraft.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.adam.vBedPlates.commands.BedplateCommand;
import com.adam.vBedPlates.config.MapConfig;
import com.adam.vBedPlates.config.MapConfigManager;
import com.adam.vBedPlates.util.ScoreboardParser;
import com.adam.vBedPlates.util.TeamDetector;
import com.adam.vBedPlates.util.TeamDetector.BedTeam;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Enhanced bed scanner with map-aware scanning and team detection.
 */
public class BedScanner {
	private final Set<BlockPos> knownBeds = new HashSet<>();
	private final Set<Block> defenseBlocks = new HashSet<>();
	private final Set<BlockPos> obsidianBlocks = new HashSet<>();
	private final Set<BlockPos> fullyEncasedBeds = new HashSet<>();
	private final Map<BlockPos, Block> lastKnownBlocks = new HashMap<>();
	public static List<BedData> trackedBeds = new ArrayList<>();

	// Dynamic scan range based on map config
	private int currentRadiusXZ = 30;
	private int currentHeightUp = 15;
	private int currentHeightDown = 5;
	private static final int OBSIDIAN_SCAN_RADIUS = 5;

	// Map tracking
	private String currentMapName = null;
	private MapConfig currentMapConfig = null;
	private boolean hasWarnedNoConfig = false;

	private int tickCounter = 0;

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) return;

		World world = mc.theWorld;
		BlockPos playerPos = mc.thePlayer.getPosition();

		// Check for map changes every 2 seconds
		if (world.getTotalWorldTime() % 40 == 0) {
			updateMapConfiguration();
		}

		// Perform bed scanning every second (using dynamic scan range)
		if (world.getTotalWorldTime() % 20 == 0) {
			scanForBeds(world, playerPos);
			updateDefenseBlocks(world);
			rebuildObsidianHighlights(world);
		}

		// Detect nearby block changes every 0.5 seconds
		if (tickCounter++ >= 10) {
			detectNearbyBlockChanges(world);
			tickCounter = 0;
		}
	}

	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) return;

		// Clear map cache on world change
		currentMapName = null;
		currentMapConfig = null;
		hasWarnedNoConfig = false;
		ScoreboardParser.clearCache();

		World world = mc.theWorld;
		BlockPos playerPos = mc.thePlayer.getPosition();

		sendChat("§7World loaded — checking for Bedwars game...");

		// Initialize map config manager if not already done
		if (!MapConfigManager.getInstance().isLoaded()) {
			MapConfigManager.getInstance().initialize();
		}

		updateMapConfiguration();
		scanForBeds(world, playerPos);
		updateDefenseBlocks(world);
	}

	/**
	 * Updates map configuration based on scoreboard data.
	 * Adjusts scan parameters accordingly.
	 */
	private void updateMapConfiguration() {
		if (!ScoreboardParser.isInBedwarsGame()) {
			// Not in Bedwars, use default scanning
			if (currentMapName != null) {
				sendChat("§7Left Bedwars game — reverting to default scanning");
				currentMapName = null;
				currentMapConfig = null;
				resetToDefaultScanParameters();
			}
			return;
		}

		String mapName = ScoreboardParser.getCurrentMapName();

		// Map hasn't changed
		if (mapName == null || mapName.equals(currentMapName)) {
			return;
		}

		currentMapName = mapName;
		currentMapConfig = MapConfigManager.getInstance().getMapConfig(mapName);

		if (currentMapConfig != null) {
			// Update scan parameters based on map config
			MapConfig.ScanParameters params = currentMapConfig.getScanParameters();
			currentRadiusXZ = params.radiusXZ;
			currentHeightUp = params.heightUp;
			currentHeightDown = params.heightDown;

			sendChat("§aMap detected: §f" + currentMapConfig.name +
					" §7(" + currentMapConfig.getTeamCount() + " teams, " +
					(currentMapConfig.isFastMode() ? "Fast" : "Slow") + ")");
			sendChat("§7Scan area: §f" + currentRadiusXZ + "x" + currentHeightUp + "x" + currentHeightDown);

			hasWarnedNoConfig = false;

			// Clear and rescan with new parameters
			clearAllTracking();
			scanForBeds(Minecraft.getMinecraft().theWorld,
					Minecraft.getMinecraft().thePlayer.getPosition());
		} else {
			// Unknown map - use default scanning
			if (!hasWarnedNoConfig) {
				sendChat("§eMap '§f" + mapName + "§e' not found in database");
				sendChat("§7Using default scan parameters");
				hasWarnedNoConfig = true;
			}
			resetToDefaultScanParameters();
		}
	}

	/**
	 * Resets scan parameters to defaults (for unknown maps or non-Bedwars)
	 */
	private void resetToDefaultScanParameters() {
		currentRadiusXZ = 30;
		currentHeightUp = 15;
		currentHeightDown = 5;
	}

	/**
	 * Clears all tracking data
	 */
	private void clearAllTracking() {
		knownBeds.clear();
		trackedBeds.clear();
		defenseBlocks.clear();
		obsidianBlocks.clear();
		fullyEncasedBeds.clear();
		lastKnownBlocks.clear();
	}

	private void detectNearbyBlockChanges(World world) {
		Set<BlockPos> affectedBeds = new HashSet<>();
		Set<BlockPos> newObsidian = new HashSet<>();

		for (BlockPos bed : new HashSet<>(knownBeds)) {
			if (world.getBlockState(bed).getBlock() != Blocks.bed) {
				continue;
			}

			BlockPos min = bed.add(-2, 0, -2);
			BlockPos max = bed.add(2, 4, 2);

			for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
				Block current = world.getBlockState(pos).getBlock();
				Block previous = lastKnownBlocks.get(pos);

				lastKnownBlocks.put(pos, current);

				if (current == Blocks.obsidian) {
					newObsidian.add(pos);
				}

				if (previous == null || previous != current) {
					if (current == Blocks.obsidian || previous == Blocks.obsidian) {
						affectedBeds.add(bed);
					}
				}
			}
		}

		obsidianBlocks.clear();
		obsidianBlocks.addAll(newObsidian);

		for (BlockPos bed : affectedBeds) {
			checkFullBedEncasement(world, bed);
		}
	}

	private void scanForBeds(World world, BlockPos center) {
		// Validate destroyed beds
		List<BedData> toRemoveData = new ArrayList<>();
		Set<BlockPos> toRemoveBeds = new HashSet<>();

		for (BedData bedData : trackedBeds) {
			Block block = world.getBlockState(bedData.pos).getBlock();
			if (block != Blocks.bed) {
				toRemoveData.add(bedData);
				toRemoveBeds.add(bedData.pos);

				BlockPos min = bedData.pos.add(-OBSIDIAN_SCAN_RADIUS, 0, -OBSIDIAN_SCAN_RADIUS);
				BlockPos max = bedData.pos.add(OBSIDIAN_SCAN_RADIUS, OBSIDIAN_SCAN_RADIUS, OBSIDIAN_SCAN_RADIUS);
				for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
					obsidianBlocks.remove(pos);
					lastKnownBlocks.remove(pos);
				}
			}
		}

		trackedBeds.removeAll(toRemoveData);
		knownBeds.removeAll(toRemoveBeds);
		defenseBlocks.clear();
		fullyEncasedBeds.removeAll(toRemoveBeds);

		// Scan for new beds using dynamic scan range
		for (int x = -currentRadiusXZ; x <= currentRadiusXZ; x++) {
			for (int y = -currentHeightDown; y <= currentHeightUp; y++) {
				for (int z = -currentRadiusXZ; z <= currentRadiusXZ; z++) {
					BlockPos checkPos = center.add(x, y, z);
					Block block = world.getBlockState(checkPos).getBlock();

					if (block == Blocks.bed) {
						int meta = block.getMetaFromState(world.getBlockState(checkPos));
						boolean isHead = (meta & 8) != 0;

						if (!isHead && !knownBeds.contains(checkPos)) {
							knownBeds.add(checkPos);
							BlockPos headPos = getOtherHalf(world, checkPos);
							if (headPos != null) {
								knownBeds.add(headPos);
							}

							// Detect team and create bed data
							BedTeam team = TeamDetector.detectBedTeam(world, checkPos);
							BedData bedData = new BedData(checkPos, team);
							trackedBeds.add(bedData);

							String teamInfo = (team != BedTeam.UNKNOWN)
									? " " + TeamDetector.getTeamIcon(team)
									: "";
							//sendChat("§aFound bed" + teamInfo + " §7at §f" + checkPos.getX() + " " + checkPos.getY() + " " + checkPos.getZ());

							// Cache nearby blocks
							BlockPos min = checkPos.add(-4, 0, -4);
							BlockPos max = checkPos.add(4, 4, 4);
							for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
								Block nearby = world.getBlockState(pos).getBlock();
								lastKnownBlocks.put(pos, nearby);
							}

							scanSurroundings(world, checkPos, OBSIDIAN_SCAN_RADIUS);
							checkFullBedEncasement(world, checkPos);
						}
					}
				}
			}
		}
	}

	private void updateDefenseBlocks(World world) {
		for (BedData bedData : trackedBeds) {
			Set<Block> bedDefTypes = new HashSet<>();
			for (int x = -OBSIDIAN_SCAN_RADIUS; x <= OBSIDIAN_SCAN_RADIUS; x++) {
				for (int y = 0; y <= OBSIDIAN_SCAN_RADIUS; y++) {
					for (int z = -OBSIDIAN_SCAN_RADIUS; z <= OBSIDIAN_SCAN_RADIUS; z++) {
						BlockPos pos = bedData.pos.add(x, y, z);
						Block block = world.getBlockState(pos).getBlock();
						if (isDefenseBlock(block)) {
							bedDefTypes.add(block);
						}
					}
				}
			}
			bedData.defenseBlocks = bedDefTypes;
		}

		defenseBlocks.clear();
		for (BedData bedData : trackedBeds) {
			defenseBlocks.addAll(bedData.defenseBlocks);
		}
	}

	private void sendChat(String msg) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.thePlayer != null) {
			mc.thePlayer.addChatMessage(new ChatComponentText("[§avBedplate§f] " + msg));
		}
	}

	private void scanSurroundings(World world, BlockPos bedPos, int radius) {
		for (int x = -radius; x <= radius; x++) {
			for (int y = 0; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos checkPos = bedPos.add(x, y, z);
					Block nearbyBlock = world.getBlockState(checkPos).getBlock();

					if (nearbyBlock == Blocks.obsidian) {
						if (!obsidianBlocks.contains(checkPos)) {
							obsidianBlocks.add(checkPos);
						}
					}
				}
			}
		}
	}

	private void rebuildObsidianHighlights(World world) {
		obsidianBlocks.clear();

		for (BedData bedData : trackedBeds) {
			for (int x = -OBSIDIAN_SCAN_RADIUS; x <= OBSIDIAN_SCAN_RADIUS; x++) {
				for (int y = 0; y <= OBSIDIAN_SCAN_RADIUS; y++) {
					for (int z = -OBSIDIAN_SCAN_RADIUS; z <= OBSIDIAN_SCAN_RADIUS; z++) {
						BlockPos checkPos = bedData.pos.add(x, y, z);
						Block block = world.getBlockState(checkPos).getBlock();
						if (block == Blocks.obsidian) {
							obsidianBlocks.add(checkPos);
						}
					}
				}
			}
		}
	}

	public static boolean isDefenseBlock(Block block) {
		return block == Blocks.end_stone ||
				block == Blocks.obsidian ||
				block == Blocks.glass ||
				block == Blocks.stained_glass ||
				block == Blocks.wool ||
				block == Blocks.ladder ||
				block == Blocks.planks ||
				block == Blocks.log ||
				block == Blocks.log2 ||
				block == Blocks.packed_ice;
	}

	private BlockPos getOtherHalf(World world, BlockPos bedPos) {
		Block bedBlock = world.getBlockState(bedPos).getBlock();
		if (bedBlock != Blocks.bed) return null;

		int meta = world.getBlockState(bedPos).getBlock().getMetaFromState(world.getBlockState(bedPos));
		int direction = meta & 3;

		switch (direction) {
			case 0: return bedPos.south();
			case 1: return bedPos.west();
			case 2: return bedPos.north();
			case 3: return bedPos.east();
			default: return null;
		}
	}

	private boolean isBedHalfEncased(World world, BlockPos pos) {
		Block block = world.getBlockState(pos).getBlock();
		if (block != Blocks.bed) return false;

		int meta = block.getMetaFromState(world.getBlockState(pos));
		int dir = meta & 3;
		boolean isHead = (meta & 8) != 0;

		BlockPos otherHalfPos;
		switch (dir) {
			case 0:  otherHalfPos = isHead ? pos.north() : pos.south(); break;
			case 1:  otherHalfPos = isHead ? pos.east()  : pos.west();  break;
			case 2:  otherHalfPos = isHead ? pos.south() : pos.north(); break;
			case 3:  otherHalfPos = isHead ? pos.west()  : pos.east();  break;
			default: otherHalfPos = null;
		}

		BlockPos[] sides = {
				pos.north(),
				pos.south(),
				pos.east(),
				pos.west(),
				pos.up()
		};

		for (BlockPos side : sides) {
			if (otherHalfPos != null && side.equals(otherHalfPos)) continue;

			Block neighbor = world.getBlockState(side).getBlock();
			if (neighbor != Blocks.obsidian) {
				return false;
			}
		}
		return true;
	}

	private void checkFullBedEncasement(World world, BlockPos bedPos) {
		BlockPos otherHalf = getOtherHalf(world, bedPos);
		if (otherHalf == null) {
			return;
		}

		boolean half1 = isBedHalfEncased(world, bedPos);
		boolean half2 = isBedHalfEncased(world, otherHalf);
		boolean fullyEncased = half1 && half2;
		boolean wasEncased = fullyEncasedBeds.contains(bedPos) || fullyEncasedBeds.contains(otherHalf);
		fullyEncasedBeds.remove(bedPos);
		fullyEncasedBeds.remove(otherHalf);

		if (fullyEncased && !wasEncased) {
			fullyEncasedBeds.add(bedPos);
			fullyEncasedBeds.add(otherHalf);
			if (BedplateCommand.Config.fullObsidianNotifs) {
				sendChat("§8Bed fully encased in obsidian! §7at §f" +
						bedPos.getX() + " " + bedPos.getY() + " " + bedPos.getZ());
			}

		} else if (!fullyEncased && wasEncased) {
			fullyEncasedBeds.remove(bedPos);
			fullyEncasedBeds.remove(otherHalf);
			if (BedplateCommand.Config.fullObsidianNotifs){
				sendChat("§8No more full obby :D §7at §f" +
						bedPos.getX() + " " + bedPos.getY() + " " + bedPos.getZ());
			}
		}
	}

	public Set<BlockPos> getKnownBeds() {
		return this.knownBeds;
	}

	public Set<BlockPos> getObsidianBlocks() {
		return this.obsidianBlocks;
	}

	/**
	 * Enhanced BedData with team information
	 */
	public static class BedData {
		public BlockPos pos;
		public Set<Block> defenseBlocks = new HashSet<>();
		public BedTeam team;

		public BedData(BlockPos pos) {
			this(pos, BedTeam.UNKNOWN);
		}

		public BedData(BlockPos pos, BedTeam team) {
			this.pos = pos;
			this.team = team;
		}

		public ItemStack[] getDefenseItems() {
			ItemStack[] stacks = new ItemStack[defenseBlocks.size()];
			int i = 0;
			for (Block block : defenseBlocks) {
				stacks[i++] = new ItemStack(block);
			}
			return stacks;
		}
	}

	public static List<BedData> getTrackedBeds() {
		return trackedBeds;
	}
}
