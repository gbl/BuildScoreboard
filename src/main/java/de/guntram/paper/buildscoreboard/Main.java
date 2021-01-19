package de.guntram.paper.buildscoreboard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public class Main extends JavaPlugin implements Listener {
    
    private Logger logger;
    private FileConfiguration config;

    private String worldName;
    private int minX, minY, minZ, maxX, maxY, maxZ;

    private Map<Player, Scoreboard> boards;
    private Map<String, Integer> placedBlocks;
    private long lastSavedTime;
    
    @Override
    public void onEnable() {
        
        FileConfiguration config;
        saveDefaultConfig();
        config = getConfig();
        
        this.worldName = config.getString("area.world", "world_nether");
        this.minX = config.getInt("area.minX", -100);
        this.maxX = config.getInt("area.maxX", 100);
        this.minY = config.getInt("area.minY", -100);
        this.maxY = config.getInt("area.maxY", 100);
        this.minZ = config.getInt("area.minZ", -100);
        this.maxZ = config.getInt("area.maxZ", 100);
        
        boards = new HashMap<>();
        placedBlocks = new HashMap<>();
        
        logger = this.getLogger();
        try {
            loadCounts();
        } catch (IOException ex) {
            logger.warning("Cannot read count file: "+ex.getMessage());
        }
        getServer().getPluginManager().registerEvents(this, this);
        logger.info("BuildScoreboard checking "+worldName+" from "+minX+"/"+minY+"/"+minZ+" to "+maxX+"/"+maxY+"/"+maxZ);
    }
    
    @Override
    public void onDisable() {
        lastSavedTime = 0;
        saveCounts();
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (isInArea(event.getPlayer().getLocation())) {
            createPlayerScoreboard(event.getPlayer());
        }
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        boards.remove(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        logger.log(Level.FINE, "world is "+event.getTo().getWorld().getName());
        logger.log(Level.FINE, "Location: "+event.getTo());
        if (isInArea(event.getTo())) {
            logger.log(Level.FINER, "In area");
            if (!boards.containsKey(event.getPlayer())) {
                createPlayerScoreboard(event.getPlayer());
            }
        } else {
            logger.log(Level.FINER, "NOT In area");
            if (boards.containsKey(event.getPlayer())) {
                logger.log(Level.FINE, "clearing, world ="+event.getTo().getWorld().getName());
                event.getPlayer().getScoreboard().clearSlot(DisplaySlot.SIDEBAR);
                boards.remove(event.getPlayer());
            }
        }
    }
    
    private void createPlayerScoreboard(Player player) {
        logger.log(Level.FINE, "setting");
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        popuplateScoreboard(board);
        player.setScoreboard(board);
        boards.put(player, board);
    }
    
    @EventHandler
    public void onBlockPlaced(BlockPlaceEvent event) {
        if (isInArea(event.getBlock().getLocation())) {
            String name = event.getPlayer().getDisplayName();
            int total;
            if (placedBlocks.containsKey(name)) {
                placedBlocks.put(name, total = placedBlocks.get(name)+1);
            } else {
                placedBlocks.put(name, total = 1);
            }
            for (Player player: boards.keySet()) {
                try {
                    player.getScoreboard().getObjective("Blocks placed").getScore(name).setScore(total);
                } catch (NullPointerException ex) {
                    logger.log(Level.INFO, "Can''t update score for {0}", player.getDisplayName());
                }
            }
            saveCounts();
        }
    }
    
    private boolean isInArea(Location location) {
        return location.getWorld().getName().equals(worldName)
        &&  location.getBlockX() > this.minX && location.getBlockX() < this.maxX
        &&  location.getBlockY() > this.minY && location.getBlockY() < this.maxY
        &&  location.getBlockZ() > this.minZ && location.getBlockZ() < this.maxZ;
    }
    
    private void popuplateScoreboard(Scoreboard board) {
        Objective objective = board.registerNewObjective("Blocks placed", "dummy");
        objective.setDisplayName("Blocks placed");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Map.Entry<String, Integer> entry: placedBlocks.entrySet()) {
            Score score = objective.getScore(entry.getKey());
            score.setScore(entry.getValue());
        }
    }
    
    private void loadCounts() throws IOException {
        File file = getDataFile();
        placedBlocks.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    try {
                        placedBlocks.put(parts[0], Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ex) {
                        logger.warning("Bad number in line "+line);
                    }
                } else {
                    logger.warning("Bad line syntax "+line);
                }
            }
        }
    }
    
    private void saveCounts() {
        if (lastSavedTime + 10000 > System.currentTimeMillis())
            return;
        lastSavedTime = System.currentTimeMillis();
        try {
            File file = getDataFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                for (Map.Entry<String, Integer> entry: placedBlocks.entrySet()) {
                    writer.println(entry.getKey()+"="+entry.getValue());
                }
            }
        } catch (IOException ex) {
            logger.warning("Can't save block placement counts: "+ex.getMessage());
        }
    }
                
    private File getDataFile() {
        return new File(this.getDataFolder(), "counts.txt");
    }
}
