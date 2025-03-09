package me.mizu.mythologicalWeapons;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

@SuppressWarnings("all")
public class KhonshuMoonStaff implements Listener {
    private final MythologicalWeapons plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final int COOLDOWN; // Cooldown in seconds (configurable)
    private final String STAFF_NAME = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "Khonshu’s Moon Staff";
    private final int CUSTOM_MODEL_DATA_1 = 1; // Custom model data for inactive staff
    private final int CUSTOM_MODEL_DATA_2 = 2; // Custom model data for active staff
    private final double DAMAGE_MULTIPLIER; // Damage multiplier (configurable)
    private final int SLOWNESS_DURATION; // Slowness duration in seconds (configurable)
    private final double EFFECT_RADIUS; // Radius for applying effects (configurable)
    private final int TIME_INCREMENT; // Time increment per tick (configurable)
    private final int NIGHT_DURATION; // Duration of nighttime in seconds (configurable)
    private final HashMap<UUID, BukkitRunnable> actionBarTasks = new HashMap<>(); // Track active cooldown tasks


    public KhonshuMoonStaff(MythologicalWeapons plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Load configuration values
        COOLDOWN = plugin.getConfig().getInt("khonshu-moon-staff.cooldown", 35); // Default: 35 seconds
        DAMAGE_MULTIPLIER = plugin.getConfig().getDouble("khonshu-moon-staff.damage-multiplier", 1.5); // Default: 1.5x
        SLOWNESS_DURATION = plugin.getConfig().getInt("khonshu-moon-staff.slowness-duration", 10); // Default: 10 seconds
        EFFECT_RADIUS = plugin.getConfig().getDouble("khonshu-moon-staff.effect-radius", 15.0); // Default: 15 blocks
        TIME_INCREMENT = plugin.getConfig().getInt("khonshu-moon-staff.time-increment", 200); // Default: 200 ticks per tick
        NIGHT_DURATION = plugin.getConfig().getInt("khonshu-moon-staff.night-duration", 10); // Default: 10 seconds
    }

    public ItemStack getKhonshuMoonStaff() {
        ItemStack staff = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(STAFF_NAME);
        meta.setCustomModelData(CUSTOM_MODEL_DATA_1);
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("khonshu-moon-staff.lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        staff.setItemMeta(meta);
        return staff;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        // Check if the interaction is a right-click
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();

            if (isKhonshuMoonStaff(item)) {
                long currentTime = System.currentTimeMillis();
                long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);

                // Check if the cooldown has expired
                if (currentTime - lastUsed >= COOLDOWN * 1000L) {
                    cooldowns.put(player.getUniqueId(), currentTime);
                    // Trigger the ability
                    triggerAbility(player);
                    player.sendMessage(ChatColor.GREEN + "You invoked the power of §8§lKhonshu’s Moon Staff!");
                    // Spawn environmental effects
                    spawnEnvironmentalEffects(player.getLocation());
                    // Replace the staff with the active version
                    replaceWithActiveStaff(player);
                } else {
                    // Calculate remaining cooldown time
                    long remainingTime = (COOLDOWN * 1000L - (currentTime - lastUsed)) / 1000L;
                    player.sendMessage("Ability is on §4§lcooldown!");
                }
            }
        }
    }

    private void triggerAbility(Player player) {
        World world = player.getWorld();
        long targetNightTime = 18000; // Nighttime in Minecraft (18000 ticks)
        long currentTime = world.getTime();

        if (currentTime < 13000) { // Check if it's daytime
            player.sendMessage("§7§lThe moon rises...");
            new BukkitRunnable() {
                @Override
                public void run() {
                    long currentWorldTime = world.getTime();
                    if (currentWorldTime >= targetNightTime) {
                        this.cancel(); // Stop the task when nighttime is reached
                        activateNightEffects(player); // Activate nighttime effects

                        // Schedule turning back to day after NIGHT_DURATION seconds
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                world.setTime(0); // Instantly set time back to daytime
                                revertToInactiveStaff(player); // Revert to inactive staff
                            }
                        }.runTaskLater(plugin, 20L * NIGHT_DURATION); // Turn back to day after NIGHT_DURATION seconds
                        return;
                    }
                    world.setTime(currentWorldTime + TIME_INCREMENT); // Increment time by TIME_INCREMENT per tick
                }
            }.runTaskTimer(plugin, 0L, 1L); // Run every tick (20 times per second)
        }
    }

    private void activateNightEffects(Player player) {
        // Apply Strength effect to the player
        int strengthAmplifier = plugin.getConfig().getInt("khonshu-moon-staff.strength-amplifier", 0); // Default: Strength I (amplifier 0)
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * SLOWNESS_DURATION, strengthAmplifier, true, false));

        // Apply Slowness 4 to nearby enemies
        Location playerLocation = player.getLocation();
        for (Entity nearbyEntity : playerLocation.getWorld().getNearbyEntities(playerLocation, EFFECT_RADIUS, EFFECT_RADIUS, EFFECT_RADIUS)) {
            if (nearbyEntity instanceof LivingEntity && nearbyEntity != player) {
                LivingEntity entity = (LivingEntity) nearbyEntity;
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * SLOWNESS_DURATION, 3, true, false));
            }
        }

        // Spawn particles around the player
        player.getWorld().spawnParticle(Particle.END_ROD, playerLocation, 50, 2.0, 2.0, 2.0, 0.1);
        player.getWorld().playSound(playerLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
    }

    private void spawnEnvironmentalEffects(Location location) {
        // Spawn moon-themed particles
        location.getWorld().spawnParticle(Particle.END_ROD, location, 30, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.LARGE_SMOKE, location, 10, 0.5, 0.5, 0.5, 0.1);

        // Play a custom sound effect
        location.getWorld().playSound(location, Sound.AMBIENT_CAVE, 1.0f, 0.8f);
    }

    private void playCustomSoundtrack(Player player) {
        // Play a custom sound when the weapon is used or equipped
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0f, 0f);
    }

    private void replaceWithActiveStaff(Player player) {
        ItemStack activeStaff = player.getInventory().getItemInMainHand();
        ItemMeta meta = activeStaff.getItemMeta();
        meta.setCustomModelData(CUSTOM_MODEL_DATA_2);
        activeStaff.setItemMeta(meta);

        // Update the player's inventory
        player.getInventory().setItemInMainHand(activeStaff);

        // Start the "Ability Activated" action bar
        startAbilityActionBar(player);
    }

    private void revertToInactiveStaff(Player player) {
        ItemStack inactiveStaff = player.getInventory().getItemInMainHand();
        ItemMeta meta = inactiveStaff.getItemMeta();
        meta.setCustomModelData(CUSTOM_MODEL_DATA_1);
        inactiveStaff.setItemMeta(meta);

        // Update the player's inventory
        player.getInventory().setItemInMainHand(inactiveStaff);

        // Start the cooldown action bar
        startCooldownActionBar(player);
    }

    private void startAbilityActionBar(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    return;
                }

                ItemStack mainHandItem = player.getInventory().getItemInMainHand();
                // Only display "Ability Activated" if the player is holding the active staff
                if (isCustomModelData(mainHandItem, CUSTOM_MODEL_DATA_2)) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Ability Activated"));
                } else {
                    this.cancel(); // Cancel the task if the player switches to Custom Model Data 1 or stops holding the staff
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Update every second
    }

    private void startCooldownActionBar(Player player) {
        // Cancel any existing task for the player
        cancelCooldownActionBar(player);

        // Do not start the cooldown task if the staff is active (Custom Model Data 2)
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (isCustomModelData(mainHandItem, CUSTOM_MODEL_DATA_2)) {
            return;
        }

        // Create a new task to display the cooldown on the action bar
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isKhonshuMoonStaff(player.getInventory().getItemInMainHand())) {
                    cancelCooldownActionBar(player); // Stop the task if the player stops holding the staff
                    return;
                }

                long currentTime = System.currentTimeMillis();
                long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);
                long cooldownDuration = COOLDOWN * 1000L; // Cooldown in milliseconds
                long remainingTime = Math.max(0, (cooldownDuration - (currentTime - lastUsed)) / 1000L);

                if (remainingTime > 0) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(ChatColor.RED + "" + ChatColor.BOLD + "Cooldown: " + ChatColor.GREEN + remainingTime + " seconds"));
                } else {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(ChatColor.GREEN + "" + ChatColor.BOLD + "Ready To Use"));
                    cancelCooldownActionBar(player); // Stop the task when the cooldown ends
                }
            }
        };

        // Run the task every second and store it in the map
        task.runTaskTimer(plugin, 0L, 20L); // Update every second
        actionBarTasks.put(player.getUniqueId(), task);
    }


    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (isKhonshuMoonStaff(newItem)) {
            applyRegenerationEffect(player); // Apply Regeneration I when holding the staff
            playCustomSoundtrack(player); // Play custom soundtrack when switching to the staff

            // Start the cooldown action bar task only if the cooldown is still active
            startCooldownActionBar(player);
        } else {
            removeRegenerationEffect(player); // Remove Regeneration I when no longer holding the staff
            cancelCooldownActionBar(player);
        }

        // Prevent item switching if the staff is active (Custom Model Data 2)
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (isKhonshuMoonStaff(mainHandItem) && isCustomModelData(mainHandItem, CUSTOM_MODEL_DATA_2)) {
            event.setCancelled(true); // Prevent switching items
            player.sendMessage("You §4§LCANNOT §Fswitch items while the §4§lSTAFF IS ACTIVE!");
        }
    }

    private void applyRegenerationEffect(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, true, false));
    }

    private void removeRegenerationEffect(Player player) {
        player.removePotionEffect(PotionEffectType.REGENERATION);
    }

    private void cancelCooldownActionBar(Player player) {
        // Cancel the existing task for the player
        BukkitRunnable task = actionBarTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();

            // Prevent inventory interaction if holding the active staff in the main hand and clicking it
            if (clickedItem != null && clickedItem.equals(mainHandItem) && isKhonshuMoonStaff(mainHandItem) && isCustomModelData(mainHandItem, CUSTOM_MODEL_DATA_2)) {
                event.setCancelled(true);
            }

            // Prevent clicking the inactive staff in the inventory if it is in the main hand
            if (clickedItem != null && isKhonshuMoonStaff(clickedItem) && isCustomModelData(clickedItem, CUSTOM_MODEL_DATA_1)) {
                if (clickedItem.equals(mainHandItem)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        // Prevent swapping items if holding the active staff
        if (isKhonshuMoonStaff(mainHandItem) && isCustomModelData(mainHandItem, CUSTOM_MODEL_DATA_2)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        if (isKhonshuMoonStaff(droppedItem)) {
            event.setCancelled(true); // Prevent dropping the staff
            event.getPlayer().sendMessage("You cannot drop §8§lKhonshu’s Moon Staff!");
        }
    }

    private boolean isKhonshuMoonStaff(ItemStack item) {
        if (item != null && item.getType() == Material.DIAMOND_SWORD && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta.hasCustomModelData() && meta.getDisplayName().equals(STAFF_NAME);
        }
        return false;
    }

    private boolean isCustomModelData(ItemStack item, int customModelData) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta.hasCustomModelData() && meta.getCustomModelData() == customModelData;
        }
        return false;
    }
}