package org.apache.ignite.console.generator.service;

import org.apache.ignite.console.generator.common.config.GenConfig;
import org.apache.ignite.console.generator.domain.GenTable;
import org.apache.ignite.console.generator.domain.GenTableData;

import java.util.List;
import java.util.Map;

public interface GenService {

    List<GenTable> selectDbTableList(GenConfig config,Map<String,Object> context);

    List<GenTableData> buildTableInfo(List<GenTable> genTables, GenConfig genConfig,Map<String,Object> context);

    /**
     * 渲染所有模板并把生成的源码按目录结构展开写入 outputRoot。
     *
     * @param genTableData 表信息
     * @param outputRoot   输出根目录（如 target/generated-crud），不存在时自动创建
     * @return 成功生成的模型数量（0 表示全部被跳过，调用方应报错而不是静默“成功”）
     */
    int generatorCode(List<GenTableData> genTableData, String outputRoot);

    void setPkColumn(GenTable table);
}
