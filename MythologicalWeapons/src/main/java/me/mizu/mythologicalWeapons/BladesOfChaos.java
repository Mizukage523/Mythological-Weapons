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
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

@SuppressWarnings("all")
public class BladesOfChaos implements Listener {
    private final MythologicalWeapons plugin; // Reference to the main plugin
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private final int COOLDOWN = 30; // Cooldown in seconds
    private final String BLADES_NAME = ChatColor.DARK_RED + "" + ChatColor.BOLD + "Blades of Chaos";
    private final int CUSTOM_MODEL_DATA = 1;
    private final double THROW_SPEED = 1.5; // Speed of the thrown weapon
    private final double PULL_RANGE = 20.0; // Range for pulling the target

    public BladesOfChaos(MythologicalWeapons plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ItemStack getBladesOfChaos() {
        ItemStack blade = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = blade.getItemMeta();
        meta.setDisplayName(BLADES_NAME);
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        List<String> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("blades-of-chaos.lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line)); // Translate & to §
        }
        meta.setLore(lore);
        blade.setItemMeta(meta);
        return blade;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isBladesOfChaos(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            long currentTime = System.currentTimeMillis();
            long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (currentTime - lastUsed >= COOLDOWN * 1000L) {
                cooldowns.put(player.getUniqueId(), currentTime);
                throwBlade(player);
                player.sendMessage("You threw §4§lThe Blades of Chaos!");
                spawnEnvironmentalEffects(player.getLocation());
                playCustomSoundtrack(player);
                startCooldownActionBar(player);
            } else {
                player.sendMessage("Ability is on §4§lcooldown!");
            }
        }
    }

    private void throwBlade(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        Entity thrownBlade = player.launchProjectile(Trident.class);
        thrownBlade.setVelocity(player.getLocation().getDirection().multiply(THROW_SPEED));
        thrownBlade.setMetadata("BladeOwner", new org.bukkit.metadata.FixedMetadataValue(plugin, player.getUniqueId()));
        thrownBlade.setMetadata("BladeItem", new org.bukkit.metadata.FixedMetadataValue(plugin, mainHand));
        trackTrident(player, thrownBlade, mainHand);
    }

    private void trackTrident(Player player, Entity trident, ItemStack blade) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (trident == null || trident.isDead() || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                double distance = player.getLocation().distance(trident.getLocation());
                if (distance > 10.0) {
                    player.getInventory().setItemInMainHand(blade);
                    player.getInventory().setItemInOffHand(blade.clone());
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
                    player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
                    player.sendMessage("§4§lThe Blades of Chaos §fhave returned!");
                    trident.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Trident && event.getEntity().hasMetadata("BladeOwner")) {
            Trident trident = (Trident) event.getEntity();
            UUID ownerUUID = (UUID) trident.getMetadata("BladeOwner").get(0).value();
            Player owner = Bukkit.getPlayer(ownerUUID);
            if (owner != null) {
                ItemStack blade = (ItemStack) trident.getMetadata("BladeItem").get(0).value();
                owner.getInventory().setItemInMainHand(blade);
                Location hitLocation = trident.getLocation();
                double pullRadius = 10.0;
                for (Entity nearbyEntity : hitLocation.getWorld().getNearbyEntities(hitLocation, pullRadius, pullRadius, pullRadius)) {
                    if (nearbyEntity instanceof LivingEntity && nearbyEntity != owner) {
                        LivingEntity target = (LivingEntity) nearbyEntity;
                        summonHorizontalChains(owner, target);
                        Vector direction = owner.getLocation().toVector().subtract(target.getLocation().toVector()).normalize();
                        target.setVelocity(direction.multiply(1.5));
                        target.setFireTicks(100);
                        target.damage(4);
                        owner.getWorld().playSound(target.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.0f, 1.0f);
                        owner.getWorld().spawnParticle(Particle.FLAME, target.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                owner.getWorld().playSound(owner.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.0f);
                owner.getWorld().spawnParticle(Particle.LARGE_SMOKE, owner.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
                owner.sendMessage("§4§lThe Blades of Chaos §fhave returned!");
            }
            trident.remove();
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (isBladesOfChaos(item)) {
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.LAVA, player.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
            playCustomSoundtrack(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, true, false));
            ItemStack offhandItem = player.getInventory().getItemInOffHand();
            if (offhandItem == null || offhandItem.getType() == Material.AIR) {
                player.getInventory().setItemInOffHand(item.clone());
            }
            startCooldownActionBar(player);
        } else {
            player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
            ItemStack offhandItem = player.getInventory().getItemInOffHand();
            if (isBladesOfChaos(offhandItem)) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();
            ItemStack clickedItem = event.getCurrentItem();
            if (isBladesOfChaos(mainHandItem) && clickedItem != null && isBladesOfChaos(clickedItem)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (isBladesOfChaos(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player killer = (Player) event.getDamager();
            ItemStack item = killer.getInventory().getItemInMainHand();
            if (isBladesOfChaos(item)) {
                if (event.getFinalDamage() >= ((LivingEntity) event.getEntity()).getHealth()) {
                    playCustomDeathAnimation((LivingEntity) event.getEntity());
                    killer.getWorld().playSound(killer.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
                } else {
                    killer.getWorld().playSound(killer.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (isBladesOfChaos(droppedItem)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("You cannot drop §4§lThe Blades of Chaos!");
        }
    }

    private void spawnEnvironmentalEffects(Location location) {
        location.getWorld().spawnParticle(Particle.FLAME, location, 30, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().spawnParticle(Particle.DRIPPING_LAVA, location, 10, 0.5, 0.5, 0.5, 0.1);
        location.getWorld().playSound(location, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.0f);
        List<Location> fireBlocks = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block block = location.clone().add(x, 0, z).getBlock();
                if (block.getType() == Material.AIR) {
                    block.setType(Material.FIRE);
                    fireBlocks.add(block.getLocation());
                }
            }
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Location fireLocation : fireBlocks) {
                    Block block = fireLocation.getBlock();
                    if (block.getType() == Material.FIRE) {
                        block.setType(Material.AIR);
                        block.getWorld().spawnParticle(Particle.LARGE_SMOKE, block.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                    }
                }
            }
        }.runTaskLater(plugin, 100L); // Revert after 5 seconds
    }

    private void playCustomSoundtrack(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0f, 0f);
    }

    private void playCustomDeathAnimation(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, entity.getLocation(), 1);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 1.0f);
        entity.setFireTicks(100);
        entity.getWorld().spawnParticle(Particle.LARGE_SMOKE, entity.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
        new BukkitRunnable() {
            @Override
            public void run() {
                entity.remove();
            }
        }.runTaskLater(plugin, 20L);
    }

    private void startCooldownActionBar(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isBladesOfChaos(player.getInventory().getItemInMainHand())) {
                    this.cancel();
                    return;
                }
                long currentTime = System.currentTimeMillis();
                long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);
                long cooldownDuration = plugin.getConfig().getInt("blades-of-chaos.cooldown", 30) * 1000L;
                long remainingTime = Math.max(0, (cooldownDuration - (currentTime - lastUsed)) / 1000L);
                if (remainingTime > 0) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(ChatColor.RED + "" + ChatColor.BOLD + "Cooldown: " + ChatColor.GREEN + remainingTime + " seconds"));
                } else {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(ChatColor.GREEN + "" + ChatColor.BOLD + "Ready To Use"));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean isBladesOfChaos(ItemStack item) {
        if (item != null && item.getType() == Material.NETHERITE_SWORD && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            return meta.hasCustomModelData() && meta.getCustomModelData() == CUSTOM_MODEL_DATA && meta.getDisplayName().equals(BLADES_NAME);
        }
        return false;
    }

    private void summonHorizontalChains(Player player, LivingEntity target) {
        Location playerLocation = player.getLocation();
        Location targetLocation = target.getLocation();
        Vector direction = playerLocation.toVector().subtract(targetLocation.toVector()).normalize();
        int chainLength = plugin.getConfig().getInt("blades-of-chaos.chain-length", 9);
        double spacing = plugin.getConfig().getDouble("blades-of-chaos.chain-spacing", 1.0);
        List<ItemDisplay> chainDisplays = new ArrayList<>();
        for (int i = 0; i < chainLength; i++) {
            Location chainLocation = targetLocation.clone().add(direction.clone().multiply(i * spacing));
            chainLocation.setY(chainLocation.getY() + 1);
            ItemDisplay chainDisplay = (ItemDisplay) chainLocation.getWorld().spawnEntity(chainLocation, EntityType.ITEM_DISPLAY);
            chainDisplay.setItemStack(new ItemStack(Material.CHAIN));
            chainDisplay.setTransformation(new Transformation(
                    new Vector3f(),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
            chainDisplay.setInterpolationDuration(0);
            chainDisplay.setInterpolationDelay(0);
            chainDisplay.setRotation(playerLocation.getYaw(), 0);
            chainDisplays.add(chainDisplay);
        }
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index < chainDisplays.size()) {
                    chainDisplays.get(index).remove();
                    index++;
                } else {
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}