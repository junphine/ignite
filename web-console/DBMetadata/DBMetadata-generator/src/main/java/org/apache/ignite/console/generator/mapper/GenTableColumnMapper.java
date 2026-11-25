package org.apache.ignite.console.generator.mapper;

import org.apache.ignite.console.generator.domain.GenTableColumn;

import java.util.List;
import java.util.Map;

public interface GenTableColumnMapper{
    List<GenTableColumn> selectDbTableColumnsByName(String tableName, Map<String,Object> context);
}
