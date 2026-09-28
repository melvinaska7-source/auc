package ru.privatenull.service;

import ru.privatenull.PnMarketPlugin;
import ru.privatenull.pnlibrary.database.DatabaseRouter;
import ru.privatenull.storage.*;

/** Routes every pnMarket repository through one shared pnLibrary backend. */
public final class MarketStorageFactory implements AutoCloseable {
    private final PnMarketPlugin plugin;
    private final DatabaseRouter router;
    private JdbcMarketRepository jdbcRegular;
    private JdbcMarketRepository jdbcDonate;
    private MarketRepository mongoRegular;
    private MarketRepository mongoDonate;
    private RedisMarketStorage redisRegular;
    private RedisMarketStorage redisDonate;

    public MarketStorageFactory(PnMarketPlugin plugin) {
        this.plugin = plugin;
        this.router = DatabaseRouter.from(plugin.getConfig().getConfigurationSection("storage"),
                plugin.getDataFolder());
    }

    public MarketStorage open(boolean donate) {
        return router.route(
                jdbc -> jdbc(donate),
                mongo -> mongo(donate),
                redis -> redis(donate));
    }

    public MarketStorage openNotifications() {
        return router.route(
                jdbc -> jdbc(false),
                mongo -> mongo(false),
                redis -> redis(false));
    }

    public MarketStorage openFavorites() {
        return router.route(
                jdbc -> jdbc(false),
                mongo -> mongo(false),
                redis -> redis(false));
    }

    public MarketStorage openDeliveries() {
        return router.route(
                jdbc -> jdbc(false),
                mongo -> mongo(false),
                redis -> redis(false));
    }

    public DatabaseRouter router() {
        return router;
    }

    private RedisMarketStorage redis(boolean donate) {
        if (donate) {
            if (redisDonate == null) redisDonate = new RedisMarketStorage(router.redis(), true);
            return redisDonate;
        }
        if (redisRegular == null) redisRegular = new RedisMarketStorage(router.redis(), false);
        return redisRegular;
    }

    private JdbcMarketRepository jdbc(boolean donate) {
        if (donate) {
            if (jdbcDonate == null) jdbcDonate = new JdbcMarketRepository(
                    router.jdbc(), legacyExpiry(), plugin.getLogger(), table(true));
            return jdbcDonate;
        }
        if (jdbcRegular == null) jdbcRegular = new JdbcMarketRepository(
                router.jdbc(), legacyExpiry(), plugin.getLogger(), table(false));
        return jdbcRegular;
    }

    private MarketRepository mongo(boolean donate) {
        String base = router.mongo().settings().collection();
        if (donate) {
            if (mongoDonate == null) mongoDonate = new MarketRepository(router.mongo().database(),
                    base + "_donate", base, legacyExpiry(), plugin.getLogger());
            return mongoDonate;
        }
        if (mongoRegular == null) mongoRegular = new MarketRepository(router.mongo().database(),
                base, base, legacyExpiry(), plugin.getLogger());
        return mongoRegular;
    }

    private String table(boolean donate) {
        return donate ? "pnmarket_donate_listings" : "pnmarket_listings";
    }

    private long legacyExpiry() {
        return 86_400_000L;
    }

    @Override
    public void close() {
        router.close();
    }
}
