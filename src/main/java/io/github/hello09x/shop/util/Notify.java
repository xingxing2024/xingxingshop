package io.github.hello09x.shop.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** 可点击的提醒消息。 */
public final class Notify {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Notify() {
    }

    public static void clickable(@NotNull Player player, @NotNull String message,
                                 @NotNull String command, @Nullable String hover) {
        Component component = LEGACY.deserialize(message)
                .clickEvent(ClickEvent.runCommand(command));
        if (hover != null && !hover.isBlank()) {
            component = component.hoverEvent(HoverEvent.showText(LEGACY.deserialize(hover)));
        }
        player.sendMessage(component);
    }
}
