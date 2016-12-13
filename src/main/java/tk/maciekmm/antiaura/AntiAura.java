/*
 * Copyright (C) 2014 Maciej Mionskowski
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package tk.maciekmm.antiaura;

import com.comphenix.packetwrapper.WrapperPlayClientUseEntity;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;


public class AntiAura extends JavaPlugin implements Listener {

    private static final NumberFormat NUMBER_FORMAT;

    static {
        NUMBER_FORMAT = NumberFormat.getInstance();
        NUMBER_FORMAT.setMaximumIntegerDigits(Integer.MAX_VALUE);
        NUMBER_FORMAT.setMinimumIntegerDigits(1);
        NUMBER_FORMAT.setMaximumFractionDigits(2);
        NUMBER_FORMAT.setMinimumFractionDigits(1);
    }

    private HashMap<UUID, AuraCheck> running = new HashMap<>();
    private HashMap<UUID, Long> lastHit = new HashMap<>();
    private boolean notifyPermission;
    private boolean randomCheckOnFight;
    private boolean worldPvpCheck;
    private boolean iCanHasPVP = false;
    private boolean isRegistered;
    private boolean customCommandToggle;
    private boolean visOrInvisible;
    private boolean visCmd;
    private static boolean silentBan;
    private String typeCmd;
    private String type;
    private String customCommand;
    private static String banMessage;
    private static String kickMessage;
    private int minToAutoRun;
    private int count = 0;
    private int maxToCheck;
    private int fightTimeDelay;
    private static int runEvery;
    public static int total;
    private static int autoBanCount;
    private long fightTimeDelayTrue;
    public static final Random RANDOM = new Random();

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        total = this.getConfig().getInt("settings.amountOfFakePlayers", 16);
        autoBanCount = this.getConfig().getInt("settings.autoBanOnXPlayers", 3);
        silentBan = this.getConfig().getBoolean("settings.silentBan", true);
        runEvery = this.getConfig().getInt("settings.runEvery", 2400);
        banMessage = this.getConfig().getString("messages.banMessage", "ANTI-AURA: Passed threshold");
        kickMessage = this.getConfig().getString("messages.kickMessage", "ANTI-AURA: Passed threshold");
        type = this.getConfig().getString("settings.defaultType", "running");
        customCommandToggle = this.getConfig().getBoolean("customBanCommand.enable", false);
        customCommand = this.getConfig().getString("customBanCommand.command", "ban %player");
        visOrInvisible = this.getConfig().getBoolean("settings.invisibility", false);
        minToAutoRun = this.getConfig().getInt("settings.min-players-to-autorun", 5);
        worldPvpCheck = this.getConfig().getBoolean("player-checks.world", true);
        maxToCheck = this.getConfig().getInt("player-checks.max-to-check", 10);
        notifyPermission = this.getConfig().getBoolean("settings.notify-users-with-permission", false);
        randomCheckOnFight = this.getConfig().getBoolean("player-checks.on-pvp.enabled", true);
        fightTimeDelay = this.getConfig().getInt("player-checks.on-pvp.time-delay", 60);

        fightTimeDelayTrue = fightTimeDelay * 1000L;

        this.getServer().getPluginManager().registerEvents(this, this);

        if(!(type.equalsIgnoreCase("running") || type.equalsIgnoreCase("standing"))) {
            type = "running";
        }

        if(this.getConfig().getBoolean("settings.randomlyRun")) {
            Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                @Override
                public void run() {
                    if(Bukkit.getOnlinePlayers().length > minToAutoRun) {
                        if(worldPvpCheck) {
                            findPlayerWorld();
                        } else {
                            checkExecute(getRandomPlayer().getName());
                        }
                    }
                }
            }, 800L, runEvery);
        }
    }

    public Player getRandomPlayer() {
        return org.bukkit.Bukkit.getOnlinePlayers()[RANDOM.nextInt(Bukkit.getOnlinePlayers().length)];
    }

    public void findPlayerWorld() {
        while(!iCanHasPVP) {
            if(count > maxToCheck) {
                count = 0;
                break;
            }
            Player player = getRandomPlayer();
            count++;
            if(player.getWorld().getPVP()) {
                iCanHasPVP = true;
                checkExecute(player.getName());
            }
        }
        iCanHasPVP = false;
    }

    public void checkExecute(String player) {
        org.bukkit.Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "auracheck " + player);
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(this, WrapperPlayClientUseEntity.TYPE) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        if (event.getPacketType() == WrapperPlayClientUseEntity.TYPE) {
                            WrapperPlayClientUseEntity packet = new WrapperPlayClientUseEntity(event.getPacket());
                            int entID = packet.getTargetID();
                            if (running.containsKey(event.getPlayer().getUniqueId()) && packet.getType() == EntityUseAction.ATTACK) {
                                running.get(event.getPlayer().getUniqueId()).markAsKilled(entID);
                            }
                        }
                    }

                });
        this.isRegistered = true;
    }

    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListeners(this);
        this.isRegistered = false;
    }

    public AuraCheck remove(UUID id) {
        if (this.running.containsKey(id)) {

            if (running.size() == 1) {
                this.unregister();
            }

            return this.running.remove(id);
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            this.reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "AntiAura config successfully reloaded");
            return true;
        }

        @SuppressWarnings("deprecation")
        List<Player> playerList = Bukkit.matchPlayer(args[0]);
        Player player;
        if (playerList.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Player is not online.");
            return true;
        } else if (playerList.size() == 1) {
            player = playerList.get(0);
        } else {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("[\"\",{\"text\":\"What player do you mean? (click one)\\n\",\"color\":\"green\"},");
            for (Player p : playerList) {
                stringBuilder.append("{\"text\":\"").append(p.getName()).append(", \",\"color\":\"blue\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/auracheck ").append(p.getName()).append("\"},\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"\",\"extra\":[{\"text\":\"").append(p.getName()).append("\",\"color\":\"dark_purple\"}]}}},");
            }
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            stringBuilder.append("]");
            String json = stringBuilder.toString();
            PacketContainer packet = new PacketContainer(PacketType.Play.Server.CHAT);
            packet.getChatComponents().write(0, WrappedChatComponent.fromJson(json));
            try {
                ProtocolLibrary.getProtocolManager().sendServerPacket((Player) sender, packet);
            } catch (InvocationTargetException e) {
            }
            return true;
        }
        if (player == null) {
            sender.sendMessage(ChatColor.RED + "Player is not online.");
            return true;
        }

        if (!isRegistered) {
            this.register();
        }

        if(args.length >= 2) {
            if(args[1].equalsIgnoreCase("standing") || args[1].equalsIgnoreCase("running")) {
                typeCmd = args[1];
            } else {
                typeCmd = type;
            }
        } else {
            typeCmd = type;
        }

        if(args.length >= 3) {
            if(args[2].equalsIgnoreCase("visible") || args[2].equalsIgnoreCase("invisible")) {
                if(args[2].equalsIgnoreCase("visible")) {
                    visCmd = false;
                } else {
                    visCmd = true;
                }
            } else {
                visCmd = visOrInvisible;
            }
        } else {
            visCmd = visOrInvisible;
        }

        AuraCheck check = new AuraCheck(this, player);
        running.put(player.getUniqueId(), check);

        check.invoke(sender, typeCmd, visCmd, new AuraCheck.Callback() {
            @Override
            public void done(long started, long finished, AbstractMap.SimpleEntry<Integer, Integer> result, CommandSender invoker, Player player) {
                if (invoker instanceof Player && !((Player) invoker).isOnline()) {
                    return;
                }
                invoker.sendMessage(ChatColor.DARK_PURPLE + "Aura check on " + player.getName() + " result: killed " + result.getKey() + " out of " + result.getValue());
                double timeTaken = finished != Long.MAX_VALUE ? ((double) (finished - started) / 1000D) : ((double) getConfig().getInt("settings.ticksToKill", 10) / 20D);
                invoker.sendMessage(ChatColor.DARK_PURPLE + "Check length: " + timeTaken + " seconds.");
                if(result.getKey() >= autoBanCount) {
                    if(notifyPermission) {
                        Bukkit.broadcast("[AntiAura] Banning player " + player.getName() + "for going beyond AntiAura threshold.", "auracheck.check");
                    }
                    getLogger().log(Level.INFO, "Banning player {0} for going beyond AntiAura threshold.", player.getName());
                    if(!customCommandToggle) {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), banMessage, null, "AntiAura-AutoBan");
                    } else {
                        String disposableCommand = customCommand;
                        if(customCommand.contains("%player")) {
                            disposableCommand = disposableCommand.replace("%player", player.getName());
                        }
                        if(customCommand.contains("%count")) {
                            disposableCommand = disposableCommand.replace("%count", result.getKey() + "");
                        }
                        org.bukkit.Bukkit.dispatchCommand(Bukkit.getConsoleSender(), disposableCommand);
                    }
                    if(!silentBan && !customCommandToggle) {
                        player.kickPlayer(kickMessage);
                    }
                }
            }
        });
        return true;
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if(randomCheckOnFight) {
            if(event.getDamager().getType() == EntityType.PLAYER && event.getEntity().getType() == EntityType.PLAYER) {
                if(lastHit.containsKey(event.getDamager().getUniqueId())) {
                    if((lastHit.get(event.getDamager().getUniqueId()) + fightTimeDelayTrue) < System.currentTimeMillis()) {
                        final String name = ((Player)event.getDamager()).getName();
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                checkExecute(name);
                            }
                        }.runTaskLater(this, 60L); // 3 seconds after fight starts
                    }
                } else {
                    lastHit.put(event.getDamager().getUniqueId(), System.currentTimeMillis());
                }
            }
        }
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent event) {
        AuraCheck check = this.remove(event.getPlayer().getUniqueId());
        if (check != null) {
            check.end();
        }

        if(lastHit.containsKey(event.getPlayer().getUniqueId())) {
            lastHit.remove(event.getPlayer().getUniqueId());
        }
    }
}
