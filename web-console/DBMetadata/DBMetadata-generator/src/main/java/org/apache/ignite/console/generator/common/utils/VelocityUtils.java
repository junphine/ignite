package org.apache.ignite.console.generator.common.utils;

import org.apache.ignite.console.generator.domain.GenTable;
import org.apache.ignite.console.generator.domain.GenTableColumn;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.vertx.core.json.JsonObject;
import org.apache.velocity.VelocityContext;

public class VelocityUtils {
    private static final String PROJECT_PATH = "main/java";

    public static VelocityContext prepareContext(GenTable genTable) {
        String moduleName = genTable.getModuleName();
        String businessName = genTable.getBusinessName();
        String packageName = genTable.getPackageName();
        String tplCategory = genTable.getTplCategory();
        String functionName = genTable.getFunctionName();
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("tplCategory", genTable.getTplCategory());
        velocityContext.put("tableName", genTable.getTableName());
        velocityContext.put("functionName", StringUtil.isNotEmpty(functionName) ? functionName : "【请填写功能名称】");
                velocityContext.put("ClassName", genTable.getClassName());
        velocityContext.put("className", StringUtil.uncapitalize(genTable.getClassName()));
        velocityContext.put("moduleName", genTable.getModuleName());
        velocityContext.put("BusinessName", StringUtil.capitalize(genTable.getBusinessName()));
        velocityContext.put("businessName", genTable.getBusinessName());
        velocityContext.put("urlPrefix", genTable.getUrlPrefix());
        velocityContext.put("layout", genTable.getLayout());
        velocityContext.put("basePackage", getPackagePrefix(packageName));
        velocityContext.put("packageName", packageName);
        velocityContext.put("author", genTable.getFunctionAuthor());
        velocityContext.put("datetime", DateUtil.getDate());
        velocityContext.put("pkColumn", genTable.getPkColumn());
        velocityContext.put("compositeKey", genTable.isCompositeKey());

        // ---- 主键：缓存 keyType（仓库/服务的键类型）与 model 类 @Id 字段的类型可能不同 ----
        // 例：Users 的 keyType=Integer（cache），但 model 上是 long uid
        String pkJavaType = resolvePkJavaType(genTable);
        String keyProperty = resolveKeyProperty(genTable);
        String keyFieldType = resolveKeyFieldType(genTable, pkJavaType);
        velocityContext.put("pkJavaType", pkJavaType);
        velocityContext.put("keyProperty", keyProperty);
        velocityContext.put("capKeyProperty", StringUtil.capitalize(keyProperty));
        velocityContext.put("keyFieldType", keyFieldType);
        // getKeyOf(model)：model 的 @Id 字段 -> 缓存 key（必要时做类型转换）
        velocityContext.put("keyOfExpr", buildKeyOfExpr(genTable, "model", keyProperty, pkJavaType, keyFieldType));
        // save 时 key 是否视为未分配（0/null/空串 -> 服务端生成）
        velocityContext.put("absentKeyExpr", buildAbsentKeyExpr(pkJavaType));
        // nextKey()：基于已有数据 max+1 的新 key 表达式（局部变量 max 类型为 long）
        velocityContext.put("newKeyExpr", buildNewKeyExpr(pkJavaType));
        velocityContext.put("stringKey", "String".equals(GenUtils.boxJavaType(pkJavaType)));
        velocityContext.put("numericKey", GenUtils.isNumericJavaType(pkJavaType));

        velocityContext.put("pkRequestParamDecl", buildPkRequestParamDecl(genTable));
        velocityContext.put("pkConstructorArgs", buildPkConstructorArgs(genTable));
        velocityContext.put("autoCreateCache", genTable.isAutoCreateCache());
        // 模型工程坐标（可选，groupId:artifactId:version）：不生成 entity 后，生成的 pom 靠它解析已有 model 类
        String[] modelGav = splitModelDependency(genTable.getModelDependency());
        velocityContext.put("modelGroupId", modelGav[0]);
        velocityContext.put("modelArtifactId", modelGav[1]);
        velocityContext.put("modelVersion", modelGav[2]);
        // 已有 model 类（不生成 entity）：valueType 全限定名 / 类型简单名 / 是否需要 import
        String valueType = genTable.getValueType();
        velocityContext.put("valueType", valueType);
        velocityContext.put("modelType", resolveModelType(genTable));
        velocityContext.put("hasValuePackage", valueType != null && valueType.indexOf('.') > 0);
        // ---- 前端（Hilla React）视图：frontend/views/{集群名}/{tableName}.tsx ----
        // 集群名：目录段（context.name，非法路径字符已替换）
        velocityContext.put("clusterName", genTable.getClusterName());
        // @BrowserCallable 所在类 = 端点名（Hilla 按类的 simple name 注册，前端从 endpoints 里 import）
        velocityContext.put("endpointName", genTable.getClassName() + "ServiceImpl");
        // Hilla TS Model 路径：com.lr.drupal.model.Users -> Frontend/generated/com/lr/drupal/model/UsersModel
        velocityContext.put("tsModelPath", "Frontend/generated/"
                + (valueType == null ? "" : valueType.replace('.', '/')) + "Model");
        // Grid 列（含主键）与表单/value 属性列表（不含主键：model 的 key 字段没有 setter）
        velocityContext.put("gridColumns", buildPropertyList(genTable, false));
        velocityContext.put("valueProperties", buildPropertyList(genTable, true));
        velocityContext.put("permissionPrefix", getPermissionPrefix(moduleName, businessName));
        velocityContext.put("columns", genTable.getColumns());
        velocityContext.put("table", genTable);
        velocityContext.put("dicts", getDicts(genTable));
        setMenuVelocityContext(velocityContext, genTable);
        if ("tree".equals(tplCategory))
            setTreeVelocityContext(velocityContext, genTable);
        if ("sub".equals(tplCategory))
            setSubVelocityContext(velocityContext, genTable);
        return velocityContext;
    }

    /**
     * 模型工程坐标 {@code groupId:artifactId:version} → [groupId, artifactId, version]。
     * 未配置或格式不对 → 三个空串（生成的 pom 里只留说明，不输出依赖）。
     */
    private static String[] splitModelDependency(String modelDependency) {
        String[] empty = { "", "", "" };
        if (StringUtil.isEmpty(modelDependency)) {
            return empty;
        }
        String[] parts = modelDependency.trim().split(":");
        if (parts.length != 3 || parts[0].trim().isEmpty() || parts[1].trim().isEmpty() || parts[2].trim().isEmpty()) {
            System.err.println("[WARN] modelDependency 应为 groupId:artifactId:version，已忽略：" + modelDependency);
            return empty;
        }
        return new String[] { parts[0].trim(), parts[1].trim(), parts[2].trim() };
    }

    /**
     * 引用的外部 model 类简单名：valueType 有包名时取最后一段，否则退回表名驼峰类名。
     */
    private static String resolveModelType(GenTable genTable) {
        String valueType = genTable.getValueType();
        if (StringUtil.isNotEmpty(valueType)) {
            int lastDot = valueType.lastIndexOf('.');
            return lastDot >= 0 ? valueType.substring(lastDot + 1) : valueType;
        }
        return genTable.getClassName();
    }

    /**
     * 服务/仓库使用的键类型（{@code CrudService<T, ID>} 的 ID）：
     * 复合键 = Key 类全限定名（= cache keyType），单键 = 主键列 javaType（来自模型 keyType）。
     */
    private static String resolvePkJavaType(GenTable genTable) {
        if (genTable.isCompositeKey()) {
            return genTable.getKeyType();
        }
        GenTableColumn pkColumn = genTable.getPkColumn();
        if (pkColumn != null && StringUtil.isNotEmpty(pkColumn.getJavaType())) {
            return pkColumn.getJavaType();
        }
        return genTable.getKeyType() != null ? genTable.getKeyType() : "String";
    }

    /**
     * model 类上 @Id 字段（属性）名：复合键 = Web Console 生成 Key 类 POJO 的约定名 "id"，
     * 单键 = keyFieldName（如 uid/fid/zoneId）。
     */
    private static String resolveKeyProperty(GenTable genTable) {
        if (StringUtil.isNotEmpty(genTable.getKeyProperty())) {
            return genTable.getKeyProperty();
        }
        if (genTable.isCompositeKey()) {
            return "id";
        }
        GenTableColumn pkColumn = genTable.getPkColumn();
        return pkColumn != null && StringUtil.isNotEmpty(pkColumn.getJavaField()) ? pkColumn.getJavaField() : "id";
    }

    /** model 类上 @Id 字段的类型（单键常与 cache keyType 不同，如 long uid vs Integer）。 */
    private static String resolveKeyFieldType(GenTable genTable, String pkJavaType) {
        return StringUtil.isNotEmpty(genTable.getKeyFieldType()) ? genTable.getKeyFieldType() : pkJavaType;
    }

    /**
     * {@code getKeyOf(model)} 的取值表达式：model 的 @Id 字段 -> 缓存 key。
     * <ul>
     *   <li>同类型：直接 getter</li>
     *   <li>长整型收窄到 Integer：{@code Math.toIntExact(...)}（静默截断比抛异常更糟）</li>
     *   <li>其它数字互转：显式强转原始类型</li>
     * </ul>
     */
    private static String buildKeyOfExpr(GenTable genTable, String var, String keyProperty, String target,
            String source) {
        String getter = var + ".get" + StringUtil.capitalize(keyProperty) + "()";
        String targetBoxed = GenUtils.boxJavaType(target);
        String sourceBoxed = GenUtils.boxJavaType(source);
        if (genTable.isCompositeKey() || target == null || source == null || targetBoxed.equals(sourceBoxed)) {
            return getter;
        }
        if ("Integer".equals(targetBoxed) && GenUtils.isIntegralJavaType(sourceBoxed)) {
            return "Math.toIntExact(" + getter + ")";
        }
        String primitive = GenUtils.primitiveOf(target);
        if (primitive != null && GenUtils.isNumericJavaType(source)) {
            return "((" + primitive + ") " + getter + ")";
        }
        return "((" + target + ") " + getter + ")";
    }

    /** save 时判断 key 是否“未分配”的表达式（变量名为 {@code key}）。 */
    private static String buildAbsentKeyExpr(String pkJavaType) {
        String boxed = GenUtils.boxJavaType(pkJavaType);
        if ("String".equals(boxed)) {
            return "key == null || key.isEmpty()";
        }
        if (GenUtils.isNumericJavaType(pkJavaType)) {
            return "key == null || key.longValue() == 0L";
        }
        return "key == null";
    }

    /**
     * {@code nextKey()} 的返回表达式，基于局部变量 {@code long max}（已有数据的最大 key）。
     * 不用每 JVM 自增序列：那样会与已加载的存量数据撞 key。
     */
    private static String buildNewKeyExpr(String pkJavaType) {
        return switch (GenUtils.boxJavaType(pkJavaType)) {
            case "Integer" -> "Math.toIntExact(max + 1)";
            case "Short" -> "((short) (max + 1))";
            case "Byte" -> "((byte) (max + 1))";
            case "Float" -> "((float) (max + 1))";
            case "BigDecimal" -> "java.math.BigDecimal.valueOf(max + 1)";
            default -> "max + 1";
        };
    }

    /**
     * {@code "a", "b", "c"} 形式的属性列表：Grid.setColumns / 表单 setVisibleProperties 用。
     *
     * @param excludePk true 时跳过主键列（表单与复合键的 value 列）
     */
    private static String buildPropertyList(GenTable genTable, boolean excludePk) {
        List<GenTableColumn> columns = genTable.getColumns();
        if (columns == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (GenTableColumn column : columns) {
            if (excludePk && column.isPk()) {
                continue;
            }
            if (StringUtil.isEmpty(column.getJavaField())) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('"').append(column.getJavaField()).append('"');
        }
        return sb.toString();
    }

    /**
     * 复合键的请求参数声明：@RequestParam("fid") Long fid, @RequestParam("uid") Long uid。
     */
    private static String buildPkRequestParamDecl(GenTable genTable) {
        List<GenTableColumn> columns = genTable.getColumns();
        if (columns == null) {
            return "";
        }
        StringBuilder decl = new StringBuilder();
        for (GenTableColumn column : columns) {
            if (column.isPk()) {
                if (decl.length() > 0) {
                    decl.append(", ");
                }
                decl.append("@RequestParam(\"").append(column.getJavaField()).append("\") ")
                        .append(column.getJavaType()).append(' ').append(column.getJavaField());
            }
        }
        return decl.toString();
    }

    /**
     * 复合键 Key 构造的裸参数名：fid, uid（与 {@link #buildPkRequestParamDecl} 对应）。
     */
    private static String buildPkConstructorArgs(GenTable genTable) {
        List<GenTableColumn> columns = genTable.getColumns();
        if (columns == null) {
            return "";
        }
        StringBuilder args = new StringBuilder();
        for (GenTableColumn column : columns) {
            if (column.isPk()) {
                if (args.length() > 0) {
                    args.append(", ");
                }
                args.append(column.getJavaField());
            }
        }
        return args.toString();
    }

    public static void setMenuVelocityContext(VelocityContext context, GenTable genTable) {
        String options = genTable.getOptions();
        JsonObject paramsObj = new JsonObject(StringUtil.isNotEmpty(options) ? options : "{}");
        String parentMenuId = getParentMenuId(paramsObj);
        context.put("parentMenuId", parentMenuId);
    }

    public static void setTreeVelocityContext(VelocityContext context, GenTable genTable) {
        String options = genTable.getOptions();
        JsonObject paramsObj = new JsonObject(options);
        String treeCode = getTreecode(paramsObj);
        String treeParentCode = getTreeParentCode(paramsObj);
        String treeName = getTreeName(paramsObj);
        context.put("treeCode", treeCode);
        context.put("treeParentCode", treeParentCode);
        context.put("treeName", treeName);
        context.put("expandColumn", Integer.valueOf(getExpandColumn(genTable)));
        if (paramsObj.containsKey("treeParentCode"))
            context.put("tree_parent_code", paramsObj.getString("treeParentCode"));
        if (paramsObj.containsKey("treeName"))
            context.put("tree_name", paramsObj.getString("treeName"));
    }

    public static void setSubVelocityContext(VelocityContext context, GenTable genTable) {
        GenTable subTable = genTable.getSubTable();
        String subTableName = genTable.getSubTableName();
        String subTableFkName = genTable.getSubTableFkName();
        String subClassName = genTable.getSubTable().getClassName();
        String subTableFkClassName = StringUtil.convertToCamelCase(subTableFkName);
        context.put("subTable", subTable);
        context.put("subTableName", subTableName);
        context.put("subTableFkName", subTableFkName);
        context.put("subTableFkClassName", subTableFkClassName);
        context.put("subTableFkclassName", StringUtil.uncapitalize(subTableFkClassName));
        context.put("subClassName", subClassName);
        context.put("subclassName", StringUtil.uncapitalize(subClassName));
        context.put("subImportList", getImportList(genTable.getSubTable()));
    }

    /**
     * Vaadin Flow + crudui 最小闭环：IgniteRepository / Service 接口 / Service 实现 /
     * GridCrud 视图 / @RestController CRUD API；
     * 另有 Hilla React 视图（{@code vm/frontend/}，输出到 frontend/views/{集群名}/，
     * 由 {@code context.generateFrontend=false} 关闭）。
     * <p>
     * 不生成 entity —— 直接引用外部已有 model 类（valueType）；
     * Key/Value 分离：key 作为独立参数传递（saveById(id, model)），service 继承 Hilla CrudService
     * 并带 {@code @BrowserCallable}（供生成的前端 AutoCrud 调用）。
     * （工程级文件见 {@link #getProjectTemplateList()}）
     */
    public static List<String> getTemplateList(String tplCategory) {
        List<String> templates = new ArrayList<>();
        templates.add("vm/java/repository.vm.java");
        templates.add("vm/java/service.vm.java");
        templates.add("vm/java/serviceImpl.vm.java");
        templates.add("vm/ui/crudview.vm.java");
        templates.add("vm/java/controller.vm.java");
        templates.add("vm/frontend/crudview.vm.tsx");
        templates.add("vm/frontend/@layout.tsx");
        return templates;
    }

    /**
     * 是否是前端（Hilla React）模板：context.generateFrontend=false 时整类过滤掉。
     */
    public static boolean isFrontendTemplate(String template) {
        return template.startsWith("vm/frontend/");
    }

    /**
     * 工程级文件模板：UiPlugin 实现类、META-INF/services 描述符、pom.xml —— 每棵生成树只渲染一次。
     */
    public static List<String> getProjectTemplateList() {
        List<String> templates = new ArrayList<>();
        templates.add("vm/plugin/uiplugin.vm.java");
        templates.add("vm/plugin/uip-services.vm");
        templates.add("vm/xml/pom.vm.xml");
        return templates;
    }

    /**
     * 工程级模板的输出路径（与表无关，仅根包 ${packageName} 参与）。
     */
    public static String getProjectFileName(String template, GenTable genTable) {
        String javaPath = "src/main/java/" + StringUtil.replace(genTable.getPackageName(), ".", "/");
        switch (template) {
            case "vm/plugin/uiplugin.vm.java":
                return javaPath + "/CrudUiPlugin.java";
            case "vm/plugin/uip-services.vm":
                return "src/main/resources/META-INF/services/org.vaadin.crudui.plugin.UiPlugin";
            case "vm/xml/pom.vm.xml":
                return "pom.xml";
            default:
                return "";
        }
    }

    public static String getFileName(String template, GenTable genTable) {
        String packageName = genTable.getPackageName();
        String className = genTable.getClassName();
        String tableName = genTable.getTableName();
        String cluster = genTable.getClusterName();
        if (StringUtil.isEmpty(cluster)) {
            cluster = "cluster";
        }
        String javaPath = "src/main/java/" + StringUtil.replace(packageName, ".", "/");
        // Hilla React 视图（前端）：frontend/views/{集群名}/{tableName}.tsx（文件名 = 表名，URL 即 /集群/表名）
        if (template.contains("/frontend/crudview.vm.tsx")) {
            return String.format("frontend/views/%s/%s.tsx", cluster, tableName);
        }
        if (template.contains("/frontend/@layout.tsx")) {
            return String.format("frontend/views/%s/@layout.tsx", cluster);
        }
        if (template.contains("repository.vm.java"))
            return StringUtil.format("{}/repository/{}Repository.java", new Object[] { javaPath, className });
        if (template.contains("serviceImpl.vm.java"))
            return StringUtil.format("{}/service/impl/{}ServiceImpl.java", new Object[] { javaPath, className });
        if (template.contains("service.vm.java"))
            return StringUtil.format("{}/service/{}Service.java", new Object[] { javaPath, className });
        if (template.contains("crudview.vm.java"))
            return StringUtil.format("{}/ui/view/{}View.java", new Object[] { javaPath, className });
        if (template.contains("controller.vm.java"))
            return StringUtil.format("{}/controller/{}Controller.java", new Object[] { javaPath, className });
        return "";
    }

    public static String getPackagePrefix(String packageName) {
        int lastIndex = packageName.lastIndexOf(".");
        return StringUtil.substring(packageName, 0, lastIndex);
    }

    public static HashSet<String> getImportList(GenTable genTable) {
        List<GenTableColumn> columns = genTable.getColumns();
        GenTable subGenTable = genTable.getSubTable();
        HashSet<String> importList = new HashSet<>();
        if (StringUtil.isNotNull(subGenTable))
            importList.add("java.util.List");
        for (GenTableColumn column : columns) {
            String javaType = column.getJavaType();
            if (javaType == null)
                continue;
            if ("Date".equals(javaType)) {
                importList.add("java.util.Date");
                continue;
            }
            if (!column.isSuperColumn() && "BigDecimal".equals(javaType))
                importList.add("java.math.BigDecimal");
        }
        return importList;
    }

    public static String getDicts(GenTable genTable) {
        List<GenTableColumn> columns = genTable.getColumns();
        Set<String> dicts = new HashSet<>();
        addDicts(dicts, columns);
        if (StringUtil.isNotNull(genTable.getSubTable())) {
            List<GenTableColumn> subColumns = genTable.getSubTable().getColumns();
            addDicts(dicts, subColumns);
        }
        return StringUtil.join(dicts, ", ");
    }

    public static void addDicts(Set<String> dicts, List<GenTableColumn> columns) {
        for (GenTableColumn column : columns) {
            if (!column.isSuperColumn() && StringUtil.isNotEmpty(column.getDictType()) && StringUtil.equalsAny(column
                    .getHtmlType(), (CharSequence[])new String[] { "select", "radio", "checkbox" }))
                dicts.add("'" + column.getDictType() + "'");
        }
    }

    public static String getPermissionPrefix(String moduleName, String businessName) {
        return StringUtil.format("{}:{}", new Object[] { moduleName, businessName });
    }

    public static String getParentMenuId(JsonObject paramsObj) {
        if (paramsObj != null && !paramsObj.isEmpty() && paramsObj.containsKey("parentMenuId") &&
                StringUtil.isNotEmpty(paramsObj.getString("parentMenuId")))
            return paramsObj.getString("parentMenuId");
        return "3";
    }

    public static String getTreecode(JsonObject paramsObj) {
        if (paramsObj.containsKey("treeCode"))
            return StringUtil.toCamelCase(paramsObj.getString("treeCode"));
        return "";
    }

    public static String getTreeParentCode(JsonObject paramsObj) {
        if (paramsObj.containsKey("treeParentCode"))
            return StringUtil.toCamelCase(paramsObj.getString("treeParentCode"));
        return "";
    }

    public static String getTreeName(JsonObject paramsObj) {
        if (paramsObj.containsKey("treeName"))
            return StringUtil.toCamelCase(paramsObj.getString("treeName"));
        return "";
    }

    public static int getExpandColumn(GenTable genTable) {
        String options = genTable.getOptions();
        JsonObject paramsObj = new JsonObject(options);
        String treeName = paramsObj.getString("treeName");
        int num = 0;
        for (GenTableColumn column : genTable.getColumns()) {
            if (column.isList()) {
                num++;
                String columnName = column.getColumnName();
                if (columnName.equals(treeName))
                    break;
            }
        }
        return num;
    }
}
