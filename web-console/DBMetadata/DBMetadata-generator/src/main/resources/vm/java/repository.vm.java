package ${packageName}.repository;

import org.apache.ignite.springdata.repository.IgniteRepository;
import org.apache.ignite.springdata.repository.config.RepositoryConfig;
#if($hasValuePackage)

import ${valueType};
#end

/**
 * ${functionName} Ignite Spring Data repository，绑定 cache：${table.cacheName}
 * 值类型为外部已有 model 类 ${modelType}（不生成 entity）。
 *
 * @author ${author}
 * @date ${datetime}
 */
@RepositoryConfig(cacheName = "${table.cacheName}", autoCreateCache = ${autoCreateCache})
public interface ${ClassName}Repository extends IgniteRepository<${modelType}, ${pkJavaType}> {
}
