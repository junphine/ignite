package org.apache.ignite.console.generator.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

public class GenConfig {

    public  String author;

    public  String packageName;

    public  boolean autoRemovePre;

    /**
     * @RepositoryConfig 的 autoCreateCache 属性值：生成的 repository 是否自动建缓存。
     */
    public boolean autoCreateCache = true;

    /**
     * 模型类所在 Maven 工程坐标 {@code groupId:artifactId:version}（可选，context.modelDependency）。
     * 不再生成 entity 后，生成的代码直接引用已有 model 类，构建生成工程时 classpath 上必须有模型工程；
     * 配置后会写进生成的 pom.xml，未配置则只在 pom 里留说明（需手动引入模型工程）。
     */
    public String modelDependency;

    /**
     * 是否生成前端 Hilla React 视图（context.generateFrontend，默认 true）。
     * 关掉后只产出 Java/ pom，不写 frontend/views/{集群名}/ 下的 tsx。
     */
    public boolean generateFrontend = true;

    public  String tablePrefix;

    public  String fileDownLoadPath;

    public  String urlPrefix = "api";

    @Value("${gen.url-prefix:api}")
    public void setUrlPrefix(String urlPrefix) {
        this.urlPrefix = urlPrefix;
    }

    public String getUrlPrefix() {
        return urlPrefix;
    }


    @Value("${gen.fileDownLoadPath}")
    public void setFileDownLoadPath(String fileDownLoadPath) {
        this.fileDownLoadPath = fileDownLoadPath;
    }

    public boolean isAutoRemovePre() {
        return autoRemovePre;
    }

    public String getFileDownLoadPath() {
        return fileDownLoadPath;
    }

    public  String getAuthor() {
        return author;
    }

    @Value("${gen.author}")
    public void setAuthor(String author) {
        this.author = author;
    }

    public  String getPackageName() {
        return packageName;
    }

    @Value("${gen.packageName}")
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public  boolean getAutoRemovePre() {
        return autoRemovePre;
    }

    @Value("${gen.autoRemovePre}")
    public void setAutoRemovePre(boolean autoRemovePre) {
        this.autoRemovePre = autoRemovePre;
    }

    public boolean isAutoCreateCache() {
        return autoCreateCache;
    }

    public void setAutoCreateCache(boolean autoCreateCache) {
        this.autoCreateCache = autoCreateCache;
    }

    public  String getTablePrefix() {
        return tablePrefix;
    }

    @Value("${gen.tablePrefix}")
    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public String getModelDependency() {
        return modelDependency;
    }

    public void setModelDependency(String modelDependency) {
        this.modelDependency = modelDependency;
    }

    public boolean isGenerateFrontend() {
        return generateFrontend;
    }

    public void setGenerateFrontend(boolean generateFrontend) {
        this.generateFrontend = generateFrontend;
    }
}
