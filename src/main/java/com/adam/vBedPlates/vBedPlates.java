package com.adam.vBedPlates;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.common.MinecraftForge;

import com.adam.vBedPlates.events.BedScanner;
import com.adam.vBedPlates.events.BedplateRender;
import com.adam.vBedPlates.commands.BedplateCommand;
import com.adam.vBedPlates.config.MapConfigManager;
import com.adam.vBedPlates.config.MapConfig;
import com.adam.vBedPlates.util.ScoreboardParser;
import com.adam.vBedPlates.util.TeamDetector;

@Mod(modid = vBedPlates.MODID, version = vBedPlates.VERSION)
public class vBedPlates {
	public static final String MODID = "vbedplates";
	public static final String VERSION = "0.1";


	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		MapConfigManager.getInstance().initialize();
		BedScanner scanner = new BedScanner();
		MinecraftForge.EVENT_BUS.register(scanner);
		ClientCommandHandler.instance.registerCommand(new BedplateCommand());
		MinecraftForge.EVENT_BUS.register(new BedplateRender(scanner));
		System.out.println("BedScanner and BedDefenseRenderer online");
	}


}
