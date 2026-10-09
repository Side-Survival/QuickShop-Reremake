/*
 * This file is a part of project QuickShop, the name is VirtualDisplayItem.java
 *  Copyright (C) PotatoCraft Studio and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.maxgamer.quickshop.shop;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.api.event.ShopDisplayItemSpawnEvent;
import org.maxgamer.quickshop.api.shop.AbstractDisplayItem;
import org.maxgamer.quickshop.api.shop.DisplayType;
import org.maxgamer.quickshop.api.shop.Shop;
import org.maxgamer.quickshop.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side display item, sent through PacketEvents so packet formats follow the server's Minecraft version.
 */
public class VirtualDisplayItem extends AbstractDisplayItem {
    private static final AtomicInteger COUNTER = new AtomicInteger(Integer.MAX_VALUE);
    // Item entity metadata slot holding the ItemStack (base entity data uses slots 0-7)
    private static final int ITEM_STACK_DATA_INDEX = 8;
    private static PacketListenerCommon packetListener = null;
    //unique EntityID
    private final int entityID = COUNTER.decrementAndGet();
    //The List which store packet sender
    private final Set<UUID> packetSenders = new ConcurrentSkipListSet<>();
    //cache chunk x and z
    private SimpleShopChunk chunkLocation;
    private volatile boolean isDisplay;
    //If packet initialized
    private volatile boolean initialized = false;
    //packets
    private PacketWrapper<?> fakeItemSpawnPacket;
    private PacketWrapper<?> fakeItemMetaPacket;
    private PacketWrapper<?> fakeItemVelocityPacket;
    private PacketWrapper<?> fakeItemDestroyPacket;

    public VirtualDisplayItem(@NotNull Shop shop) throws RuntimeException {
        super(shop);
        VirtualDisplayItemManager.load();
    }

    //Due to the delay task in ChunkListener
    //We must move load task to first spawn to prevent some bug and make the check lesser
    private void load() {
        Util.ensureThread(false);
        //some time shop can be loaded when world isn't loaded
        Chunk chunk = shop.getLocation().getChunk();
        chunkLocation = new SimpleShopChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        VirtualDisplayItemManager.put(chunkLocation, this);
        if (Util.isLoaded(shop.getLocation())) {
            //Let nearby player can saw fake item
            Collection<Entity> entityCollection = shop.getLocation().getWorld().getNearbyEntities(shop.getLocation(), PLUGIN.getServer().getViewDistance() * 16, shop.getLocation().getWorld().getMaxHeight(), PLUGIN.getServer().getViewDistance() * 16);
            for (Entity entity : entityCollection) {
                if (entity instanceof Player) {
                    packetSenders.add(entity.getUniqueId());
                }
            }
        }
    }

    private void initFakeDropItemPacket() {
        fakeItemSpawnPacket = PacketFactory.createFakeItemSpawnPacket(entityID, getDisplayLocation());
        fakeItemMetaPacket = PacketFactory.createFakeItemMetaPacket(entityID, getOriginalItemStack().clone());
        fakeItemVelocityPacket = PacketFactory.createFakeItemVelocityPacket(entityID);
        fakeItemDestroyPacket = PacketFactory.createFakeItemDestroyPacket(entityID);
        initialized = true;
    }

    @Override
    public boolean checkDisplayIsMoved() {
        return false;
    }

    @Override
    public boolean checkDisplayNeedRegen() {
        return false;
    }

    @Override
    public boolean checkIsShopEntity(@NotNull Entity entity) {
        return false;
    }

    @Override
    public void fixDisplayMoved() {

    }

    @Override
    public void fixDisplayNeedRegen() {

    }

    @Override
    public void remove() {
        if (isDisplay) {
            sendPacketToAll(fakeItemDestroyPacket);
            unload();
            isDisplay = false;
        }
    }

    private void sendPacketToAll(@NotNull PacketWrapper<?> packet) {
        Iterator<UUID> iterator = packetSenders.iterator();
        while (iterator.hasNext()) {
            Player nextPlayer = PLUGIN.getServer().getPlayer(iterator.next());
            if (nextPlayer == null) {
                iterator.remove();
            } else {
                sendPacket(nextPlayer, packet);
            }
        }
    }

    private void sendPacket(@NotNull Player player, @NotNull PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
        } catch (Exception e) {
            throw new RuntimeException("An error occurred when sending a packet", e);
        }
    }

    @Override
    public boolean removeDupe() {
        return false;
    }

    @Override
    public void respawn() {
        Util.ensureThread(false);
        remove();
        spawn();
    }

    public void sendFakeItemToAll() {
        sendPacketToAll(fakeItemSpawnPacket);
        sendPacketToAll(fakeItemMetaPacket);
        sendPacketToAll(fakeItemVelocityPacket);
    }

    @Override
    public void safeGuard(@Nullable Entity entity) {

    }

    @Override
    public void spawn() {
        Util.ensureThread(false);
        if (shop.isLeftShop() || isDisplay || shop.isDeleted() || !shop.isLoaded()) {
            return;
        }
        ShopDisplayItemSpawnEvent shopDisplayItemSpawnEvent = new ShopDisplayItemSpawnEvent(shop, originalItemStack, DisplayType.VIRTUALITEM);
        PLUGIN.getServer().getPluginManager().callEvent(shopDisplayItemSpawnEvent);
        if (shopDisplayItemSpawnEvent.isCancelled()) {
            Util.debugLog(
                    "Canceled the displayItem spawning because a plugin setCancelled the spawning event, usually this is a QuickShop Add on");
            return;
        }

        //lazy initialize
        if (!initialized) {
            initFakeDropItemPacket();
        }

        load();

        sendFakeItemToAll();
        isDisplay = true;
    }

    private void unload() {
        packetSenders.clear();
        VirtualDisplayItemManager.remove(chunkLocation, this);
    }

    public void sendFakeItem(@NotNull Player player) {
        sendPacket(player, fakeItemSpawnPacket);
        sendPacket(player, fakeItemMetaPacket);
        sendPacket(player, fakeItemVelocityPacket);
    }

    @Override
    public @Nullable Entity getDisplay() {
        return null;
    }

    @Override
    public boolean isSpawned() {
        if (shop.isLeftShop()) {
            Shop aShop = shop.getAttachedShop();
            if (aShop instanceof ContainerShop) {
                return (Objects.requireNonNull(((ContainerShop) aShop).getDisplayItem())).isSpawned();
            }

        }
        return isDisplay;
    }

    public static class VirtualDisplayItemManager {
        private static final AtomicBoolean LOADED = new AtomicBoolean(false);
        private static final Map<SimpleShopChunk, List<VirtualDisplayItem>> CHUNKS_MAPPING = new ConcurrentHashMap<>();

        public static void put(@NotNull SimpleShopChunk key, @NotNull VirtualDisplayItem value) {
            //Thread-safe was ensured by ONLY USE Map method to do something
            List<VirtualDisplayItem> virtualDisplayItems = new ArrayList<>(Collections.singletonList(value));
            CHUNKS_MAPPING.merge(key, virtualDisplayItems, (mapOldVal, mapNewVal) -> {
                mapOldVal.addAll(mapNewVal);
                return mapOldVal;
            });
        }

        public static void remove(@NotNull SimpleShopChunk key, @NotNull VirtualDisplayItem value) {
            CHUNKS_MAPPING.computeIfPresent(key, (mapOldKey, mapOldVal) -> {
                mapOldVal.remove(value);
                return mapOldVal;
            });
        }

        public static void load() {
            if (LOADED.get()) {
                return;
            }
            Util.debugLog("Loading VirtualDisplayItem chunks mapping manager...");
            if (packetListener == null) {
                Util.debugLog("Registering the packet listener...");
                packetListener = PacketEvents.getAPI().getEventManager().registerListener(new ChunkPacketListener());
                LOADED.set(true);
            }
        }

        private static class ChunkPacketListener extends PacketListenerAbstract {

            ChunkPacketListener() {
                super(PacketListenerPriority.HIGH);
            }

            @Override
            public void onPacketSend(@NotNull PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) {
                    return;
                }
                Object eventPlayer = event.getPlayer();
                if (!(eventPlayer instanceof Player)) {
                    return;
                }
                Player player = (Player) eventPlayer;
                // Read only chunk x/z instead of decoding the whole chunk; PacketEvents restores the buffer afterwards
                PacketWrapper<?> wrapper = new PacketWrapper<>(event, false);
                int x = wrapper.readInt();
                int z = wrapper.readInt();
                // We are on the netty thread: hop to main so the entity follows the chunk and world access is safe
                PLUGIN.getServer().getScheduler().runTask(PLUGIN, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    CHUNKS_MAPPING.computeIfPresent(new SimpleShopChunk(player.getWorld().getName(), x, z), (chunkLocation, targetList) -> {
                        for (VirtualDisplayItem target : targetList) {
                            if (!target.shop.isLoaded() || !target.isDisplay || target.shop.isLeftShop()) {
                                continue;
                            }
                            target.packetSenders.add(player.getUniqueId());
                            target.sendFakeItem(player);
                        }
                        return targetList;
                    });
                });
            }

        }

        public static void unload() {
            Util.debugLog("Unloading VirtualDisplayItem chunks mapping manager...");
            if (LOADED.get()) {
                Util.debugLog("Unregistering the packet listener...");
                PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
                packetListener = null;
                LOADED.set(false);
            }
        }
    }

    public static class PacketFactory {
        public static Throwable testFakeItem() {
            try {
                createFakeItemSpawnPacket(0, new Location(PLUGIN.getServer().getWorlds().get(0), 0, 0, 0));
                createFakeItemMetaPacket(0, new ItemStack(Material.STONE));
                createFakeItemVelocityPacket(0);
                createFakeItemDestroyPacket(0);
                return null;
            } catch (Throwable throwable) {
                return throwable;
            }
        }

        private static PacketWrapper<?> createFakeItemSpawnPacket(int entityID, Location displayLocation) {
            return new WrapperPlayServerSpawnEntity(entityID, Optional.of(UUID.randomUUID()), EntityTypes.ITEM,
                    new Vector3d(displayLocation.getX(), displayLocation.getY(), displayLocation.getZ()),
                    0f, 0f, 0f, 0, Optional.of(new Vector3d(0, 0, 0)));
        }

        private static PacketWrapper<?> createFakeItemMetaPacket(int entityID, ItemStack itemStack) {
            List<EntityData<?>> data = new ArrayList<>();
            if (PLUGIN.getConfig().getBoolean("shop.display-item-use-name")) {
                String itemName = QuickShop.isTesting() ? itemStack.getType().name() : Util.getItemStackName(itemStack);
                data.add(new EntityData<>(2, EntityDataTypes.OPTIONAL_COMPONENT, Optional.of(ComponentSerializer.toString(TextComponent.fromLegacyText(itemName)))));
                data.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, true));
            }
            data.add(new EntityData<>(ITEM_STACK_DATA_INDEX, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(itemStack)));
            return new WrapperPlayServerEntityMetadata(entityID, data);
        }

        private static PacketWrapper<?> createFakeItemVelocityPacket(int entityID) {
            //Stop the client from giving the item a random spawn velocity
            return new WrapperPlayServerEntityVelocity(entityID, new Vector3d(0, 0, 0));
        }

        private static PacketWrapper<?> createFakeItemDestroyPacket(int entityID) {
            return new WrapperPlayServerDestroyEntities(entityID);
        }
    }
}
