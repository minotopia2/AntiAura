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
import com.comphenix.packetwrapper.WrapperPlayServerPlayerInfo;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.Registry;
import com.comphenix.protocol.wrappers.WrappedDataWatcher.WrappedDataWatcherObject;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;


public class AuraCheck {
    public static final short[][] standing = {{0,1},{-1,0},{1,0},{0,-1},{1,1},{-1,-1},{-1,1},{1,-1}};
    public static final short[][] running = {{0,2},{-2,0},{2,0},{0,-2},{1,1},{2,2},{-1,-1},{-2,-2},{-1,1},{-2,2},{1,-1},{2,-2}};

    private final AntiAura plugin;
    private Map<Integer, Boolean> entitiesSpawned = new HashMap<>();
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

    public void invoke(CommandSender player, String type, boolean visOrInvisible, final Callback callback) {
        this.invoker = player;
        this.started = System.currentTimeMillis();

        int numPlayers = plugin.getConfig().getInt("amountOfFakePlayers");
        for (int i = 1; i <= numPlayers; i++) {
            int degrees = 360 / (numPlayers - 1) * i;
            double radians = Math.toRadians(degrees);
            WrapperPlayServerNamedEntitySpawn spawnWrapper;
            if (i == 1) {
                spawnWrapper = getSpawnWrapper(this.checked.getLocation().add(0, 2, 0).toVector(), plugin);
            } else {
                spawnWrapper = getSpawnWrapper(this.checked.getLocation().add(2 * Math.cos(radians), 0.2, 2 * Math.sin(radians)).toVector(), plugin);
            }
            WrapperPlayServerPlayerInfo infoWrapper = getInfoWrapper(spawnWrapper.getPlayerUUID(), PlayerInfoAction.ADD_PLAYER);
            infoWrapper.sendPacket(this.checked);
            spawnWrapper.sendPacket(this.checked);
            entitiesSpawned.put(spawnWrapper.getEntityID(), false);
            WrapperPlayServerPlayerInfo RemoveinfoWrapper = getInfoWrapper(spawnWrapper.getPlayerUUID(), PlayerInfoAction.REMOVE_PLAYER);
            RemoveinfoWrapper.sendPacket(this.checked);
        }

        Bukkit.getScheduler().runTaskLater(this.plugin, new Runnable() {
            @Override
            public void run() {
                AbstractMap.SimpleEntry<Integer, Integer> result = end();
                plugin.remove(checked.getUniqueId());
                callback.done(started,finished,result,invoker,checked);
            }
        }, plugin.getConfig().getInt("settings.ticksToKill", 10));
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

    public static WrapperPlayServerNamedEntitySpawn getSpawnWrapper(Vector loc, AntiAura plugin, boolean visOrInvisible) {
        WrapperPlayServerNamedEntitySpawn wrapper = new WrapperPlayServerNamedEntitySpawn();
        wrapper.setEntityID(AntiAura.RANDOM.nextInt(20000));
        wrapper.setPosition(loc);
        wrapper.setPlayerUUID(UUID.randomUUID());
        wrapper.setYaw(0.0F);
        wrapper.setPitch(-45.0F);
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        watcher.setObject(new WrappedDataWatcherObject(0, Registry.get(Byte.class)), visOrInvisible ? (byte) 0x20 : (byte) 0);
        watcher.setObject(new WrappedDataWatcherObject(6, Registry.get(Float.class)), 0.5F);
        watcher.setObject(new WrappedDataWatcherObject(11, Registry.get(Byte.class)), (byte) 1);
        wrapper.setMetadata(watcher);
        return wrapper;
    }


    public static WrapperPlayServerPlayerInfo getInfoWrapper(UUID playeruuid, PlayerInfoAction action) {
        WrapperPlayServerPlayerInfo wrapper = new WrapperPlayServerPlayerInfo();
        wrapper.setAction(action);
        WrappedGameProfile profile = new WrappedGameProfile(playeruuid, NameGenerator.newName());
        PlayerInfoData data = new PlayerInfoData(profile, 1, NativeGameMode.SURVIVAL, WrappedChatComponent.fromText(NameGenerator.newName()));
        List<PlayerInfoData> listdata = new ArrayList<>();
        listdata.add(data);
        wrapper.setData(listdata);
        return wrapper;
    }

    public static WrapperPlayServerEntityDestroy kill(int entity) {
        WrapperPlayServerEntityDestroy wrapper = new WrapperPlayServerEntityDestroy();
        wrapper.setEntityIds(new int[]{entity});
        return wrapper;
    }

    public interface Callback {
        public void done(long started, long finished, AbstractMap.SimpleEntry<Integer, Integer> result, CommandSender invoker, Player target);
    }

}
