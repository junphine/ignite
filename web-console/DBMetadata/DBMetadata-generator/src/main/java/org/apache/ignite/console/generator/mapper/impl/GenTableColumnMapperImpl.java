package org.apache.ignite.console.generator.mapper.impl;

import org.apache.ignite.console.generator.common.utils.GenUtils;
import org.apache.ignite.console.generator.common.utils.StringUtil;
import org.apache.ignite.console.generator.domain.GenTableColumn;
import org.apache.ignite.console.generator.mapper.GenTableColumnMapper;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 context 中的 Ignite Web Console 模型配置（modelList）构建 {@link GenTableColumn} 列表。
 * <p>
 * 列顺序 = keyFields（主键）+ valueFields（普通字段）；字段注释取 model.fields 中同名字段的 comment；
 * javaType 直接采用模型配置，数据库类型做小写规范化后交给 {@code GenUtils.initColumnField}
 * 推导 htmlType/queryType 等表单标志位。
 */
public class GenTableColumnMapperImpl implements GenTableColumnMapper {

    @Override
    public List<GenTableColumn> selectDbTableColumnsByName(String tableName, Map<String, Object> context) {
        List<GenTableColumn> columns = new ArrayList<>();
        Object modelListObj = context.get("modelList");
        if (!(modelListObj instanceof JsonObject modelList)) {
            return columns;
        }
        JsonObject model = findModelByTable(modelList, tableName);
        if (model == null) {
            return columns;
        }

        // name -> comment（model.fields 中的中文注释）
        Map<String, String> comments = new HashMap<>();
        JsonArray fields = model.getJsonArray("fields");
        if (fields != null) {
            for (int i = 0; i < fields.size(); i++) {
                JsonObject field = fields.getJsonObject(i);
                if (field == null) {
                    continue;
                }
                String name = field.getString("name");
                String comment = field.getString("comment");
                if (name != null && comment != null) {
                    comments.put(name, comment);
                }
            }
        }

        String keyFieldName = model.getString("keyFieldName");

        // 主键列在前，普通列在后
        JsonArray keyFields = model.getJsonArray("keyFields");
        boolean compositeKey = keyFields != null && keyFields.size() > 1;
        JsonArray valueFields = model.getJsonArray("valueFields");
        List<JsonObject> defs = new ArrayList<>();
        collect(defs, keyFields);
        collect(defs, valueFields);

        for (JsonObject def : defs) {
            GenTableColumn column = new GenTableColumn();
            String databaseFieldName = def.getString("databaseFieldName");
            String javaFieldName = def.getString("javaFieldName");
            boolean pk = (javaFieldName != null && javaFieldName.equals(keyFieldName)) || isKeyField(keyFields, def);

            String javaType = def.getString("javaType");
            if (pk && !compositeKey) {
                // 单键：主键类型以模型顶层 keyType 为准（与 fields[].className 一致，
                // keyFields[].javaType 在个别模型上与之矛盾），缺省再退回 keyFields 的配置；
                // 复合键：keyType 是整个 Key 类（如 com.lr.drupal.model.ProfileValuesKey），
                // 各键字段保持 keyFields 自己的 javaType（fid/uid 均为 Long）
                String keyType = model.getString("keyType");
                if (StringUtil.isNotEmpty(keyType)) {
                    javaType = keyType;
                }
            }

            column.setColumnName(databaseFieldName);
            column.setJavaField(javaFieldName);
            column.setJavaType(GenUtils.boxJavaType(javaType));
            column.setColumnType(normalizeDbType(def.getString("databaseFieldType")));
            column.setIsPk(pk ? "1" : "0");

            String comment = javaFieldName != null ? comments.get(javaFieldName) : null;
            column.setColumnComment(comment != null ? comment : "");

            columns.add(column);
        }
        return columns;
    }

    private static void collect(List<JsonObject> target, JsonArray array) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.size(); i++) {
            JsonObject item = array.getJsonObject(i);
            if (item != null) {
                target.add(item);
            }
        }
    }

    private static boolean isKeyField(JsonArray keyFields, JsonObject def) {
        if (keyFields == null) {
            return false;
        }
        String databaseFieldName = def.getString("databaseFieldName");
        for (int i = 0; i < keyFields.size(); i++) {
            JsonObject keyField = keyFields.getJsonObject(i);
            if (keyField != null && keyField.getString("databaseFieldName") != null
                    && keyField.getString("databaseFieldName").equals(databaseFieldName)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject findModelByTable(JsonObject modelList, String tableName) {
        if (tableName == null) {
            return null;
        }
        // 先全部按真实 databaseTable 精确匹配，再按“没有 databaseTable 时的推导表名”
        // （与 GenMapperImpl.setTableName 同一规则）匹配，避免纯 Ignite 模型被静默跳过
        JsonObject fallback = null;
        for (String valueType : modelList.fieldNames()) {
            JsonObject model = modelList.getJsonObject(valueType);
            if (model == null) {
                continue;
            }
            if (tableName.equals(model.getString("databaseTable"))) {
                return model;
            }
        }
        for (String valueType : modelList.fieldNames()) {
            JsonObject model = modelList.getJsonObject(valueType);
            if (model != null && tableName.equals(GenUtils.deriveTableName(null, valueType))) {
                fallback = model;
                break;
            }
        }
        return fallback;
    }

    /**
     * 数据库类型小写规范化，并把 JDBC 风格类型映射到 GenUtils 认识的 SQL 类型
     * （GenUtils.COLUMNTYPE_* 全部是小写匹配）。
     */
    private static String normalizeDbType(String databaseFieldType) {
        if (databaseFieldType == null) {
            return "";
        }
        String type = databaseFieldType.trim().toLowerCase();
        return switch (type) {
            case "longvarchar", "clob" -> "longtext";
            case "varchar2", "nvarchar" -> "varchar";
            case "int" -> "integer";
            case "bool" -> "bit";
            case "real" -> "float";
            default -> type;
        };
    }
}
