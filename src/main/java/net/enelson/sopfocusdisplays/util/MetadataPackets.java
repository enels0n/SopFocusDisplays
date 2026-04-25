package net.enelson.sopfocusdisplays.util;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class MetadataPackets {

    private MetadataPackets() {
    }

    public static void sendEntityMetadata(ProtocolManager protocolManager, Player player, int targetEntityId, Entity metadataSource) {
        PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, targetEntityId);

        List<WrappedWatchableObject> watchables = com.comphenix.protocol.wrappers.WrappedDataWatcher
                .getEntityWatcher(metadataSource)
                .getWatchableObjects();

        try {
            packet.getDataValueCollectionModifier().write(0, toDataValues(watchables));
        } catch (FieldAccessException exception) {
            packet.getWatchableCollectionModifier().write(0, watchables);
        }

        protocolManager.sendServerPacket(player, packet);
    }

    private static List<WrappedDataValue> toDataValues(List<WrappedWatchableObject> watchables) {
        List<WrappedDataValue> values = new ArrayList<WrappedDataValue>();
        for (WrappedWatchableObject watchable : watchables) {
            if (watchable == null || watchable.getWatcherObject() == null) {
                continue;
            }

            values.add(new WrappedDataValue(
                    watchable.getWatcherObject().getIndex(),
                    watchable.getWatcherObject().getSerializer(),
                    watchable.getRawValue()
            ));
        }
        return values;
    }
}