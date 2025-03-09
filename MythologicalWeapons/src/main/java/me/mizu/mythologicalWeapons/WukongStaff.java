package me.mizu.mythologicalWeapons;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

@SuppressWarnings("all")
public class WukongStaff implements Listener {
    private final MythologicalWeapons plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final HashMap<UUID, BukkitRunnable> actionBarTasks = new HashMap<>(); // Track active cooldown tasks
    private final HashMap<UUID, BukkitRunnable> enlargedActionBarTasks = new HashMap<>(); // Track "Enlarged" action bar tasks
    private final HashMap<UUID, Boolean> enlargedPlayers = new HashMap<>(); // Track players with enlarged staff
    private final HashMap<UUID, List<ItemDisplay>> earthquakeBlocks = new HashMap<>(); // Track ItemDisplay entities for earthquake
    private final int COOLDOWN; // Cooldown in seconds (configurable)
    private final String STAFF_NAME = ChatColor.YELLOW + "" + ChatColor.BOLD + "Wukong’s Staff";
    private final int CUSTOM_MODEL_DATA_NORMAL = 2; // Custom model data for normal staff
    private final int CUSTOM_MODEL_DATA_ENLARGED = 3; // Custom model data for enlarged staff
    private final int ENLARGEMENT_DURATION; // Duration of enlargement (configurable)

    public WukongStaff(MythologicalWeapons plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Load configuration values
        COOLDOWN = plugin.getConfig().getInt("wukong-staff.cooldown", 30); // Default: 30 seconds
        ENLARGEMENT_DURATION = plugin.getConfig().getInt("wukong-staff.enlargement-duration", 7); // Default: 7 seconds
    }

    public ItemStack getWukongStaff() {
        ItemStack staff = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(STAFF_NAME);
        meta.setCustomModelData(CUSTOM_MODEL_DATA_NORMAL);
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("wukong-staff.lore")) {
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

            if (isWukongStaff(item)) {
                long currentTime = System.currentTimeMillis();
                long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);

                if (currentTime - lastUsed >= COOLDOWN * 1000L) {
                    cooldowns.put(player.getUniqueId(), currentTime);

                    // Replace the staff with the enlarged version
                    enlargeStaff(player);
                    player.sendMessage("You enlarged the §e§lWukong Staff!");

                    // Play custom soundtrack and environmental effects
                    playCustomSoundtrack(player);
                    spawnEnvironmentalEffects(player.getLocation());

                    // Start the cooldown AFTER the enlargement duration ends
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                        }
                    }.runTaskLater(plugin, 20L * ENLARGEMENT_DURATION); // Start cooldown after enlargement ends
                } else {
                    player.sendMessage("Ability is on §4§lCooldown!");
                }
            }
        }
    }

    private void enlargeStaff(Player player) {
        ItemStack enlargedStaff = getEnlargedStaff();
        player.getInventory().setItemInMainHand(enlargedStaff);

        // Mark the player as having an enlarged staff
        enlargedPlayers.put(player.getUniqueId(), true);

        // Start the "Enlarged" action bar task
        startEnlargedActionBar(player);

        // Stop the cooldown action bar task while the staff is enlarged
        cancelCooldownActionBar(player);

        // Start the earthquake animation
        startEarthquakeAnimation(player);

        // Revert to the normal staff after the configured duration
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack normalStaff = getWukongStaff();
                player.getInventory().setItemInMainHand(normalStaff);
                player.sendMessage("The §e§lWukong Staff §fhas returned to normal size.");

                // Mark the player as no longer having an enlarged staff
                enlargedPlayers.remove(player.getUniqueId());

                // Stop the "Enlarged" action bar task
                stopEnlargedActionBar(player);

                // Resume the cooldown action bar task
                startCooldownActionBar(player);

                // Stop the earthquake animation
                stopEarthquakeAnimation(player);
            }
        }.runTaskLater(plugin, 20L * ENLARGEMENT_DURATION); // Revert after ENLARGEMENT_DURATION seconds
    }

    private ItemStack getEnlargedStaff() {
        ItemStack staff = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(STAFF_NAME + ChatColor.GOLD + " Enlarged");
        meta.setCustomModelData(CUSTOM_MODEL_DATA_ENLARGED);
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("wukong-staff.lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        staff.setItemMeta(meta);
        return staff;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            ItemStack item = player.getInventory().getItemInMainHand();

            // Check if the player is holding the Wukong Staff and it is enlarged
            if (isWukongStaff(item) && item.getItemMeta().getCustomModelData() == CUSTOM_MODEL_DATA_ENLARGED) {
                if (event.getEntity() instanceof LivingEntity) {
                    LivingEntity target = (LivingEntity) event.getEntity();

                    // Apply Slowness 4 for 7 seconds
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 7, 3, true, false));

                    // Play a custom hit sound
                    player.getWorld().playSound(target.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 1.0f);

                    // Spawn particles at the target's location
                    player.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);

                    // Apply 1 heart of true damage (2 damage points)
                    double originalHealth = target.getHealth();
                    double newHealth = Math.max(0, originalHealth - 2); // Subtract 2 health points (1 heart)
                    target.setHealth(newHealth);

                    // Optional: Send a message to the player confirming the true damage
                    player.sendMessage("You dealt §4§l1 heart §fof true damage to §4§l" + target.getName() + "!");
                }
            }
        }
    }

    private void spawnEnvironmentalEffects(Location location) {
        // Spawn particles around the player
        location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 20, 0.5, 0.5, 0.5, 0.1);

        // Play a custom sound effect
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
    }

    private void playCustomSoundtrack(Player player) {
        // Play a custom sound when the weapon is used or equipped
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0f, 0f);
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        // Start the cooldown action bar task if the player holds the Wukong Staff
        if (isWukongStaff(newItem)) {
            applySpeedEffect(player); // Apply Speed I when holding the staff
            playCustomSoundtrack(player); // Play custom soundtrack when switching to the staff
            startCooldownActionBar(player); // Start cooldown action bar task
        } else {
            // Cancel the action bar task if the player stops holding the staff
            removeSpeedEffect(player); // Remove Speed I when no longer holding the staff
            cancelCooldownActionBar(player);
        }
        // Check if the player has an enlarged staff
        if (enlargedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true); // Cancel the event
            player.sendMessage("You §4§lCannot §fSwitch items §4§lWhile §the staff is enlarged!");
        }
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();

            // Check if the player has an enlarged staff
            if (enlargedPlayers.containsKey(player.getUniqueId())) {
                event.setCancelled(true); // Cancel the event
                player.sendMessage("You §4§lCannot §fuse your inventory §4§lWhile the staff is enlarged!");
            }
        }{
            Player player = (Player) event.getWhoClicked();
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();

            if (isWukongStaff(mainHandItem)) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && isWukongStaff(clickedItem)) {
                    event.setCancelled(true); // Prevent moving the weapon in the inventory
                    player.sendMessage("You §4§lcannot Move §e§lWukong Staff §fin the inventory.");
                }
            }
        }
    }
    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // Check if the player has an enlarged staff
        if (enlargedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true); // Cancel the event
            player.sendMessage("You §4§lCannot §fswap items §4§lWhile §fthe staff is enlarged!");
        }
    }
    private void applySpeedEffect(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 0, true, false));
    }

    private void removeSpeedEffect(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    private void startCooldownActionBar(Player player) {
        // Cancel any existing task for the player
        cancelCooldownActionBar(player);

        // Do not start the cooldown task if the staff is enlarged
        if (enlargedPlayers.containsKey(player.getUniqueId())) {
            return;
        }

        // Create a new task to display the cooldown on the action bar
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isWukongStaff(player.getInventory().getItemInMainHand())) {
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
                }
            }
        };

        // Run the task every second and store it in the map
        task.runTaskTimer(plugin, 0L, 20L); // Update every second
        actionBarTasks.put(player.getUniqueId(), task);
    }

    private void cancelCooldownActionBar(Player player) {
        // Cancel the existing task for the player
        BukkitRunnable task = actionBarTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void startEnlargedActionBar(Player player) {
        // Cancel any existing "Enlarged" action bar task for the player
        stopEnlargedActionBar(player);

        // Create a new task to display "Enlarged" on the action bar
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isWukongStaff(player.getInventory().getItemInMainHand())) {
                    stopEnlargedActionBar(player); // Stop the task if the player stops holding the staff
                    return;
                }

                // Display "Enlarged" in bold yellow on the action bar
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        TextComponent.fromLegacyText(ChatColor.YELLOW + "" + ChatColor.BOLD + "Enlarged"));
            }
        };

        // Run the task every second and store it in the map
        task.runTaskTimer(plugin, 0L, 20L); // Update every second
        enlargedActionBarTasks.put(player.getUniqueId(), task);
    }

    private void stopEnlargedActionBar(Player player) {
        // Cancel the existing "Enlarged" action bar task for the player
        BukkitRunnable task = enlargedActionBarTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void startEarthquakeAnimation(Player player) {
        // Generate random blocks below the player and spawn ItemDisplay entities
        Location playerLocation = player.getLocation();
        World world = player.getWorld();
        Random random = new Random();

        List<ItemDisplay> blocks = new ArrayList<>();

        // Create a grid of blocks below the player
        int radius = 3; // Radius of the earthquake effect
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Block block = playerLocation.clone().add(x, -1, z).getBlock(); // Get the block below the player
                if (block.getType() != Material.AIR) {
                    // Spawn an ItemDisplay entity at the block's location
                    ItemDisplay itemDisplay = (ItemDisplay) world.spawnEntity(block.getLocation().add(0.5, 0, 0.5), EntityType.ITEM_DISPLAY);
                    itemDisplay.setItemStack(new ItemStack(block.getType())); // Set the block's material as the display item
                    itemDisplay.setTransformation(new Transformation(
                            new Vector3f(), // Translation (offset)
                            new AxisAngle4f(0, 0, 0, 1), // Rotation (none)
                            new Vector3f(1, 1, 1), // Scale (default size)
                            new AxisAngle4f(0, 0, 0, 1) // Additional rotation (none)
                    ));
                    blocks.add(itemDisplay);
                }
            }
        }

        // Store the ItemDisplay entities for later removal
        earthquakeBlocks.put(player.getUniqueId(), blocks);

        // Animate the blocks moving up and down
        new BukkitRunnable() {
            double offset = 0; // Current offset for the animation
            boolean goingUp = true; // Direction of the animation

            @Override
            public void run() {
                if (!enlargedPlayers.containsKey(player.getUniqueId())) {
                    // Smoothly return blocks to their original positions when enlargement ends
                    for (ItemDisplay itemDisplay : blocks) {
                        Location location = itemDisplay.getLocation();
                        location.setY(location.getY() - offset); // Reset Y position
                        itemDisplay.teleport(location); // Teleport the ItemDisplay to the original position
                        itemDisplay.remove(); // Remove the ItemDisplay entity
                    }
                    this.cancel(); // Stop the animation
                    return;
                }

                for (ItemDisplay itemDisplay : blocks) {
                    Location location = itemDisplay.getLocation();
                    if (goingUp) {
                        offset += 0.1; // Move up
                        if (offset >= 3.0) goingUp = false; // Reverse direction
                    } else {
                        offset -= 0.1; // Move down
                        if (offset <= 0) goingUp = true; // Reverse direction
                    }
                    location.setY(location.getY() + offset); // Update the Y position
                    itemDisplay.teleport(location); // Teleport the ItemDisplay to the new position
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Update every tick (0.05 seconds)
    }

    private void stopEarthquakeAnimation(Player player) {
        // Remove all ItemDisplay entities associated with the player
        List<ItemDisplay> blocks = earthquakeBlocks.remove(player.getUniqueId());
        if (blocks != null) {
            for (ItemDisplay itemDisplay : blocks) {
                itemDisplay.remove(); // Remove the ItemDisplay entity
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // Check if the player has an enlarged staff
        if (enlargedPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true); // Cancel the event
            player.sendMessage("You §4§lCannot §fdrop items §4§lWhile §fthe staff is enlarged!");
        }

        // Prevent dropping the Wukong Staff regardless of enlargement
        if (isWukongStaff(droppedItem)) {
            event.setCancelled(true);
            player.sendMessage("You cannot drop the §e§lWukong Staff!");
        }
    }

    private boolean isWukongStaff(ItemStack item) {
        if (item != null && item.getType() == Material.NETHERITE_SWORD && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            String displayName = meta.getDisplayName();
            return (meta.hasCustomModelData() &&
                    (meta.getCustomModelData() == CUSTOM_MODEL_DATA_NORMAL || meta.getCustomModelData() == CUSTOM_MODEL_DATA_ENLARGED) &&
                    (displayName.equals(STAFF_NAME) || displayName.equals(STAFF_NAME + ChatColor.GOLD + " Enlarged")));
        }
        return false;
    }
}