package com.adam.vBedPlates.events;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import org.lwjgl.opengl.GL11;

import com.adam.vBedPlates.commands.BedplateCommand;

import java.util.List;
import java.util.ArrayList;

/**
 * BedplateRender - Renders billboard UI above beds showing defense block types
 * and highlights obsidian blocks with ESP wireframes.
 */
public class BedplateRender {

	// Visual constants
	private static final float BILLBOARD_BASE_SCALE = 0.05F; // Base multiplier for distance-based scaling (adjust for size)
	private static final float BILLBOARD_SCALE_MIN = 1.5F; // Minimum scale when very close
	private static final float BILLBOARD_SCALE_MAX = 5.0F; // Maximum scale when very far
	private static final float ITEM_SCALE = 0.45F;
	private static final double BILLBOARD_HEIGHT_OFFSET = 2.5;
	private static final double OBSIDIAN_RENDER_DISTANCE = 50.0;
	private static final double BILLBOARD_MIN_DISTANCE = 2.0; // Don't render if closer than this

	// Colors - Glassy gray background
	private static final float BOX_COLOR_R = 0.3F;
	private static final float BOX_COLOR_G = 0.3F;
	private static final float BOX_COLOR_B = 0.3F;
	private static final float BOX_ALPHA = 0.65F; // Less opaque for better visibility

	private final Minecraft mc = Minecraft.getMinecraft();
	private final BedScanner scanner;

	public BedplateRender(BedScanner scanner) {
		this.scanner = scanner;
	}

	@SubscribeEvent
	public void onRenderWorldLast(RenderWorldLastEvent event) {
		if (mc.theWorld == null || mc.thePlayer == null) return;

		EntityPlayerSP player = mc.thePlayer;
		World world = mc.theWorld;
		double partialTicks = event.partialTicks;

		double camX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
		double camY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
		double camZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

		GlStateManager.pushMatrix();
		GlStateManager.translate(-camX, -camY, -camZ);

		List<BedScanner.BedData> beds = BedScanner.getTrackedBeds();
		if (beds != null && (BedplateCommand.Config.showBillboards || BedplateCommand.Config.showBedOutlines)) {
			for (BedScanner.BedData bed : beds) {
				if (bed == null || bed.pos == null) continue;

				// Draw the lower-half red outline (if enabled)
				if (BedplateCommand.Config.showBedOutlines) {
					drawBedOutline(world, bed.pos);
				}

				// Draw the billboard with items (if enabled)
				if (BedplateCommand.Config.showBillboards) {
					drawBedplateBillboard(world, bed);
				}
			}
		}

		// Obsidian ESP highlights with distance culling and fade (if enabled)
		if (BedplateCommand.Config.showObsidianESP && scanner != null) {
			for (BlockPos obPos : scanner.getObsidianBlocks()) {
				// Calculate squared distance for efficiency (avoid sqrt)
				double dx = obPos.getX() + 0.5 - camX;
				double dy = obPos.getY() + 0.5 - camY;
				double dz = obPos.getZ() + 0.5 - camZ;
				double distSq = dx * dx + dy * dy + dz * dz;

				// Only render if within distance
				if (distSq <= OBSIDIAN_RENDER_DISTANCE * OBSIDIAN_RENDER_DISTANCE) {
					// Calculate alpha based on distance (fade effect)
					float alpha = 1.0f - (float)(Math.sqrt(distSq) / OBSIDIAN_RENDER_DISTANCE);
					alpha = Math.max(0.3f, alpha); // Don't go fully transparent

					drawOutlinedWireCube(obPos.getX(), obPos.getY(), obPos.getZ(),
							1.0, 1.0, 1.0, 0.6f, 0.0f, 0.8f, alpha);
				}
			}
		}

		GlStateManager.popMatrix();
	}

	/**
	 * Draw the red wireframe prism for the bed aligned to the lower half (1 x 0.5 x 2)
	 */
	private void drawBedOutline(World world, BlockPos footPos) {
		if (!BedplateCommand.Config.showBedOutlines) return; // Early exit if disabled
		if (world.getBlockState(footPos).getBlock() != Blocks.bed) return;

		int meta = world.getBlockState(footPos).getBlock().getMetaFromState(world.getBlockState(footPos));
		int dir = meta & 3;

		BlockPos headPos;
		switch (dir) {
			case 0: headPos = footPos.south(); break;
			case 1: headPos = footPos.west();  break;
			case 2: headPos = footPos.north(); break;
			case 3: headPos = footPos.east();  break;
			default: headPos = footPos.south(); break;
		}

		double minX = Math.min(footPos.getX(), headPos.getX());
		double minZ = Math.min(footPos.getZ(), headPos.getZ());
		double width = Math.abs(headPos.getX() - footPos.getX()) + 1.0;
		double depth = Math.abs(headPos.getZ() - footPos.getZ()) + 1.0;

		double prismHeight = 0.5;
		double y = footPos.getY() + 0.25 + 0.01;

		drawWireframePrism(minX, y, minZ, width, prismHeight, depth,
				1.0F, 0.0F, 0.0F, 1.0F);
	}

	/**
	 * Draw a billboard centered above the bed showing a gradient grey box with item icons.
	 * Box maintains constant on-screen size; icons are drawn at a fixed readable size.
	 */
	private void drawBedplateBillboard(World world, BedScanner.BedData bed) {
		if (!BedplateCommand.Config.showBillboards) return; // Early exit if disabled
		if (bed == null || bed.pos == null) return;

		// Find head to compute center between halves
		BlockPos foot = bed.pos;
		BlockPos head = null;
		if (world.getBlockState(foot).getBlock() == Blocks.bed) {
			int meta = world.getBlockState(foot).getBlock().getMetaFromState(world.getBlockState(foot));
			int dir = meta & 3;
			switch (dir) {
				case 0: head = foot.south(); break;
				case 1: head = foot.west();  break;
				case 2: head = foot.north(); break;
				case 3: head = foot.east();  break;
				default: head = foot.south(); break;
			}
		} else {
			head = foot;
		}

		// Center between foot and head (in XZ)
		double centerX = (foot.getX() + head.getX()) / 2.0 + 0.5;
		double centerZ = (foot.getZ() + head.getZ()) / 2.0 + 0.5;
		double centerY = foot.getY() + BILLBOARD_HEIGHT_OFFSET;

		// Check distance - don't render if too close
		double distToBillboard = mc.thePlayer.getDistance(centerX, centerY, centerZ);
		if (distToBillboard < BILLBOARD_MIN_DISTANCE) {
			return; // Skip rendering when very close
		}

		// Calculate box size based on number of defense blocks (dynamic)
		int count = (bed.defenseBlocks == null) ? 0 : bed.defenseBlocks.size();
		double boxWidth = Math.max(0.9, Math.min(3.2, 0.9 + 0.35 * count));
		double boxHeight = 0.5;
		double halfW = boxWidth / 2.0;
		double halfH = boxHeight / 2.0;

		// Save and set state for billboard
		GlStateManager.pushMatrix();
		GlStateManager.translate(centerX, centerY, centerZ);

		// Make it face the player (billboard effect)
		float pvYaw = mc.getRenderManager().playerViewY;
		float pvPitch = mc.getRenderManager().playerViewX;
		GlStateManager.rotate(-pvYaw, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(pvPitch, 1.0F, 0.0F, 0.0F);

		// Distance-based scaling to maintain constant on-screen size
		// Scale grows with distance so it appears same size on screen
		float scale = (float)(BILLBOARD_BASE_SCALE * distToBillboard);
		// Clamp between min/max to prevent extreme sizes
		scale = Math.max(BILLBOARD_SCALE_MIN, Math.min(BILLBOARD_SCALE_MAX, scale));

		GlStateManager.scale(scale, scale, scale);

		// Draw the glassy gray background using raw GL11 calls
		GlStateManager.pushMatrix();
		GlStateManager.disableLighting();
		GlStateManager.disableTexture2D();
		GlStateManager.enableBlend();
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GlStateManager.disableDepth();
		GlStateManager.disableCull();

		// Push the background slightly back so items render on top
		GlStateManager.translate(0.0, 0.0, -0.01);

		// Draw using immediate mode GL11
		GL11.glColor4f(BOX_COLOR_R, BOX_COLOR_G, BOX_COLOR_B, BOX_ALPHA);

		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex3d(-halfW, -halfH, 0.0);
		GL11.glVertex3d(halfW, -halfH, 0.0);
		GL11.glVertex3d(halfW, halfH, 0.0);
		GL11.glVertex3d(-halfW, halfH, 0.0);
		GL11.glEnd();

		// Reset translation
		GlStateManager.popMatrix();

		// Reset color
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();

		// Draw darker border for depth
		GL11.glColor4f(0.05F, 0.05F, 0.05F, 0.9F);
		GL11.glLineWidth(2.5F);
		wr.begin(GL11.GL_LINE_LOOP, DefaultVertexFormats.POSITION);
		wr.pos(-halfW, -halfH, 0.0).endVertex();
		wr.pos(halfW, -halfH, 0.0).endVertex();
		wr.pos(halfW, halfH, 0.0).endVertex();
		wr.pos(-halfW, halfH, 0.0).endVertex();
		tess.draw();

		// Reset color
		GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

		GlStateManager.enableCull();

		// Draw item icons inside the box (using 3D renderer but flattened)
		if (bed.defenseBlocks != null && !bed.defenseBlocks.isEmpty()) {
			List<Block> blocks = new ArrayList<>(bed.defenseBlocks);
			int itemCount = blocks.size();

			// Layout: evenly spaced horizontally with padding
			double totalWidth = boxWidth * 0.9;
			double iconSpacing = totalWidth / Math.max(1, itemCount);
			double startX = - (totalWidth / 2.0) + iconSpacing / 2.0;

			// Prepare item rendering
			GlStateManager.enableTexture2D();
			GlStateManager.enableRescaleNormal();
			RenderHelper.enableGUIStandardItemLighting();
			GlStateManager.enableBlend();
			GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
			GlStateManager.disableDepth(); // Force items to render flat without depth

			RenderItem renderItem = mc.getRenderItem();
			renderItem.zLevel = 200.0F;

			for (int i = 0; i < itemCount; i++) {
				Block b = blocks.get(i);
				ItemStack stack = new ItemStack(b);

				GlStateManager.pushMatrix();

				// Position each icon
				double ix = startX + i * iconSpacing;
				double iy = 0.0;

				GlStateManager.translate(ix, iy, 0.01);

				// Scale with minimal Z to keep mostly flat
				GlStateManager.scale(ITEM_SCALE, ITEM_SCALE, 0.05);

				// Add isometric rotations for GUI/inventory look
				GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
				GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
				GlStateManager.rotate(30.0F, 1.0F, 0.0F, 1.0F);

				// Use GUI transform
				renderItem.renderItem(stack, ItemCameraTransforms.TransformType.GUI);

				GlStateManager.popMatrix();
			}

			// Restore item rendering state
			renderItem.zLevel = 0.0F;
			GlStateManager.enableDepth();
			RenderHelper.disableStandardItemLighting();
			GlStateManager.disableRescaleNormal();
		}

		// Restore GL state
		GlStateManager.enableTexture2D();
		GlStateManager.enableDepth();
		GlStateManager.disableBlend();
		GlStateManager.enableLighting();

		GlStateManager.popMatrix();
	}

	/**
	 * Draw an outlined wireframe prism (no fill). x,y,z is the min corner (block coordinate style)
	 */
	private void drawWireframePrism(double minX, double minY, double minZ, double width, double height, double depth,
									float r, float g, float b, float alpha) {
		GlStateManager.pushMatrix();
		GlStateManager.disableTexture2D();
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);

		GL11.glLineWidth(2.0F);
		Tessellator tess = Tessellator.getInstance();
		WorldRenderer wr = tess.getWorldRenderer();

		// Compute 8 corners
		double x0 = minX;
		double x1 = minX + width;
		double y0 = minY;
		double y1 = minY + height;
		double z0 = minZ;
		double z1 = minZ + depth;

		// Draw all edges
		wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

		// 4 vertical edges
		wr.pos(x0, y0, z0).color(r, g, b, alpha).endVertex(); wr.pos(x0, y1, z0).color(r, g, b, alpha).endVertex();
		wr.pos(x1, y0, z0).color(r, g, b, alpha).endVertex(); wr.pos(x1, y1, z0).color(r, g, b, alpha).endVertex();
		wr.pos(x1, y0, z1).color(r, g, b, alpha).endVertex(); wr.pos(x1, y1, z1).color(r, g, b, alpha).endVertex();
		wr.pos(x0, y0, z1).color(r, g, b, alpha).endVertex(); wr.pos(x0, y1, z1).color(r, g, b, alpha).endVertex();

		// Bottom rectangle
		wr.pos(x0, y0, z0).color(r, g, b, alpha).endVertex(); wr.pos(x1, y0, z0).color(r, g, b, alpha).endVertex();
		wr.pos(x1, y0, z0).color(r, g, b, alpha).endVertex(); wr.pos(x1, y0, z1).color(r, g, b, alpha).endVertex();
		wr.pos(x1, y0, z1).color(r, g, b, alpha).endVertex(); wr.pos(x0, y0, z1).color(r, g, b, alpha).endVertex();
		wr.pos(x0, y0, z1).color(r, g, b, alpha).endVertex(); wr.pos(x0, y0, z0).color(r, g, b, alpha).endVertex();

		// Top rectangle
		wr.pos(x0, y1, z0).color(r, g, b, alpha).endVertex(); wr.pos(x1, y1, z0).color(r, g, b, alpha).endVertex();
		wr.pos(x1, y1, z0).color(r, g, b, alpha).endVertex(); wr.pos(x1, y1, z1).color(r, g, b, alpha).endVertex();
		wr.pos(x1, y1, z1).color(r, g, b, alpha).endVertex(); wr.pos(x0, y1, z1).color(r, g, b, alpha).endVertex();
		wr.pos(x0, y1, z1).color(r, g, b, alpha).endVertex(); wr.pos(x0, y1, z0).color(r, g, b, alpha).endVertex();

		tess.draw();

		GlStateManager.enableDepth();
		GlStateManager.enableTexture2D();
		GlStateManager.enableLighting();
		GlStateManager.disableBlend();
		GlStateManager.popMatrix();
	}

	/**
	 * Convenience wrapper for drawing obsidian block outlines
	 */
	private void drawOutlinedWireCube(double blockX, double blockY, double blockZ, double width, double height, double depth,
									  float r, float g, float b, float alpha) {
		if (!BedplateCommand.Config.showObsidianESP) return; // Early exit if disabled
		drawWireframePrism(blockX, blockY + 0.01, blockZ, width, height, depth, r, g, b, alpha);
	}
}