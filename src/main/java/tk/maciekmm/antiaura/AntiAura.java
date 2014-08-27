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
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;


public class AntiAura extends JavaPlugin implements Listener {
    private HashMap<UUID, AuraCheck> running = new HashMap<>();
    public static int total;
    private static int autoBanCount;
    private boolean isRegistered;
    private static boolean silentBan;
    private static int runEvery;
    private static String banMessage;
    private static String kickMessage;
    private String typeCmd;
    private String type;
    private String customCommand;
    private boolean customCommandToggle;
    public static final Random RANDOM = new Random();

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
        this.getServer().getPluginManager().registerEvents(this, this);
        
        if(type.equalsIgnoreCase("running") || type.equalsIgnoreCase("standing")) {
        } else {
            type = "running";
        }
        if(this.getConfig().getBoolean("settings.randomlyRun")) {
            Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                @Override
                public void run() {
                    if(Bukkit.getOnlinePlayers().length > 0) {
                        String player = org.bukkit.Bukkit.getOnlinePlayers()[RANDOM.nextInt(Bukkit.getOnlinePlayers().length)].getName();
                        org.bukkit.Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "auracheck " + player);
                    }
                }
            }, 800L, runEvery);
        }
    }

    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(
                new PacketAdapter(this, WrapperPlayClientUseEntity.TYPE) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        if (event.getPacketType() == WrapperPlayClientUseEntity.TYPE) {
                            int entID = new WrapperPlayClientUseEntity(event.getPacket()).getTargetID();
                            if (running.containsKey(event.getPlayer().getUniqueId())) {
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

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }

        Player player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage("Player is not online.");
            return true;
        }

        if (!isRegistered) {
            this.register();
        }
        
        if(args.length == 2) {
            if(args[1].equalsIgnoreCase("standing") || args[1].equalsIgnoreCase("running")) {
                typeCmd = args[1];
            } else {
                typeCmd = type;
            }
        } else {
            typeCmd = type;
        }

        AuraCheck check = new AuraCheck(this, player);
        running.put(player.getUniqueId(), check);

        check.invoke(sender, typeCmd, new AuraCheck.Callback() {
            @Override
            public void done(long started, long finished, AbstractMap.SimpleEntry<Integer, Integer> result, CommandSender invoker, Player player) {
                if (invoker instanceof Player && !((Player) invoker).isOnline()) {
                    return;
                }
                invoker.sendMessage(ChatColor.DARK_PURPLE + "Aura check on " + player.getName() + " result: killed " + result.getKey() + " out of " + result.getValue());
                double timeTaken = finished != Long.MAX_VALUE ? (int) ((finished - started) / 1000) : ((double) getConfig().getInt("settings.ticksToKill", 10) / 20);
                invoker.sendMessage(ChatColor.DARK_PURPLE + "Check length: " + timeTaken + " seconds.");
                if(result.getKey() >= autoBanCount) {
                    getLogger().log(Level.INFO, "Banning player {0} for going beyond AntiAura threshold.", player.getName());
                    if(!customCommandToggle) {
                        Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), banMessage, null, "AntiAura-AutoBan");
                    } else {
                        org.bukkit.Bukkit.dispatchCommand(Bukkit.getConsoleSender(), customCommand.replace("%player", player.getName()));
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
    public void onDisconnect(PlayerQuitEvent event) {
        AuraCheck check = this.remove(event.getPlayer().getUniqueId());
        if (check != null) {
            check.end();
        }
    }
}
