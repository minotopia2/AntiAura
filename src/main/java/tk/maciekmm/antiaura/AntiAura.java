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
import com.google.common.collect.ImmutableList;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
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
import org.bukkit.util.Vector;


public class AntiAura extends JavaPlugin implements Listener {
    private HashMap<UUID, AuraCheck> running = new HashMap<>();
    public static ImmutableList<Vector> POSITIONS;
    public int autoBanCount;
    private boolean isRegistered;
    private boolean silentBan;
    private int runEvery;
    public static final Random RANDOM = new Random();

    public void onEnable() {
        this.saveDefaultConfig();
        POSITIONS = getPositionsForAmount(this.getConfig().getInt("amountOfFakePlayers"));
        autoBanCount = this.getConfig().getInt("autoBanOnXPlayers");
        silentBan = this.getConfig().getBoolean("silentBan");
        runEvery = this.getConfig().getInt("runEvery");
        this.getServer().getPluginManager().registerEvents(this, this);
        
        if(this.getConfig().getBoolean("randomlyRun")) {
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

    public ImmutableList<Vector> getPositionsForAmount(int total) {
        ImmutableList.Builder<Vector> pos_temp = new ImmutableList.Builder<>();
        double per90deg = total / 4;
        double result = per90deg / (per90deg + 1);
        double currentX = 0;
        double currentY = 1;
        for (int i = 0; i <= per90deg; i++) {
            currentX += result;
            currentY -= result;
            pos_temp.add(new Vector(currentX,(double)0,currentY));
        }
        return pos_temp.build();
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

        AuraCheck check = new AuraCheck(this, player);
        running.put(player.getUniqueId(), check);

        check.invoke(sender, new AuraCheck.Callback() {
            @Override
            public void done(long started, long finished, AbstractMap.SimpleEntry<Integer, Integer> result, CommandSender invoker, Player player) {
                if (invoker instanceof Player && !((Player) invoker).isOnline()) {
                    return;
                }
                invoker.sendMessage(ChatColor.DARK_PURPLE + "Aura check on " + player.getName() + " result: killed " + result.getKey() + " out of " + result.getValue());
                double timeTaken = finished != Long.MAX_VALUE ? (int) ((finished - started) / 1000) : ((double) getConfig().getInt("ticksToKill", 10) / 20);
                invoker.sendMessage(ChatColor.DARK_PURPLE + "Check length: " + timeTaken + " seconds.");
                if(result.getKey() >= autoBanCount) {
                    Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), "ANTI-AURA: Hit ban limit", null, "AntiAura-AutoBan");
                    if(!silentBan) {
                        player.kickPlayer("ANTI-AURA: Hit limit reached");
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
