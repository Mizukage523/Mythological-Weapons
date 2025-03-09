package me.mizu.mythologicalWeapons;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("all")
public class MythologicalWeapons extends JavaPlugin {
    private BladesOfChaos bladesOfChaos;
    private KhonshuMoonStaff khonshuMoonStaff;
    private WukongStaff wukongStaff;
    private OdinsGungnir odinsGungnir;

    @Override
    public void onEnable() {
        // Initialize weapon classes
        bladesOfChaos = new BladesOfChaos(this);
        khonshuMoonStaff = new KhonshuMoonStaff(this);
        wukongStaff = new WukongStaff(this);
        odinsGungnir = new OdinsGungnir(this);

        // Save default config
        saveDefaultConfig();

        // Register commands and tab completer
        PluginCommand mythologyCommand = getCommand("mythology");
        if (mythologyCommand != null) {
            mythologyCommand.setTabCompleter(new MythologyTabCompleter());
        } else {
            getLogger().severe("Failed to register /mythology command. Check your plugin.yml file.");
        }

        getLogger().info("Mythological Weapons has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Mythological Weapons has been disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("mythology")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
                return true;
            }

            Player player = (Player) sender;

            // Check if the player provided a weapon name
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /mythology <weapon>");
                player.sendMessage(ChatColor.GRAY + "Available weapons: §4§lChaosblade, §8§lMoonstaff, §e§lWukongstaff, §3§lGungnir");
                return true;
            }

            String weaponName = args[0].toLowerCase();

            // Give the requested weapon
            switch (weaponName) {
                case "chaosblade":
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                        return true;
                    }
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage(ChatColor.RED + "Your inventory is full!");
                        return true;
                    }
                    player.getInventory().addItem(bladesOfChaos.getBladesOfChaos());
                    player.sendMessage("You have received §4§lThe Blades of Chaos!");
                    break;

                case "moonstaff":
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                        return true;
                    }
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage(ChatColor.RED + "Your inventory is full!");
                        return true;
                    }
                    player.getInventory().addItem(khonshuMoonStaff.getKhonshuMoonStaff());
                    player.sendMessage("You have received §8§lKhonshu’s Moon Staff!");
                    break;

                case "wukongstaff":
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                        return true;
                    }
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage(ChatColor.RED + "Your inventory is full!");
                        return true;
                    }
                    player.getInventory().addItem(wukongStaff.getWukongStaff());
                    player.sendMessage("You have received §e§lWukong’s Staff!");
                    break;

                case "gungnir":
                    if (!player.isOp()) {
                        player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                        return true;
                    }
                    if (player.getInventory().firstEmpty() == -1) {
                        player.sendMessage(ChatColor.RED + "Your inventory is full!");
                        return true;
                    }
                    player.getInventory().addItem(odinsGungnir.getOdinsGungnir());
                    player.sendMessage("You have received §3§lOdin’s Gungnir!");
                    break;

                default:
                    player.sendMessage(ChatColor.RED + "Invalid weapon name.");
                    player.sendMessage(ChatColor.GRAY + "Available weapons: §4§lChaosblade, §8§lMoonstaff, §e§lWukongstaff, §3§lGungnir");
                    break;
            }
            return true;
        }
        return false;
    }

    // Inner class for tab completer
    public class MythologyTabCompleter implements TabCompleter {
        private final List<String> weapons = Arrays.asList("chaosblade", "moonstaff", "wukongstaff", "gungnir");

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (command.getName().equalsIgnoreCase("mythology") && args.length == 1) {
                // Return matching weapon names
                List<String> completions = new ArrayList<>();
                for (String weapon : weapons) {
                    if (weapon.startsWith(args[0].toLowerCase())) {
                        completions.add(weapon);
                    }
                }
                return completions;
            }
            return null;
        }
    }
}