package com.adam.vBedPlates.commands;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

public class BedplateCommand extends CommandBase {

	// Config class to hold settings
	public static class Config {
		public static boolean showBillboards = true;
		public static boolean showObsidianESP = true;
		public static boolean showBedOutlines = true;
		public static boolean enableChatMessages = true;
		public static boolean fullObsidianNotifs = true;
	}

	@Override
	public String getCommandName() {
		return "bedplate";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/bedplate <toggle|help> [setting]";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0; // Anyone can use
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		if (args.length == 0) {
			sendHelp(sender);
			return;
		}

		String subCommand = args[0].toLowerCase();

		switch (subCommand) {
			case "toggle":
				if (args.length < 2) {
					sendMessage(sender, "§cUsage: /bedplate toggle <bedplates|obsidian|bedesp|fullobby|messages>");
					return;
				}
				handleToggle(sender, args[1].toLowerCase());
				break;

			case "status":
				sendStatus(sender);
				break;

			case "help":
			default:
				sendHelp(sender);
				break;
		}
	}

	private void handleToggle(ICommandSender sender, String setting) {
		switch (setting) {
			case "billboards":
			case "billboard":
			case "bedplate":
			case "bedplates":
				Config.showBillboards = !Config.showBillboards;
				sendMessage(sender, "§aBedplates: " + getStatusText(Config.showBillboards));
				System.out.println("[vBedplates] Bedplates toggled to: " + Config.showBillboards);
				break;

			case "obsidian":
			case "obby":
			case "oesp":
			case "obbyesp":
				Config.showObsidianESP = !Config.showObsidianESP;
				sendMessage(sender, "§aObsidian ESP: " + getStatusText(Config.showObsidianESP));
				System.out.println("[Bedplate] Obsidian ESP toggled to: " + Config.showObsidianESP);
				break;

			case "outlines":
			case "outline":
			case "bedoutline":
			case "bedesp":
			case "beds":
				Config.showBedOutlines = !Config.showBedOutlines;
				sendMessage(sender, "§aBed Outlines: " + getStatusText(Config.showBedOutlines));
				System.out.println("[Bedplate] Bed Outlines toggled to: " + Config.showBedOutlines);
				break;

			case "chat":
			case "messages":
				Config.enableChatMessages = !Config.enableChatMessages;
				sendMessage(sender, "§aChat Messages: " + getStatusText(Config.enableChatMessages));
				System.out.println("[Bedplate] Chat Messages toggled to: " + Config.enableChatMessages);
				break;
			case "fullobby":
			case "obbynotif":
				Config.fullObsidianNotifs = !Config.fullObsidianNotifs;
				sendMessage(sender, "§5Full Obsidian Placed/Broken Notifications: " + getStatusText(Config.fullObsidianNotifs));
				break;

			default:
				sendMessage(sender, "§cUnknown setting: " + setting);
				sendMessage(sender, "§7Available: billboards, obsidian, outlines, chat");
				break;
		}
	}

	private void sendStatus(ICommandSender sender) {
		sendMessage(sender, "§7=§f=§c= §4vBedplate Modules §7=§f=§c=");
		sendMessage(sender, "§aBedplates: " + getStatusText(Config.showBillboards));
		sendMessage(sender, "§aObbyESP: " + getStatusText(Config.showObsidianESP));
		sendMessage(sender, "§aBedESP: " + getStatusText(Config.showBedOutlines));
		sendMessage(sender, "§aMessages: " + getStatusText(Config.enableChatMessages));
	}

	private void sendHelp(ICommandSender sender) {
		sendMessage(sender, "§7=§f=§c= §4vBedplate Commands §7=§f=§c=");
		sendMessage(sender, "§e/bedplate toggle <setting> §7- Toggle a feature");
		sendMessage(sender, "§e/bedplate status §7- Show all settings");
		sendMessage(sender, "§e/bedplate help §7- Show this help");
		sendMessage(sender, "");
		sendMessage(sender, "§7Settings: §fbillboards, obsidian, outlines, chat");
	}

	private String getStatusText(boolean enabled) {
		return enabled ? "§2ON" : "§cOFF";
	}

	private void sendMessage(ICommandSender sender, String message) {
		sender.addChatMessage(new ChatComponentText("[§avBedplate§f] " + message));
	}
}