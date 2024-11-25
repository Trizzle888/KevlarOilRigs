package kevlar.kevlarOilRigs;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.inventory.Inventory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.block.BlockFace;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.block.Biome;

public class KevlarOilRigs extends JavaPlugin implements Listener {
    private Map<Location, BukkitTask> activeOilRigs = new HashMap<>();
    private ItemStack oilBucket;
    private int generationDelay;
    private List<String> allowedBiomes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        generationDelay = getConfig().getInt("generation-delay", 60);
        allowedBiomes = getConfig().getStringList("allowed-biomes");
        createOilBucket();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("KevlarOilRigs enabled! Generation delay: " + generationDelay + " seconds");
        getLogger().info("Allowed biomes: " + String.join(", ", allowedBiomes));
    }

    @Override
    public void onDisable() {
        // Cancel all running tasks
        for (BukkitTask task : activeOilRigs.values()) {
            task.cancel();
        }
        activeOilRigs.clear();
        getLogger().info("KevlarOilRigs disabled!");
    }

    private void createOilBucket() {
        oilBucket = new ItemStack(Material.LAVA_BUCKET);
        ItemMeta meta = oilBucket.getItemMeta();
        meta.setDisplayName("§6Bucket of Oil");
        meta.setLore(List.of("§7A bucket filled with crude oil", "§cCannot be placed"));
        oilBucket.setItemMeta(meta);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlock();
        if (placedBlock.getType() == Material.DIAMOND_BLOCK) {
            checkAndRegisterOilRig(placedBlock, event);
        }
    }

    private void checkAndRegisterOilRig(Block placedBlock, BlockPlaceEvent event) {
        if (isValidOilRig(placedBlock)) {
            Location hopperLoc = placedBlock.getLocation().clone().subtract(0, 3, 0);

            // Check if this oil rig is already registered
            if (activeOilRigs.containsKey(hopperLoc)) {
                return;
            }

            // Start the oil generation task for this rig
            BukkitTask task = startOilGeneration(hopperLoc);
            activeOilRigs.put(hopperLoc, task);

            event.getPlayer().sendMessage("§2Oil Rig Created.");
            getLogger().info("New oil rig registered at: " + hopperLoc);
        }
    }

    private boolean isValidOilRig(Block topBlock) {
        // Must be diamond block on top
        if (topBlock.getType() != Material.DIAMOND_BLOCK) return false;

        // Check structure from top to bottom
        Block secondDiamond = topBlock.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (secondDiamond.getType() != Material.DIAMOND_BLOCK) return false;

        Block coal = secondDiamond.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (coal.getType() != Material.COAL_BLOCK) return false;

        Block hopper = coal.getLocation().clone().subtract(0, 1, 0).getBlock();
        if (!(hopper.getState() instanceof org.bukkit.block.Hopper)) return false;

        // Check if in ocean biome
        Biome biome = hopper.getBiome();
        if (!isOceanBiome(biome)) {
            return false;
        }

        // Check if on water
        Block belowHopper = hopper.getLocation().clone().subtract(0, 1, 0).getBlock();
        return belowHopper.getType() == Material.WATER;
    }

    private boolean isOceanBiome(Biome biome) {
        return allowedBiomes.contains(biome.name());
    }

    @EventHandler
    public void onInventoryMove(InventoryMoveItemEvent event) {
        // Check if source is a hopper
        if (event.getSource().getHolder() instanceof org.bukkit.block.Hopper) {
            org.bukkit.block.Hopper sourceHopper = (org.bukkit.block.Hopper) event.getSource().getHolder();
            Location hopperLoc = sourceHopper.getLocation();

            // Check if this hopper is part of an oil rig
            if (activeOilRigs.containsKey(hopperLoc)) {
                // Cancel the transfer
                event.setCancelled(true);
            }
        }
    }

    private BukkitTask startOilGeneration(Location hopperLoc) {
        return getServer().getScheduler().runTaskTimer(this, () -> {
            // Check if the structure is still valid
            if (!isStructureIntact(hopperLoc)) {
                BukkitTask task = activeOilRigs.remove(hopperLoc);
                if (task != null) {
                    task.cancel();
                }
                getLogger().info("Oil rig at " + hopperLoc + " was destroyed or modified.");
                return;
            }

            // Try to add oil bucket to hopper
            Block block = hopperLoc.getBlock();
            if (block.getState() instanceof Hopper) {
                Hopper hopper = (Hopper) block.getState();
                Inventory inv = hopper.getInventory();

                // Check if hopper is full
                if (inv.firstEmpty() == -1) {
                    getLogger().info("Hopper is full at: " + hopperLoc + ". Waiting for space...");
                    return; // Skip this cycle, but keep the task running
                }

                // Add oil bucket since there's space
                inv.addItem(oilBucket.clone());
                getLogger().info("Generated oil bucket at: " + hopperLoc);
            }
        }, 20L * generationDelay, 20L * generationDelay); // Convert seconds to ticks
    }

    private boolean isStructureIntact(Location hopperLoc) {
        Block hopper = hopperLoc.getBlock();
        if (!(hopper.getState() instanceof org.bukkit.block.Hopper)) return false;

        // Check if still in ocean and on water
        if (!isOceanBiome(hopper.getBiome())) return false;
        if (hopper.getLocation().clone().subtract(0, 1, 0).getBlock().getType() != Material.WATER) return false;

        Block coal = hopper.getLocation().clone().add(0, 1, 0).getBlock();
        if (coal.getType() != Material.COAL_BLOCK) return false;

        Block firstDiamond = coal.getLocation().clone().add(0, 1, 0).getBlock();
        if (firstDiamond.getType() != Material.DIAMOND_BLOCK) return false;

        Block secondDiamond = firstDiamond.getLocation().clone().add(0, 1, 0).getBlock();
        return secondDiamond.getType() == Material.DIAMOND_BLOCK;
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().equals("§6Bucket of Oil")) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("kevlaroilrigs")) {
            return false;
        }

        if (!sender.hasPermission("kevlaroilrigs.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§c/kevlaroilrigs <reload|forcedeposit>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "forcedeposit":
                handleForceDeposit(sender);
                break;
            default:
                sender.sendMessage("§cUnknown command. Use /kevlaroilrigs <reload|forcedeposit>");
        }
        return true;
    }

    private void handleReload(CommandSender sender) {
        // Cancel all existing tasks
        for (BukkitTask task : activeOilRigs.values()) {
            task.cancel();
        }
        activeOilRigs.clear();

        // Reload config
        reloadConfig();
        generationDelay = getConfig().getInt("generation-delay", 60);
        allowedBiomes = getConfig().getStringList("allowed-biomes");
        createOilBucket();

        // Scan for existing valid oil rigs and restart their tasks
        if (sender instanceof Player) {
            Player player = (Player) sender;
            for (int x = -30; x <= 30; x++) {
                for (int y = -30; y <= 30; y++) {
                    for (int z = -30; z <= 30; z++) {
                        Location loc = player.getLocation().clone().add(x, y, z);
                        Block block = loc.getBlock();
                        if (block.getState() instanceof Hopper) {
                            Block above = block.getLocation().clone().add(0, 3, 0).getBlock();
                            if (isValidOilRig(above)) {
                                BukkitTask task = startOilGeneration(block.getLocation());
                                activeOilRigs.put(block.getLocation(), task);
                            }
                        }
                    }
                }
            }
        }

        sender.sendMessage("§aKevlarOilRigs configuration reloaded!");
        getLogger().info("Plugin reloaded. New generation delay: " + generationDelay + " seconds");
    }

    private void handleForceDeposit(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players!");
            return;
        }

        Player player = (Player) sender;
        int rigsAffected = 0;

        // Check all oil rigs within 30 blocks
        for (Location rigLoc : activeOilRigs.keySet()) {
            if (rigLoc.getWorld().equals(player.getWorld()) &&
                    rigLoc.distance(player.getLocation()) <= 30) {

                Block block = rigLoc.getBlock();
                if (block.getState() instanceof Hopper) {
                    Hopper hopper = (Hopper) block.getState();
                    Inventory inv = hopper.getInventory();

                    if (inv.firstEmpty() != -1) {
                        inv.addItem(oilBucket.clone());
                        rigsAffected++;
                    }
                }
            }
        }

        if (rigsAffected > 0) {
            sender.sendMessage("§aForced oil deposit in " + rigsAffected + " oil rig(s)!");
        } else {
            sender.sendMessage("§cNo valid oil rigs found within 30 blocks!");
        }
    }
}