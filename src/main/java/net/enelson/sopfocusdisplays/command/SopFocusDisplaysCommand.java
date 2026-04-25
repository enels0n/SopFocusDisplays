package net.enelson.sopfocusdisplays.command;

import net.enelson.sopfocusdisplays.SopFocusDisplays;
import net.enelson.sopfocusdisplays.model.FocusDisplayDefinition;
import net.enelson.sopfocusdisplays.model.FocusDisplayType;
import net.enelson.sopfocusdisplays.model.SpawnedFocusDisplay;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SopFocusDisplaysCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "remove", "movehere", "item", "text", "list", "reload");
    private static final List<String> DISPLAY_TYPES = Arrays.asList("item", "text");

    private final SopFocusDisplays plugin;

    public SopFocusDisplaysCommand(SopFocusDisplays plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        if ("reload".equals(sub)) {
            this.plugin.getFocusDisplayManager().reloadAll();
            sender.sendMessage(color("&aSopFocusDisplays reloaded."));
            return true;
        }

        if ("list".equals(sub)) {
            if (this.plugin.getFocusDisplayManager().getDisplays().isEmpty()) {
                sender.sendMessage(color("&eNo displays created."));
                return true;
            }

            sender.sendMessage(color("&eDisplays:"));
            for (SpawnedFocusDisplay display : this.plugin.getFocusDisplayManager().getDisplays()) {
                FocusDisplayDefinition definition = display.getDefinition();
                sender.sendMessage(color("&7- &f" + definition.getId() + " &8(" + definition.getType().name().toLowerCase() + "&8)"));
            }
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(color("&cOnly players can use this subcommand."));
            return true;
        }

        Player player = (Player) sender;
        if ("create".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(color("&eUsage: /" + label + " create <item|text> <id> [text...]"));
                return true;
            }

            String type = args[1].toLowerCase();
            String id = args[2];
            Location location = resolvePlacement(player);

            if ("item".equals(type)) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand == null || hand.getType() == Material.AIR) {
                    sender.sendMessage(color("&cHold an item first."));
                    return true;
                }
                boolean created = this.plugin.getFocusDisplayManager().createItem(id, location, hand.clone());
                sender.sendMessage(color(created ? "&aCreated item focus display &f" + id : "&cDisplay already exists."));
                return true;
            }

            if ("text".equals(type)) {
                String text = args.length >= 4
                        ? String.join(" ", Arrays.copyOfRange(args, 3, args.length))
                        : this.plugin.getConfig().getString("text.default-text", "<gold>Focus Text</gold>");
                boolean created = this.plugin.getFocusDisplayManager().createText(id, location, text);
                sender.sendMessage(color(created ? "&aCreated text focus display &f" + id : "&cDisplay already exists."));
                return true;
            }

            sender.sendMessage(color("&cUnknown display type."));
            return true;
        }

        if ("remove".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(color("&eUsage: /" + label + " remove <id>"));
                return true;
            }
            boolean removed = this.plugin.getFocusDisplayManager().remove(args[1]);
            sender.sendMessage(color(removed ? "&aRemoved focus display &f" + args[1] : "&cDisplay not found."));
            return true;
        }

        if ("movehere".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(color("&eUsage: /" + label + " movehere <id>"));
                return true;
            }
            boolean moved = this.plugin.getFocusDisplayManager().moveHere(args[1], resolvePlacement(player));
            sender.sendMessage(color(moved ? "&aMoved focus display &f" + args[1] : "&cDisplay not found."));
            return true;
        }

        if ("item".equals(sub)) {
            if (args.length < 2) {
                sender.sendMessage(color("&eUsage: /" + label + " item <id>"));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
                sender.sendMessage(color("&cHold an item first."));
                return true;
            }
            boolean updated = this.plugin.getFocusDisplayManager().updateItem(args[1], hand.clone());
            sender.sendMessage(color(updated ? "&aUpdated display item for &f" + args[1] : "&cItem display not found."));
            return true;
        }

        if ("text".equals(sub)) {
            if (args.length < 3) {
                sender.sendMessage(color("&eUsage: /" + label + " text <id> <text...>"));
                return true;
            }
            String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            boolean updated = this.plugin.getFocusDisplayManager().updateText(args[1], text);
            sender.sendMessage(color(updated ? "&aUpdated display text for &f" + args[1] : "&cText display not found."));
            return true;
        }

        sendHelp(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])) {
            return filter(DISPLAY_TYPES, args[1]);
        }
        if (args.length == 2 && Arrays.asList("remove", "movehere", "item", "text").contains(args[0].toLowerCase())) {
            List<String> values = new ArrayList<String>();
            for (SpawnedFocusDisplay display : this.plugin.getFocusDisplayManager().getDisplays()) {
                FocusDisplayType type = display.getDefinition().getType();
                if ("item".equalsIgnoreCase(args[0]) && type != FocusDisplayType.ITEM) {
                    continue;
                }
                if ("text".equalsIgnoreCase(args[0]) && type != FocusDisplayType.TEXT) {
                    continue;
                }
                values.add(display.getDefinition().getId());
            }
            return filter(values, args[1]);
        }
        return Collections.emptyList();
    }

    private Location resolvePlacement(Player player) {
        int targetDistance = this.plugin.getConfig().getInt("create.target-block-distance", 10);
        Block target = player.getTargetBlockExact(targetDistance);
        if (target != null) {
            Location location = target.getLocation().add(0.5D, 1.0D + this.plugin.getConfig().getDouble("create.y-offset", 0.0D), 0.5D);
            location.setYaw(player.getLocation().getYaw());
            location.setPitch(player.getLocation().getPitch() + (float) this.plugin.getConfig().getDouble("fallback.pitch-offset", 0.0D));
            return location;
        }

        double distance = this.plugin.getConfig().getDouble("create.spawn-distance", 2.5D);
        Location location = player.getEyeLocation().clone().add(player.getEyeLocation().getDirection().multiply(distance));
        location.setYaw(player.getLocation().getYaw());
        location.setPitch(player.getLocation().getPitch() + (float) this.plugin.getConfig().getDouble("fallback.pitch-offset", 0.0D));
        return location;
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase();
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase().startsWith(lower)) {
                result.add(value);
            }
        }
        return result;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(color("&e/sopfocusdisplays create item <id>"));
        sender.sendMessage(color("&e/sopfocusdisplays create text <id> <text...>"));
        sender.sendMessage(color("&e/sopfocusdisplays remove <id>"));
        sender.sendMessage(color("&e/sopfocusdisplays movehere <id>"));
        sender.sendMessage(color("&e/sopfocusdisplays item <id>"));
        sender.sendMessage(color("&e/sopfocusdisplays text <id> <text...>"));
        sender.sendMessage(color("&e/sopfocusdisplays list"));
        sender.sendMessage(color("&e/sopfocusdisplays reload"));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}