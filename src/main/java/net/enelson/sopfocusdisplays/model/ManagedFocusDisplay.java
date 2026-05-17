package net.enelson.sopdisplays.model;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public interface ManagedFocusDisplay {

    void spawn();

    void remove();

    void updateItem(ItemStack itemStack);

    void updateText(String text);

    void move(Location location);

    FocusDisplayDefinition getDefinition();

    boolean isSameWorld(Player player);

    void ensureViewerMode(Player player);

    void syncViewerText(Player player);

    boolean isLookingAt(Player player, double maxDistance, double focusCosine);

    float getViewerScale(UUID uniqueId);

    void setViewerScale(UUID uniqueId, float scale);

    void sendScale(Player player, float scale);

    void forgetViewer(UUID uniqueId);

    void hideFor(Player player);

    void invalidateViewerState(UUID uniqueId);

    void resetViewerText(UUID uniqueId);
}
