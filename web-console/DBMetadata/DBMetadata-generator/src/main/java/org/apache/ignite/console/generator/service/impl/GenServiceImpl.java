package org.apache.ignite.console.generator.service.impl;

import org.apache.ignite.console.generator.common.config.GenConfig;
import org.apache.ignite.console.generator.common.config.VelocityInitializer;
import org.apache.ignite.console.generator.common.utils.GenUtils;
import org.apache.ignite.console.generator.common.utils.StringUtil;
import org.apache.ignite.console.generator.common.utils.VelocityUtils;
import org.apache.ignite.console.generator.domain.GenTable;
import org.apache.ignite.console.generator.domain.GenTableColumn;
import org.apache.ignite.console.generator.domain.GenTableData;
import org.apache.ignite.console.generator.mapper.GenMapper;
import org.apache.ignite.console.generator.mapper.GenTableColumnMapper;
import org.apache.ignite.console.generator.service.GenService;
import org.apache.commons.io.IOUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.context.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GenServiceImpl implements GenService {
    private static final Logger log = LoggerFactory.getLogger(GenService.class);


    private GenTableColumnMapper genTableColumnMapper;


    private GenMapper genTableMapper;

    public GenServiceImpl(GenMapper genTableMapper,GenTableColumnMapper genTableColumnMapper){
        this.genTableMapper = genTableMapper;
        this.genTableColumnMapper = genTableColumnMapper;
    }

    @Override
    public List<GenTable> selectDbTableList(GenConfig config,Map<String,Object> context) {
        return this.genTableMapper.selectDbTableList(config,context);
    }

    @Override
    public List<GenTableData> buildTableInfo(List<GenTable> genTables,GenConfig genConfig,Map<String,Object> context) {

        List<GenTableData> objects = new ArrayList<>();
        try {
            for (GenTable table : genTables) {
                GenTableData genTableData = new GenTableData();
                ArrayList<GenTableColumn> tableColumns = new ArrayList<>();
                String tableName = table.getTableName();
                genTableData.setGenTables(table);
                List<GenTableColumn> genTableColumns = genTableColumnMapper.selectDbTableColumnsByName(tableName,context);
                for (GenTableColumn column : genTableColumns) {
                    GenUtils.initColumnField(column, table);
                    tableColumns.add(column);
                }
                genTableData.setGenTableColumns(tableColumns);
                objects.add(genTableData);
            }
        } catch (Exception e) {
            throw new RuntimeException("渲染失败：" + e.getMessage());
        }
        return objects;
    }

    @Override
    public int generatorCode(List<GenTableData> genTableData, String outputRoot) {
        VelocityInitializer.initVelocity();

        int generated = 0;
        boolean projectFilesGenerated = false;
        for (GenTableData genTableDatum : genTableData) {
            GenTable genTable = genTableDatum.getGenTables();
            genTable.setColumns(genTableDatum.getGenTableColumns());
            setPkColumn(genTable);
            if (genTable.getPkColumn() == null || genTable.getColumns() == null || genTable.getColumns().isEmpty()) {
                log.warn("表 {} 没有可用字段，跳过代码生成", genTable.getTableName());
                continue;
            }
            VelocityContext context = VelocityUtils.prepareContext(genTable);
            List<String> templates = VelocityUtils.getTemplateList(genTable.getTplCategory());
            if (!genTable.isGenerateFrontend()) {
                // context.generateFrontend=false：只出 Java/pom，不写 frontend/views/{集群名}/ 下的 tsx
                templates.removeIf(VelocityUtils::isFrontendTemplate);
            }
            for (String template : templates) {
                StringWriter sw = new StringWriter();
                try {
                    Template tpl = Velocity.getTemplate(template, "UTF-8");
                    tpl.merge(context, sw);

                    String fileName = VelocityUtils.getFileName(template, genTable);
                    if (StringUtil.isEmpty(fileName)) {
                        log.warn("模板 {} 没有对应的输出路径，跳过", template);
                        continue;
                    }
                    Path dest = Paths.get(outputRoot, fileName);
                    Files.createDirectories(dest.getParent());
                    Files.write(dest, sw.toString().getBytes(StandardCharsets.UTF_8));
                    log.info("生成文件：{}", dest);
                } catch (IOException e) {
                    log.error("渲染模板失败，表名：" + genTable.getTableName(), e);
                } finally {
                    IOUtils.closeQuietly(sw);
                }
            }
            // 工程级文件（UiPlugin / services 描述符 / pom.xml）与表无关，用首个有效表的上下文渲染一次
            if (!projectFilesGenerated) {
                renderProjectFiles(genTable, outputRoot);
                projectFilesGenerated = true;
            }
            generated++;
        }
        return generated;
    }

    /**
     * 渲染工程级文件：UiPlugin 实现、META-INF/services 描述符、pom.xml。
     */
    private void renderProjectFiles(GenTable genTable, String outputRoot) {
        VelocityContext context = VelocityUtils.prepareContext(genTable);
        for (String template : VelocityUtils.getProjectTemplateList()) {
            StringWriter sw = new StringWriter();
            try {
                Template tpl = Velocity.getTemplate(template, "UTF-8");
                tpl.merge(context, sw);

                String fileName = VelocityUtils.getProjectFileName(template, genTable);
                if (StringUtil.isEmpty(fileName)) {
                    log.warn("工程模板 {} 没有对应的输出路径，跳过", template);
                    continue;
                }
                Path dest = Paths.get(outputRoot, fileName);
                Files.createDirectories(dest.getParent());
                Files.write(dest, sw.toString().getBytes(StandardCharsets.UTF_8));
                log.info("生成文件：{}", dest);
            } catch (IOException e) {
                log.error("渲染工程模板失败：" + template, e);
            } finally {
                IOUtils.closeQuietly(sw);
            }
        }
    }

    @Override
    public void setPkColumn(GenTable table) {
        if (table.getColumns() == null || table.getColumns().isEmpty()) {
            return;
        }
        for (GenTableColumn column : table.getColumns()) {
            if (column.isPk()) {
                table.setPkColumn(column);
                break;
            }
        }
        if (StringUtil.isNull(table.getPkColumn()))
            table.setPkColumn(table.getColumns().get(0));
        if ("sub".equals(table.getTplCategory())) {
            for (GenTableColumn column : table.getSubTable().getColumns()) {
                if (column.isPk()) {
                    table.getSubTable().setPkColumn(column);
                    break;
                }
            }
            if (StringUtil.isNull(table.getSubTable().getPkColumn()))
                table.getSubTable().setPkColumn(table.getSubTable().getColumns().get(0));
        }

    }
}
