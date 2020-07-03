package com.facebook.presto.plugin.ignite;

import com.facebook.presto.plugin.jdbc.*;
import com.facebook.presto.spi.ConnectorSession;
import com.facebook.presto.spi.ConnectorTableMetadata;
import com.facebook.presto.spi.PrestoException;
import com.facebook.presto.spi.SchemaTableName;
import com.facebook.presto.spi.TableNotFoundException;
import com.facebook.presto.spi.type.DoubleType;
import com.facebook.presto.spi.type.Type;
import com.facebook.presto.spi.type.VarcharType;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import org.apache.ignite.IgniteJdbcThinDriver;
import org.apache.ignite.IgniteJdbcDriver;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.facebook.presto.plugin.jdbc.JdbcErrorCode.JDBC_ERROR;
import static java.util.Locale.ENGLISH;
import static com.facebook.presto.spi.StandardErrorCode.NOT_SUPPORTED;
import static com.facebook.presto.spi.type.RealType.REAL;
import static com.facebook.presto.spi.type.TimeWithTimeZoneType.TIME_WITH_TIME_ZONE;
import static com.facebook.presto.spi.type.TimestampType.TIMESTAMP;
import static com.facebook.presto.spi.type.TimestampWithTimeZoneType.TIMESTAMP_WITH_TIME_ZONE;
import static com.facebook.presto.spi.type.VarbinaryType.VARBINARY;
import static com.facebook.presto.spi.type.Varchars.isVarcharType;
import static com.google.common.collect.Iterables.getOnlyElement;


public class IgniteClient extends BaseJdbcClient {

	IgniteConfig igniteConfig;

    @Inject
    public IgniteClient(JdbcConnectorId connectorId, BaseJdbcConfig config, IgniteConfig igniteConfig) {
    	 super(connectorId, config, "",        		
         		igniteConfig.isThinConnection() ? 
         				 new DriverConnectionFactory(new IgniteJdbcThinDriver(), config)
         				:new DriverConnectionFactory(new IgniteJdbcDriver(), config));

    }

    @Override
    protected Collection<String> listSchemas(Connection connection){
        // for MySQL, we need to list catalogs instead of schemas
        try (ResultSet resultSet = connection.getMetaData().getSchemas()) {
            ImmutableSet.Builder<String> schemaNames = ImmutableSet.builder();
            while (resultSet.next()) {
                String schemaName = resultSet.getString(1); //.toLowerCase(ENGLISH);
                schemaNames.add(schemaName);
            }
            return schemaNames.build();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

   

   
    @Override
    protected ResultSet getTables(Connection connection, Optional<String> schemaName, Optional<String> tableName)
            throws SQLException
    {
        DatabaseMetaData metadata = connection.getMetaData();
        Optional<String> escape = Optional.ofNullable(metadata.getSearchStringEscape());
        return metadata.getTables(
                connection.getCatalog(),
                escapeNamePattern(schemaName, escape).orElse(null),
                escapeNamePattern(tableName, escape).orElse(null),
                new String[] {"TABLE", "VIEW"});
    }

    @Nullable
    @Override
    public JdbcTableHandle getTableHandle(JdbcIdentity identity, SchemaTableName schemaTableName) {
        try (Connection connection = connectionFactory.openConnection(identity)) {
            DatabaseMetaData metadata = connection.getMetaData();
           
            String jdbcSchemaName = schemaTableName.getSchemaName();
            String jdbcTableName = schemaTableName.getTableName();
            if (metadata.storesUpperCaseIdentifiers()) {
                jdbcSchemaName = jdbcSchemaName.toUpperCase();
                jdbcTableName = jdbcTableName.toUpperCase();
            }
            try (ResultSet resultSet = getTables(connection,  Optional.of(jdbcSchemaName),
            		 Optional.of(jdbcTableName))) {
                List<JdbcTableHandle> tableHandles = new ArrayList<>();
                while (resultSet.next()) {
                	//modify@byron TABLE_CAT to null
                	//resultSet.getString("TABLE_CAT")
                	
                    tableHandles.add(new JdbcTableHandle(connectorId,
                            schemaTableName, null,
                            resultSet.getString("TABLE_SCHEM"), resultSet
                            .getString("TABLE_NAME")));
                }
                if (tableHandles.isEmpty()) {
                    return null;
                }
                if (tableHandles.size() > 1) {
                    throw new PrestoException(NOT_SUPPORTED,
                            "Multiple tables matched: " + schemaTableName);
                }
                return getOnlyElement(tableHandles);
            }
        } catch (SQLException e) {
            throw Throwables.propagate(e);
        }
    }

    protected String getTableWithString(ConnectorTableMetadata tableMetadata, String tableName)
    {    	
    	if(tableMetadata.getProperties().size()>0) {    		
    		StringBuilder with = new StringBuilder();
    		with.append("WITH ");
    		with.append('"');    		
    		for(Map.Entry<String,Object> ent: tableMetadata.getProperties().entrySet()) {    			
    			if(ent.getKey().equalsIgnoreCase(PRIMARY_KEY)) continue;
    			with.append(ent.getKey());
    			with.append('=');
    			with.append(ent.getValue());
    			with.append(',');
    		}
    		with.append('"');
    		return with.length()>8 ? with.toString():"";
    	}
    	return "";
    }
   

    @Override
    protected String toSqlType(Type type)
    {
        if (DoubleType.DOUBLE.equals(type)) {
            return "double";
        }
        if (TIME_WITH_TIME_ZONE.equals(type)) {
            return "time";
        }
        if (TIMESTAMP_WITH_TIME_ZONE.equals(type)) {
            return "timestamp";
        }

        return super.toSqlType(type);
    }


  /**

    private static ResultSet getColumns(JdbcTableHandle tableHandle, DatabaseMetaData metadata)
            throws SQLException {
        ResultSet columnSet = metadata.getColumns(
                tableHandle.getCatalogName(),
                tableHandle.getSchemaName(),
                tableHandle.getTableName(),
                null);
        return columnSet;
    }
    
    private static ResultSet getPkColumns(JdbcTableHandle tableHandle, DatabaseMetaData metadata)
            throws SQLException {
        ResultSet columnSet = metadata.getPrimaryKeys(
                tableHandle.getCatalogName(),
                tableHandle.getSchemaName(),
                tableHandle.getTableName()
                );
        return columnSet;
    }
  */ 
    @Override
    public JdbcOutputTableHandle beginCreateTable(ConnectorSession session, ConnectorTableMetadata tableMetadata)
    {
    	try {
            return createTable(tableMetadata, session, tableMetadata.getTable().getTableName());
        }
        catch (SQLException e) {
            throw new PrestoException(JDBC_ERROR, e);
        }
    }

    @Override
    public JdbcOutputTableHandle beginInsertTable(ConnectorSession session, ConnectorTableMetadata tableMetadata)
    {
    	SchemaTableName  table = tableMetadata.getTable();
    	try {
            return createTable(tableMetadata, session, table.getTableName());
        }
        catch (SQLException e) {        	
        	log.info("Begin insert table, Table already exists  for "+tableMetadata.getTable());
        	List<String> columnNames = tableMetadata.getColumns().stream().map(meta->meta.getName()).collect(Collectors.toList());
        	List<Type> columnTypes = tableMetadata.getColumns().stream().map(meta->meta.getType()).collect(Collectors.toList());
        	return new JdbcOutputTableHandle(
                     connectorId,
                     null,
                     table.getSchemaName(),                     
                     table.getTableName(),
                     columnNames,
                     columnTypes,
                     table.getTableName());
        }
    }
    @Override
    public void commitCreateTable(JdbcIdentity identity, JdbcOutputTableHandle handle)
    {
    	log.info("commitCreateTable "+identity+" for "+handle.getTableName());
    }
    
    @Override
    public void finishInsertTable(JdbcIdentity identity, JdbcOutputTableHandle handle)
    {
    	log.info("finishInsertTable "+identity+" for "+handle.getTableName());
    }
}
