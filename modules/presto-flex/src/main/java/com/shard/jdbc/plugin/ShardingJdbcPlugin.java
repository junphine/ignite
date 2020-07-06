package com.shard.jdbc.plugin;

import io.prestosql.plugin.jdbc.JdbcPlugin;


public class ShardingJdbcPlugin extends JdbcPlugin {

    public ShardingJdbcPlugin() {
        // name of the connector and the module implementation
        super("sharding-jdbc", new ShardingJdbcClientModule());
    }
}
