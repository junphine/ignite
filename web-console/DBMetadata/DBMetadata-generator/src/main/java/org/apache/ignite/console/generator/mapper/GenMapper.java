package org.apache.ignite.console.generator.mapper;

import org.apache.ignite.console.generator.common.config.GenConfig;
import org.apache.ignite.console.generator.domain.GenTable;


import java.util.List;
import java.util.Map;


public interface GenMapper {
    //    @Select({"select table_name, table_comment, create_time, update_time from information_schema.tables where table_schema = (select database()) AND table_name NOT LIKE 'sys_%'"})
    List<GenTable> selectDbTableList(GenConfig config,Map<String, Object> context);
}
