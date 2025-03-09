package me.mizu.mythologicalWeapons;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockIterator;

import java.util.*;

@SuppressWarnings("all")
public class OdinsGungnir implements Listener {
    private final MythologicalWeapons plugin;
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final int COOLDOWN = 30; // Cooldown in seconds
    private final String GUNGNIR_NAME = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Odin’s Gungnir";
    private final int CUSTOM_MODEL_DATA = 4; // Custom model data for the weapon
    private final int RANGE = 45; // Interaction range in blocks

    public OdinsGungnir(MythologicalWeapons plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ItemStack getOdinsGungnir() {
        ItemStack gungnir = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = gungnir.getItemMeta();
        meta.setDisplayName(GUNGNIR_NAME);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);

        // Set lore from config and translate & to §
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("odins-gungnir.lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        gungnir.setItemMeta(meta);
        return gungnir;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isOdinsGungnir(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            long currentTime = System.currentTimeMillis();
            long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);

            if (currentTime - lastUsed >= COOLDOWN * 1000L) {
                cooldowns.put(player.getUniqueId(), currentTime);

                // Find the target entity within range
                Entity target = getTargetEntity(player, RANGE);
                if (target instanceof LivingEntity) {
                    LivingEntity livingTarget = (LivingEntity) target;

                    // Summon lightning on the target
                    summonLightning(livingTarget.getLocation());
                    // Deal 2 hearts of true damage (4 health points)
                    double originalHealth = livingTarget.getHealth();
                    double newHealth = Math.max(0, originalHealth - 4); // Subtract 4 health points
                    livingTarget.setHealth(newHealth);

                    // Spawn additional lightning strikes nearby for dramatic effect
                    spawnNearbyLightnings(livingTarget.getLocation());

                    // Show an icon above the target's head
                    showTargetIcon(livingTarget);

                    // Play custom sound and particles
                    playCustomSoundtrack(player);
                    spawnEnvironmentalEffects(livingTarget.getLocation());

                    player.sendMessage("You struck §4§l" + livingTarget.getName() + " §fwith §3§lOdin’s Gungnir!");
                } else {
                    player.sendMessage("No §4§lvalid target §ffound within range!");
                }
            } else {
                player.sendMessage("Ability is on §4§lCooldown!");
            }
        }
    }

    private Entity getTargetEntity(Player player, double range) {
        BlockIterator iterator = new BlockIterator(player, (int) range);
        while (iterator.hasNext()) {
            Block block = iterator.next();
            for (Entity entity : block.getWorld().getNearbyEntities(block.getLocation(), 1, 1, 1)) {
                if (entity instanceof LivingEntity && entity != player) {
                    return entity;
                }
            }
        }
        return null;
    }

    private void summonLightning(Location location) {
        location.getWorld().strikeLightning(location);
    }

    private void spawnNearbyLightnings(Location location) {
        Random random = new Random();
        for (int i = 0; i < 5; i++) { // Spawn 5 additional lightning strikes
            double offsetX = random.nextDouble(-3, 3);
            double offsetZ = random.nextDouble(-3, 3);
            Location nearbyLocation = location.clone().add(offsetX, 0, offsetZ);
            nearbyLocation.getWorld().strikeLightning(nearbyLocation);
        }
    }

    private void showTargetIcon(LivingEntity target) {
        ArmorStand armorStand = (ArmorStand) target.getWorld().spawnEntity(target.getLocation().add(0, 2, 0), EntityType.ARMOR_STAND);
        armorStand.setMarker(true);
        armorStand.setInvisible(true);
        armorStand.setSmall(true);
        armorStand.setGravity(false);
        armorStand.setCustomName(ChatColor.YELLOW + "TARGETED");
        armorStand.setCustomNameVisible(true);

        // Remove the armor stand after 3 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                armorStand.remove();
            }
        }.runTaskLater(plugin, 60L); // 3 seconds (20 ticks per second * 3)
    }

    private void playCustomSoundtrack(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0f, 0f);
    }

    private void spawnEnvironmentalEffects(Location location) {
        location.getWorld().spawnParticle(Particle.FLASH, location, 10, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 30, 0.5, 0.5, 0.5, 0.1);
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        if (isOdinsGungnir(newItem)) {
            applyStrengthEffect(player); // Apply Strength I when holding the weapon
            playCustomSoundtrack(player); // Play custom soundtrack when switching to the weapon
            startCooldownActionBar(player); // Start cooldown action bar task
        } else {
            removeStrengthEffect(player); // Remove Strength I when no longer holding the weapon
        }
    }

    private void applyStrengthEffect(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, true, false));
    }

    private void removeStrengthEffect(Player player) {
        player.removePotionEffect(PotionEffectType.STRENGTH);
    }

    private void startCooldownActionBar(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isOdinsGungnir(player.getInventory().getItemInMainHand())) {
                    cancel();
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
        }.runTaskTimer(plugin, 0L, 20L); // Update every second
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (isOdinsGungnir(droppedItem)) {
            event.setCancelled(true); // Prevent dropping the weapon
            event.getPlayer().sendMessage("You cannot drop §3§lOdin’s Gungnir!");
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        if (isOdinsGungnir(mainHandItem)) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && isOdinsGungnir(clickedItem)) {
                event.setCancelled(true); // Prevent moving the weapon in the inventory
                player.sendMessage("You §4§lcannot Move §3§lOdin’s Gungnir §fin the inventory.");
            }
        }
    }

    private boolean isOdinsGungnir(ItemStack item) {
        if (item != null && item.getType() == Material.NETHERITE_SWORD && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta.hasCustomModelData() && meta.getCustomModelData() == CUSTOM_MODEL_DATA && meta.getDisplayName().equals(GUNGNIR_NAME);
        }
        return false;
    }
}