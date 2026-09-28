package ru.privatenull;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ru.privatenull.market.FavoriteService;
import ru.privatenull.service.MarketRuntime;
import ru.privatenull.storage.MarketStorage;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JoinNotificationsTest {
    private final UUID playerId = UUID.randomUUID();
    private final Map<UUID, Long> offlineSince = new HashMap<>();
    private PnMarketPlugin plugin;
    private Player joiningPlayer;
    private OfflinePlayer savedPlayer;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(PnMarketPlugin.class);
        Server server = mock(Server.class);
        joiningPlayer = mock(Player.class);
        savedPlayer = mock(OfflinePlayer.class);
        when(plugin.getServer()).thenReturn(server);
        when(joiningPlayer.getUniqueId()).thenReturn(playerId);
        when(server.getOfflinePlayer(playerId)).thenReturn(savedPlayer);
        setField("notificationOfflineSince", offlineSince);
        doCallRealMethod().when(plugin).prepareJoinNotifications(any());
        doCallRealMethod().when(plugin).notifyOnJoin(any());
    }

    @Test
    void capturesSavedHistoryBeforeLoginPlayerDataIsLoaded() {
        // PlayerLoginEvent exposes a fresh Player before Paper loads its saved data.
        when(joiningPlayer.hasPlayedBefore()).thenReturn(false);
        when(joiningPlayer.getLastSeen()).thenReturn(0L);
        when(savedPlayer.hasPlayedBefore()).thenReturn(true);
        when(savedPlayer.getLastSeen()).thenReturn(10_000L);

        plugin.prepareJoinNotifications(joiningPlayer);

        assertEquals(10_000L, offlineSince.get(playerId));
        verify(joiningPlayer, never()).getLastSeen();
    }

    @Test
    void firstLoginDoesNotReplayOldListings() {
        when(savedPlayer.hasPlayedBefore()).thenReturn(false);

        plugin.prepareJoinNotifications(joiningPlayer);

        assertEquals(-1L, offlineSince.get(playerId));
        verify(savedPlayer, never()).getLastSeen();
    }

    @Test
    void returningPlayerChecksBothAuctionsUsingSavedLogoutTime() throws Exception {
        when(savedPlayer.hasPlayedBefore()).thenReturn(true);
        when(savedPlayer.getLastSeen()).thenReturn(10_000L);
        when(joiningPlayer.isOnline()).thenReturn(true);
        MarketRuntime runtime = mock(MarketRuntime.class);
        MarketStorage regular = mock(MarketStorage.class);
        MarketStorage donate = mock(MarketStorage.class);
        FavoriteService favorites = mock(FavoriteService.class);
        when(runtime.storage(false)).thenReturn(regular);
        when(runtime.storage(true)).thenReturn(donate);
        when(runtime.favorites()).thenReturn(favorites);
        setField("runtime", runtime);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            plugin.prepareJoinNotifications(joiningPlayer);
            plugin.notifyOnJoin(joiningPlayer);
        }

        verify(regular).findActiveCreatedAfter(eq(10_000L), anyLong());
        verify(donate).findActiveCreatedAfter(eq(10_000L), anyLong());
        verify(favorites).notifyAvailable(joiningPlayer, 10_000L, List.of(), List.of());
        assertTrue(offlineSince.isEmpty());
    }

    private void setField(String name, Object value) throws Exception {
        Field field = PnMarketPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
