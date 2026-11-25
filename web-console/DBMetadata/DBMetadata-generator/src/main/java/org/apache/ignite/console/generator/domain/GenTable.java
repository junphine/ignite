package org.apache.ignite.console.generator.domain;

import org.apache.ignite.console.generator.common.constants.GenConstants;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

public class GenTable extends BaseEntity {
    private static final long serialVersionUID = 1L;

    private Long tableId;

    private String tableName;

    private String tableComment;

    private String subTableName;

    private String subTableFkName;

    private String className;

    private String tplCategory = "crud";

    private String packageName;

    private String moduleName;

    private String urlPrefix = "api"; // Rest服务前缀

    private String layout = "admin"; // UI,基于路径匹配的Layout,可以说admin,pages,settings

    private String businessName;

    private String functionName; // 功能名

    private String functionAuthor; // 功能实现作者

    private String genType;

    private String genPath;

    private GenTableColumn pkColumn;

    /** 关联的 Ignite cache 名称（来自 caches 配置，如 UcZonesCache） */
    private String cacheName;

    /** 模型 valueType（如 com.lr.drupal.model.UcZones），packageName 的来源 */
    private String valueType;

    /** 模型 keyType：单键时为键字段类型（如 Integer），复合键时为 Key 类全限定名（= cache keyType） */
    private String keyType;

    /**
     * model 类上 @Id 字段（属性）名：单键 = keyFieldName（如 uid），
     * 复合键 = Ignite Web Console 生成 POJO 时的约定名 "id"。
     */
    private String keyProperty;

    /**
     * model 类上 @Id 字段的类型：单键取 keyFields[0].javaType（如 Long），
     * 复合键即 keyType（Key 类）。与 {@link #keyType}（cache keyType）可能不一致。
     */
    private String keyFieldType;

    /** 是否复合主键（keyFields 多于一个字段） */
    private boolean compositeKey;

    /** @RepositoryConfig 的 autoCreateCache 属性值（来自 GenConfig） */
    private boolean autoCreateCache;

    /**
     * 模型类所在 Maven 工程坐标 {@code groupId:artifactId:version}（可选，来自 GenConfig）。
     * 不生成 entity 后生成的代码引用已有 model 类，写进生成的 pom 才能独立编译。
     */
    private String modelDependency;

    /**
     * 集群名（context.name，如 user_db），已把非法路径字符替换为 '_'。
     * 用作前端视图的输出目录：frontend/views/{clusterName}/。
     */
    private String clusterName;

    /** 是否生成前端 Hilla 视图（context.generateFrontend，默认 true） */
    private boolean generateFrontend = true;

    private GenTable subTable;

    private List<GenTableColumn> columns;

    private String options;

    private String treeCode;

    private String treeParentCode;

    private String treeName;

    private String parentMenuId;

    private String parentMenuName;

    public String getUrlPrefix() {
        return this.urlPrefix;
    }

    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public Long getTableId() {
        return this.tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public String getTableName() {
        return this.tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableComment() {
        return this.tableComment;
    }

    public void setTableComment(String tableComment) {
        this.tableComment = tableComment;
    }

    public String getSubTableName() {
        return this.subTableName;
    }

    public void setSubTableName(String subTableName) {
        this.subTableName = subTableName;
    }

    public String getSubTableFkName() {
        return this.subTableFkName;
    }

    public void setSubTableFkName(String subTableFkName) {
        this.subTableFkName = subTableFkName;
    }

    public String getClassName() {
        return this.className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getTplCategory() {
        return this.tplCategory;
    }

    public void setTplCategory(String tplCategory) {
        this.tplCategory = tplCategory;
    }

    public String getPackageName() {
        return this.packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getModuleName() {
        return this.moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getBusinessName() {
        return this.businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getFunctionName() {
        return this.functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getFunctionAuthor() {
        return this.functionAuthor;
    }

    public void setFunctionAuthor(String functionAuthor) {
        this.functionAuthor = functionAuthor;
    }

    public String getGenType() {
        return this.genType;
    }

    public void setGenType(String genType) {
        this.genType = genType;
    }

    public String getGenPath() {
        return this.genPath;
    }

    public void setGenPath(String genPath) {
        this.genPath = genPath;
    }

    public GenTableColumn getPkColumn() {
        return this.pkColumn;
    }

    public void setPkColumn(GenTableColumn pkColumn) {
        this.pkColumn = pkColumn;
    }

    public String getCacheName() {
        return this.cacheName;
    }

    public void setCacheName(String cacheName) {
        this.cacheName = cacheName;
    }

    public String getValueType() {
        return this.valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getKeyType() {
        return this.keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getKeyProperty() {
        return this.keyProperty;
    }

    public void setKeyProperty(String keyProperty) {
        this.keyProperty = keyProperty;
    }

    public String getKeyFieldType() {
        return this.keyFieldType;
    }

    public void setKeyFieldType(String keyFieldType) {
        this.keyFieldType = keyFieldType;
    }

    public boolean isCompositeKey() {
        return this.compositeKey;
    }

    public void setCompositeKey(boolean compositeKey) {
        this.compositeKey = compositeKey;
    }

    public boolean isAutoCreateCache() {
        return this.autoCreateCache;
    }

    public void setAutoCreateCache(boolean autoCreateCache) {
        this.autoCreateCache = autoCreateCache;
    }

    public String getModelDependency() {
        return this.modelDependency;
    }

    public void setModelDependency(String modelDependency) {
        this.modelDependency = modelDependency;
    }

    public String getClusterName() {
        return this.clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public boolean isGenerateFrontend() {
        return this.generateFrontend;
    }

    public void setGenerateFrontend(boolean generateFrontend) {
        this.generateFrontend = generateFrontend;
    }

    public GenTable getSubTable() {
        return this.subTable;
    }

    public void setSubTable(GenTable subTable) {
        this.subTable = subTable;
    }

    public List<GenTableColumn> getColumns() {
        return this.columns;
    }

    public void setColumns(List<GenTableColumn> columns) {
        this.columns = columns;
    }

    public String getOptions() {
        return this.options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getTreeCode() {
        return this.treeCode;
    }

    public void setTreeCode(String treeCode) {
        this.treeCode = treeCode;
    }

    public String getTreeParentCode() {
        return this.treeParentCode;
    }

    public void setTreeParentCode(String treeParentCode) {
        this.treeParentCode = treeParentCode;
    }

    public String getTreeName() {
        return this.treeName;
    }

    public void setTreeName(String treeName) {
        this.treeName = treeName;
    }

    public String getParentMenuId() {
        return this.parentMenuId;
    }

    public void setParentMenuId(String parentMenuId) {
        this.parentMenuId = parentMenuId;
    }

    public String getParentMenuName() {
        return this.parentMenuName;
    }

    public void setParentMenuName(String parentMenuName) {
        this.parentMenuName = parentMenuName;
    }

    public boolean isSub() {
        return isSub(this.tplCategory);
    }

    public static boolean isSub(String tplCategory) {
        return (tplCategory != null && StringUtils.equals("sub", tplCategory));
    }

    public boolean isTree() {
        return isTree(this.tplCategory);
    }

    public static boolean isTree(String tplCategory) {
        return (tplCategory != null && StringUtils.equals("tree", tplCategory));
    }

    public boolean isCrud() {
        return isCrud(this.tplCategory);
    }

    public static boolean isCrud(String tplCategory) {
        return (tplCategory != null && StringUtils.equals("crud", tplCategory));
    }

    public boolean isSuperColumn(String javaField) {
        return isSuperColumn(this.tplCategory, javaField);
    }

    public static boolean isSuperColumn(String tplCategory, String javaField) {
        if (isTree(tplCategory))
            return StringUtils.equalsAnyIgnoreCase(javaField,
                    (CharSequence[])ArrayUtils.addAll((Object[]) GenConstants.TREE_ENTITY, (Object[])GenConstants.BASE_ENTITY));
        return StringUtils.equalsAnyIgnoreCase(javaField, (CharSequence[])GenConstants.BASE_ENTITY);
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }
}
