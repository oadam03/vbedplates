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

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class BedScanner {
	private final Set<BlockPos> knownBeds = new HashSet<>();
	private final Set<Block> defenseBlocks = new HashSet<>();
	private final Set<BlockPos> obsidianBlocks = new HashSet<>();
	private final Set<BlockPos> fullyEncasedBeds = new HashSet<>();
	private final Map<BlockPos, Block> lastKnownBlocks = new HashMap<>();
	public static List<BedData> trackedBeds = new ArrayList<>();

	// Scan range constants
	private static final int RADIUS_XZ = 30;
	private static final int HEIGHT_UP = 15;
	private static final int HEIGHT_DOWN = 5;
	private static final int OBSIDIAN_SCAN_RADIUS = 5;

	private int tickCounter = 0;

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) return;

		World world = mc.theWorld;
		BlockPos playerPos = mc.thePlayer.getPosition();

		// Every second, rescan for new or destroyed beds
		if (world.getTotalWorldTime() % 20 == 0) {
			scanForBeds(world, playerPos);
			updateDefenseBlocks(world);
			rebuildObsidianHighlights(world); // Rebuild obsidian list from scratch
		}

		// Every 0.5 seconds, detect nearby obsidian changes
		if (tickCounter++ >= 10) {
			detectNearbyBlockChanges(world);
			tickCounter = 0;
		}
	}

	// On world load check for beds.
	@SubscribeEvent
	public void onWorldLoad(WorldEvent.Load event) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc.theWorld == null || mc.thePlayer == null) return;

		World world = mc.theWorld;
		BlockPos playerPos = mc.thePlayer.getPosition();

		sendChat("§7World loaded — performing initial bed scan...");
		scanForBeds(world, playerPos);
		updateDefenseBlocks(world);
	}

	private void detectNearbyBlockChanges(World world) {
		Set<BlockPos> affectedBeds = new HashSet<>();
		Set<BlockPos> newObsidian = new HashSet<>();

		// Only check beds that still exist
		for (BlockPos bed : new HashSet<>(knownBeds)) {
			// Skip if bed was destroyed
			if (world.getBlockState(bed).getBlock() != Blocks.bed) {
				continue;
			}

			// Only check the 2-block radius cube around the bed
			BlockPos min = bed.add(-2, 0, -2);
			BlockPos max = bed.add(2, 4, 2);

			for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
				Block current = world.getBlockState(pos).getBlock();
				Block previous = lastKnownBlocks.get(pos);

				// Always refresh last known
				lastKnownBlocks.put(pos, current);

				// Keep obsidian for rendering
				if (current == Blocks.obsidian) {
					newObsidian.add(pos);
				}

				// Detect obsidian placement/removal changes
				if (previous == null || previous != current) {
					if (current == Blocks.obsidian || previous == Blocks.obsidian) {
						affectedBeds.add(bed);
					}
				}
			}
		}

		// Update visible obsidian highlights
		obsidianBlocks.clear();
		obsidianBlocks.addAll(newObsidian);

		// Recheck only affected beds for encasement
		for (BlockPos bed : affectedBeds) {
			checkFullBedEncasement(world, bed);
		}
	}

	private void scanForBeds(World world, BlockPos center) {
		// Check known beds are still valid
		List<BedData> toRemoveData = new ArrayList<>();
		Set<BlockPos> toRemoveBeds = new HashSet<>();

		for (BedData bedData : trackedBeds) {
			Block block = world.getBlockState(bedData.pos).getBlock();
			if (block != Blocks.bed) {
				toRemoveData.add(bedData);
				toRemoveBeds.add(bedData.pos);

				// Clear ESP outlines for obsidian near this destroyed bed
				BlockPos min = bedData.pos.add(-OBSIDIAN_SCAN_RADIUS, 0, -OBSIDIAN_SCAN_RADIUS);
				BlockPos max = bedData.pos.add(OBSIDIAN_SCAN_RADIUS, OBSIDIAN_SCAN_RADIUS, OBSIDIAN_SCAN_RADIUS);
				for (BlockPos pos : BlockPos.getAllInBox(min, max)) {
					obsidianBlocks.remove(pos);  // Remove from visual tracking
					lastKnownBlocks.remove(pos); // Clear cache
				}

				//sendChat("§7Bed at §f" + bedData.pos + " §7was destroyed.");
			}
		}

		// Remove invalid beds from all tracking structures
		trackedBeds.removeAll(toRemoveData);
		knownBeds.removeAll(toRemoveBeds);
		defenseBlocks.clear();
		fullyEncasedBeds.removeAll(toRemoveBeds);

		// Scan for new beds
		for (int x = -RADIUS_XZ; x <= RADIUS_XZ; x++) {
			for (int y = -HEIGHT_DOWN; y <= HEIGHT_UP; y++) {
				for (int z = -RADIUS_XZ; z <= RADIUS_XZ; z++) {
					BlockPos checkPos = center.add(x, y, z);
					Block block = world.getBlockState(checkPos).getBlock();

					if (block == Blocks.bed) {
						int meta = block.getMetaFromState(world.getBlockState(checkPos));
						boolean isHead = (meta & 8) != 0;
						if (!isHead && !knownBeds.contains(checkPos)) {
							knownBeds.add(checkPos);
							//sendChat("§cFound bed (foot) at §f" + checkPos.getX() + " " + checkPos.getY() + " " + checkPos.getZ());
							BlockPos headPos = getOtherHalf(world, checkPos);
							if (headPos != null) {
								knownBeds.add(headPos);
							}
							// Track bed for rendering overlays
							BedData bedData = new BedData(checkPos);
							trackedBeds.add(bedData);

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
		//sendChat("Scanning defense for bed @ " + bedPos);
		for (int x = -radius; x <= radius; x++) {
			for (int y = 0; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					BlockPos checkPos = bedPos.add(x, y, z);
					Block nearbyBlock = world.getBlockState(checkPos).getBlock();

					if (nearbyBlock == Blocks.obsidian) {
						if (!obsidianBlocks.contains(checkPos)) {
							obsidianBlocks.add(checkPos);
							// sendChat("§5Detected obsidian §7at §f" + checkPos.getX() + " " + checkPos.getY() + " " + checkPos.getZ());
						}
					}
				}
			}
		}
	}

	// Rebuild the entire obsidian highlight list from all active beds
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

	public static class BedData {
		public BlockPos pos;
		public Set<Block> defenseBlocks = new HashSet<>();

		public BedData(BlockPos pos) {
			this.pos = pos;
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