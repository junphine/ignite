package org.apache.ignite.console.generator.common.utils;

import org.apache.ignite.console.generator.common.config.GenConfig;
import org.apache.ignite.console.generator.common.constants.GenConstants;
import org.apache.ignite.console.generator.domain.GenTable;
import org.apache.ignite.console.generator.domain.GenTableColumn;
import java.util.Arrays;
import java.util.Date;

import org.apache.commons.lang3.RegExUtils;

public class GenUtils {

    public static void initTable(GenTable genTable, String operName,GenConfig genConfig) {
        genTable.setClassName(convertClassName(genTable.getTableName(),genConfig));
        String packageName = resolvePackageName(genTable.getValueType(), genConfig);
        genTable.setPackageName(packageName);
        genTable.setModuleName(getModuleName(packageName));
        genTable.setAutoCreateCache(genConfig.isAutoCreateCache());
        genTable.setModelDependency(genConfig.getModelDependency());
        genTable.setGenerateFrontend(genConfig.isGenerateFrontend());
        genTable.setBusinessName(StringUtil.toCamelCase(getBusinessName(genTable.getTableName())));
        genTable.setFunctionName(replaceText(genTable.getTableComment()));
        genTable.setFunctionAuthor(genConfig.getAuthor());
        genTable.setUrlPrefix(genConfig.getUrlPrefix());
        genTable.setGenPath(genConfig.getFileDownLoadPath());
        genTable.setCreateTime(new Date());
        genTable.setCreateBy(operName);
    }

    /**
     * 包名来源：模型 valueType 的包部分（如 com.lr.drupal.model.UcZones -> com.lr.drupal.model），
     * 但不包括 model 子包（-> com.lr.drupal）；valueType 为简短名（不含包）或过滤后为空时，
     * 退回全局配置的包名（genConfig.packageName）。
     */
    public static String resolvePackageName(String valueType, GenConfig genConfig) {
        if (StringUtil.isNotEmpty(valueType)) {
            int lastIndex = valueType.lastIndexOf('.');
            if (lastIndex > 0) {
                String pkg = valueType.substring(0, lastIndex);
                String filtered = stripModelPackage(pkg);
                if (StringUtil.isNotEmpty(filtered)) {
                    return filtered;
                }
            }
        }
        return genConfig.getPackageName();
    }

    /**
     * 去掉包路径中的 model 段：com.lr.drupal.model -&gt; com.lr.drupal。
     */
    private static String stripModelPackage(String pkg) {
        StringBuilder sb = new StringBuilder();
        for (String seg : pkg.split("\\.")) {
            if ("model".equals(seg)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(seg);
        }
        return sb.toString();
    }

    private static String convertComment(String comment) {
        if (StringUtil.isEmpty(comment))
            return "";
        return comment.replaceAll("\"", "").replaceAll("\n", "");
    }

    public static void initColumnField(GenTableColumn column, GenTable table) {
        column.setColumnComment(convertComment(column.getColumnComment()));
        String dataType = getDbType(column.getColumnType());
        String columnName = column.getColumnName();
        column.setTableId(table.getTableId());
        column.setCreateBy(table.getCreateBy());
        // 模型配置（Ignite Web Console）已给出 javaField/javaType 时优先采用，否则按数据库列推导
        boolean presetJavaField = StringUtil.isNotEmpty(column.getJavaField());
        boolean presetJavaType = StringUtil.isNotEmpty(column.getJavaType());
        if (!presetJavaField)
            column.setJavaField(StringUtil.toCamelCase(columnName));
        if (!presetJavaType)
            column.setJavaType("String");
        column.setQueryType("EQ");
        if (arraysContains(GenConstants.COLUMNTYPE_STR, dataType) || arraysContains(GenConstants.COLUMNTYPE_TEXT, dataType)) {
            Integer columnLength = getColumnLength(column.getColumnType());
            String htmlType = (columnLength.intValue() >= 500 || arraysContains(GenConstants.COLUMNTYPE_TEXT, dataType)) ? "textarea" : "input";
            column.setHtmlType(htmlType);
        } else if (arraysContains(GenConstants.COLUMNTYPE_TIME, dataType)) {
            if (!presetJavaType)
                column.setJavaType("Date");
            column.setHtmlType("datetime");
        } else if (arraysContains(GenConstants.COLUMNTYPE_NUMBER, dataType)) {
            column.setHtmlType("input");
            if (!presetJavaType) {
                String[] str = StringUtil.split(StringUtil.substringBetween(column.getColumnType(), "(", ")"), ",");
                if (str != null && str.length == 2 && Integer.parseInt(str[1]) > 0) {
                    column.setJavaType("BigDecimal");
                } else if (str != null && str.length == 1 && Integer.parseInt(str[0]) <= 10) {
                    column.setJavaType("Integer");
                } else {
                    column.setJavaType("Long");
                }
            }
        }
        column.setIsInsert("1");
        if (!arraysContains(GenConstants.COLUMNNAME_NOT_EDIT, columnName) && !column.isPk())
            column.setIsEdit("1");
        if (!arraysContains(GenConstants.COLUMNNAME_NOT_LIST, columnName) && !column.isPk())
            column.setIsList("1");
        if (!arraysContains(GenConstants.COLUMNNAME_NOT_QUERY, columnName) && !column.isPk())
            column.setIsQuery("1");
        if (StringUtil.endsWithIgnoreCase(columnName, "name"))
            column.setQueryType("LIKE");
        if (StringUtil.endsWithIgnoreCase(columnName, "status")) {
            column.setHtmlType("radio");
        } else if (StringUtil.endsWithIgnoreCase(columnName, "type") || StringUtil.endsWithIgnoreCase(columnName, "sex")) {
            column.setHtmlType("select");
        } else if (StringUtil.endsWithIgnoreCase(columnName, "image")) {
            column.setHtmlType("imageUpload");
        } else if (StringUtil.endsWithIgnoreCase(columnName, "file")) {
            column.setHtmlType("fileUpload");
        } else if (StringUtil.endsWithIgnoreCase(columnName, "content")) {
            column.setHtmlType("editor");
        }
    }

    /** 取类全限定名的简单类名（com.lr.drupal.model.UcZones -&gt; UcZones）。 */
    public static String simpleName(String className) {
        if (StringUtil.isEmpty(className)) {
            return "";
        }
        int idx = className.lastIndexOf('.');
        return idx >= 0 ? className.substring(idx + 1) : className;
    }

    /**
     * 模型的“表名”：优先用模型配置的 databaseTable；没有（纯 Ignite 模型）时
     * 用 valueType 简单类名的首字母小写形式（com.lr.drupal.model.UcZones -&gt; ucZones）。
     * {@code GenTableColumnMapperImpl} 反查模型时必须用同一个推导，否则这些模型会被静默跳过。
     */
    public static String deriveTableName(String databaseTable, String valueType) {
        if (StringUtil.isNotEmpty(databaseTable)) {
            return databaseTable;
        }
        return StringUtil.uncapitalize(simpleName(valueType));
    }

    /**
     * 集群名 -&gt; 前端视图输出目录名（{@code frontend/views/{...}}）：
     * 只保留 {@code [A-Za-z0-9_-]}，其余字符（路径分隔符、点号等）替换为 '_'，
     * 避免出现 {@code ../} 这类路径穿越；空值回退 "cluster"。
     */
    public static String sanitizeDirName(String value) {
        if (StringUtil.isEmpty(value)) {
            return "cluster";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean keep = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            sb.append(keep ? c : '_');
        }
        return sb.length() == 0 ? "cluster" : sb.toString();
    }

    public static boolean arraysContains(String[] arr, String targetValue) {
        return Arrays.<String>asList(arr).contains(targetValue);
    }

    /**
     * 原始类型归一化为包装类型（生成的 service 需要做 null 判断）；已包装或自定义类型原样返回。
     */
    public static String boxJavaType(String javaType) {
        if (javaType == null) {
            return null;
        }
        return switch (javaType) {
            case "int" -> "Integer";
            case "long" -> "Long";
            case "byte" -> "Byte";
            case "short" -> "Short";
            case "double" -> "Double";
            case "float" -> "Float";
            case "boolean" -> "Boolean";
            case "char" -> "Character";
            default -> javaType;
        };
    }

    /** 判断类型是否为数字类型（含原始/包装类型与 java.math.BigDecimal）。 */
    public static boolean isNumericJavaType(String javaType) {
        if (javaType == null) {
            return false;
        }
        return switch (boxJavaType(javaType)) {
            case "Integer", "Long", "Short", "Byte", "Double", "Float", "BigDecimal" -> true;
            default -> false;
        };
    }

    /** 判断类型是否为整数类型（原始/包装类型）。 */
    public static boolean isIntegralJavaType(String javaType) {
        if (javaType == null) {
            return false;
        }
        return switch (boxJavaType(javaType)) {
            case "Integer", "Long", "Short", "Byte" -> true;
            default -> false;
        };
    }

    /** 数字类型的原始类型名（Integer -&gt; int），非数字类型返回 null。 */
    public static String primitiveOf(String javaType) {
        return switch (boxJavaType(javaType)) {
            case "Integer" -> "int";
            case "Long" -> "long";
            case "Short" -> "short";
            case "Byte" -> "byte";
            case "Double" -> "double";
            case "Float" -> "float";
            default -> null;
        };
    }

    public static String getModuleName(String packageName) {
        int lastIndex = packageName.lastIndexOf(".");
        int nameLength = packageName.length();
        return StringUtil.substring(packageName, lastIndex + 1, nameLength);
    }

    public static String getBusinessName(String tableName) {
        int firstIndex = tableName.indexOf("_");
        int nameLength = tableName.length();
        return StringUtil.substring(tableName, firstIndex + 1, nameLength);
    }

    public static String convertClassName(String tableName,GenConfig genConfig) {
        boolean autoRemovePre = genConfig.getAutoRemovePre();
        String tablePrefix = genConfig.getTablePrefix();
        if (autoRemovePre && StringUtil.isNotEmpty(tablePrefix)) {
            String[] searchList = StringUtil.split(tablePrefix, ",");
            tableName = replaceFirst(tableName, searchList);
        }
        return StringUtil.convertToCamelCase(tableName);
    }

    public static String replaceFirst(String replacementm, String[] searchList) {
        String text = replacementm;
        for (String searchString : searchList) {
            if (replacementm.startsWith(searchString)) {
                text = replacementm.replaceFirst(searchString, "");
                break;
            }
        }
        return text;
    }

    public static String replaceText(String text) {
        return RegExUtils.replaceAll(text, "(?:表)", "");
    }

    public static String getDbType(String columnType) {
        if (StringUtil.indexOf(columnType, "(") > 0)
            return StringUtil.substringBefore(columnType, "(");
        return columnType;
    }

    public static Integer getColumnLength(String columnType) {
        if (StringUtil.indexOf(columnType, "(") > 0) {
            String length = StringUtil.substringBetween(columnType, "(", ")");
            return Integer.valueOf(length);
        }
        return Integer.valueOf(0);
    }
}
