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

import com.comphenix.packetwrapper.WrapperPlayServerEntityDestroy;
import com.comphenix.packetwrapper.WrapperPlayServerNamedEntitySpawn;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class AuraCheck {
    public static final short[][] standing = {{0,1},{-1,0},{1,0},{0,-1},{1,1},{-1,-1},{-1,1},{1,-1}};
    public static final short[][] running = {{0,2},{-2,0},{2,0},{0,-2},{1,1},{2,2},{-1,-1},{-2,-2},{-1,1},{-2,2},{1,-1},{2,-2}};

    private final AntiAura plugin;
    private HashMap<Integer, Boolean> entitiesSpawned = new HashMap<>();
    private CommandSender invoker;
    private Player checked;
    private long started;
    private long finished = Long.MAX_VALUE;
    private int i = -1;
    private int z;


    public AuraCheck(AntiAura plugin, Player checked) {
        this.plugin = plugin;
        this.checked = checked;
    }

    public void invoke(CommandSender player, String type, final Callback callback) {
        this.invoker = player;
        this.started = System.currentTimeMillis();
        
        if(type.equalsIgnoreCase("running")) {
            while(z < AntiAura.total) {
                z++;
                i++;
                if(i == 12) {
                    i = 0;
                }
                WrapperPlayServerNamedEntitySpawn wrapper = getWrapper(this.checked.getLocation().add(running[i][0],0,running[i][1]).toVector(), plugin);
                entitiesSpawned.put(wrapper.getEntityID(), false);
                wrapper.sendPacket(this.checked);
            }
        } else {
            while(z < AntiAura.total) {
                z++;
                i++;
                if(i == 7) {
                    i = 0;
                }
                WrapperPlayServerNamedEntitySpawn wrapper = getWrapper(this.checked.getLocation().add(standing[i][0],0,standing[i][1]).toVector(), plugin);
                entitiesSpawned.put(wrapper.getEntityID(), false);
                wrapper.sendPacket(this.checked);
            }
        }
        i = -1;

        Bukkit.getScheduler().runTaskLater(this.plugin, new Runnable() {
            @Override
            public void run() {
                AbstractMap.SimpleEntry<Integer, Integer> result = end();
                plugin.remove(checked.getUniqueId());
                callback.done(started,finished,result,invoker,checked);
            }
        }, plugin.getConfig().getInt("ticksToKill",10));
    }

    public void markAsKilled(Integer val) {
        if (entitiesSpawned.containsKey(val)) {
            entitiesSpawned.put(val, true);
            kill(val).sendPacket(checked);
        }

        if (!entitiesSpawned.containsValue(false)) {
            this.finished = System.currentTimeMillis();
        }
    }

    public AbstractMap.SimpleEntry<Integer, Integer> end() {
        int killed = 0;
        for (Map.Entry<Integer, Boolean> entry : entitiesSpawned.entrySet()) {
            if (entry.getValue()) {
                killed++;
            } else if (checked.isOnline()) {
                kill(entry.getKey()).sendPacket(checked);
            }

        }
        int amount = entitiesSpawned.size();
        entitiesSpawned.clear();
        return new AbstractMap.SimpleEntry<>(killed, amount);

    }

    public static WrapperPlayServerNamedEntitySpawn getWrapper(Vector loc, AntiAura plugin) {
        WrapperPlayServerNamedEntitySpawn wrapper = new WrapperPlayServerNamedEntitySpawn();
        wrapper.setEntityID(AntiAura.RANDOM.nextInt(20000));
        wrapper.setPosition(loc);
        wrapper.setPlayerUUID(UUID.randomUUID().toString());
        wrapper.setPlayerName(String.valueOf(AntiAura.RANDOM.nextInt()));
        wrapper.setYaw(0);
        wrapper.setPitch(-45);
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        watcher.setObject(0, plugin.getConfig().getBoolean("invisibility", false) ? (Byte) (byte) 0x20 : (byte) 0);
        watcher.setObject(6, (Float) (float) 0.5);
        watcher.setObject(11, (Byte) (byte) 1);
        wrapper.setMetadata(watcher);
        return wrapper;
    }

    public static WrapperPlayServerEntityDestroy kill(int entity) {
        WrapperPlayServerEntityDestroy wrapper = new WrapperPlayServerEntityDestroy();
        wrapper.setEntities(new int[]{entity});
        return wrapper;
    }

    public interface Callback {
        public void done(long started, long finished, AbstractMap.SimpleEntry<Integer, Integer> result, CommandSender invoker, Player target);
    }

}
