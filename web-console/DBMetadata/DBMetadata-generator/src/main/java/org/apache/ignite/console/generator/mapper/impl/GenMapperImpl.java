package org.apache.ignite.console.generator.mapper.impl;

import org.apache.ignite.console.generator.common.config.GenConfig;
import org.apache.ignite.console.generator.common.utils.GenUtils;
import org.apache.ignite.console.generator.common.utils.StringUtil;

import org.apache.ignite.console.generator.domain.GenTable;
import org.apache.ignite.console.generator.mapper.GenMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 context 中的 Ignite Web Console 模型配置（modelList，按 valueType 索引）
 * 构建 {@link GenTable} 列表。
 * <p>
 * modelList/cacheList 由 {@code CrudUICodeGenerator} 负责解析：
 * 优先取 Web Console 服务端，失败时回退到本地 mock 目录（参考 src/test/resources/mock/）。
 */
public class GenMapperImpl implements GenMapper {

    @Override
    public List<GenTable> selectDbTableList(GenConfig config,Map<String, Object> context) {
        String author = config.author;
        List<GenTable> tables = new ArrayList<>();
        Object modelListObj = context.get("modelList");
        if (!(modelListObj instanceof JsonObject modelList)) {
            return tables;
        }
        JsonObject cacheList = context.get("cacheList") instanceof JsonObject c ? c : null;
        // 前端视图输出目录 frontend/views/{集群名}/：所有模型共用一个集群名
        String clusterName = resolveClusterName(config, context);

        for (String valueTypeKey : modelList.fieldNames()) {
            JsonObject model = modelList.getJsonObject(valueTypeKey);
            if (model == null) {
                continue;
            }
            GenTable table = new GenTable();

            String valueType = model.getString("valueType");
            if (StringUtil.isEmpty(valueType)) {
                valueType = valueTypeKey;
            }
            if (valueType == null || valueType.indexOf('.') <= 0) {
                // 不再生成 entity：必须能 import 到已有 model 类，valueType 没有包名时
                // 生成的代码引用不到该类（编译不过），直接跳过并告警
                System.err.println("[WARN] 模型 valueType 不含包名，无法引用已有 model 类，跳过：" + valueType);
                continue;
            }
            table.setValueType(valueType);

            JsonArray modelKeyFields = model.getJsonArray("keyFields");
            table.setKeyType(model.getString("keyType"));
            table.setCompositeKey(modelKeyFields != null && modelKeyFields.size() > 1);

            // model 类 @Id 字段（属性）：复合键固定为 id（Web Console 生成 Key 类 POJO 的约定），
            // 单键取 keyFieldName（如 uid/fid/zoneId）
            String keyFieldName = model.getString("keyFieldName");
            if (!table.isCompositeKey() && StringUtil.isEmpty(keyFieldName) && modelKeyFields != null
                    && modelKeyFields.size() == 1) {
                JsonObject firstKeyField = modelKeyFields.getJsonObject(0);
                keyFieldName = firstKeyField != null ? firstKeyField.getString("javaFieldName") : null;
            }
            table.setKeyProperty(table.isCompositeKey() || StringUtil.isEmpty(keyFieldName) ? "id" : keyFieldName);

            // @Id 字段类型：单键 = keyFields[0].javaType（POJO 字段类型，可能与 cache keyType 不同），
            // 复合键 = Key 类（与 keyType 相同）
            table.setKeyFieldType(table.isCompositeKey() ? table.getKeyType()
                    : resolveKeyFieldType(modelKeyFields, table.getKeyType()));

            // 没有 databaseTable 的纯 Ignite 模型：用 valueType 简单类名（首字母小写）当表名，
            // GenTableColumnMapperImpl 按同一规则反查，保证这类模型不会被静默跳过
            table.setTableName(GenUtils.deriveTableName(model.getString("databaseTable"), valueType));

            String tableComment = model.getString("tableComment");
            table.setTableComment(StringUtil.isNotEmpty(tableComment) ? tableComment : GenUtils.simpleName(valueType));

            table.setCacheName(resolveCacheName(cacheList, model, valueType));

            GenUtils.initTable(table,author,config);
            table.setClusterName(clusterName);
            tables.add(table);
        }
        return tables;
    }

    /**
     * 前端视图目录用的集群名：优先 context.name（如 user_db），
     * 缺失时退回全局包名的最后一段（org.platform.simple_test -&gt; simple_test），
     * 再不行用 "cluster"。结果经 {@link GenUtils#sanitizeDirName} 清洗。
     */
    private static String resolveClusterName(GenConfig config, Map<String, Object> context) {
        Object name = context.get("name");
        if (name != null && !name.toString().trim().isEmpty()) {
            return GenUtils.sanitizeDirName(name.toString().trim());
        }
        String packageName = config.getPackageName();
        if (StringUtil.isNotEmpty(packageName)) {
            int dot = packageName.lastIndexOf('.');
            return GenUtils.sanitizeDirName(dot >= 0 ? packageName.substring(dot + 1) : packageName);
        }
        return "cluster";
    }

    /**
     * 反查该模型挂载的 cache：cacheList 中 domains 包含模型 id 的那个 cache 的 name。
     */
    private String resolveCacheName(JsonObject cacheList, JsonObject model, String valueType) {
        String modelId = model.getString("id");
        if (cacheList != null && modelId != null) {
            for (String cacheName : cacheList.fieldNames()) {
                JsonObject cache = cacheList.getJsonObject(cacheName);
                if (cache == null) {
                    continue;
                }
                Object domains = cache.getValue("domains");
                if (domains instanceof JsonArray arr && arr.contains(modelId)) {
                    return cacheName;
                }
                if (domains instanceof List<?> list && list.contains(modelId)) {
                    return cacheName;
                }
            }
        }
        // 兜底：{简单类名}Cache
        return GenUtils.simpleName(valueType) + "Cache";
    }

    /** 单键模型 @Id 字段的 java 类型（keyFields[0].javaType，原始类型归一化为包装类型）。 */
    private static String resolveKeyFieldType(JsonArray keyFields, String fallback) {
        if (keyFields != null && keyFields.size() == 1) {
            JsonObject firstKeyField = keyFields.getJsonObject(0);
            String javaType = firstKeyField != null ? firstKeyField.getString("javaType") : null;
            if (StringUtil.isNotEmpty(javaType)) {
                return GenUtils.boxJavaType(javaType);
            }
        }
        return fallback;
    }
}
