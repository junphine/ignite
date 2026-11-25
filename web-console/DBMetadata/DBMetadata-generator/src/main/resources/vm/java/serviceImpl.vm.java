package ${packageName}.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.hilla.BrowserCallable;
import com.vaadin.hilla.crud.filter.Filter;
import org.vaadin.data.utils.FilterUtil;
#if($compositeKey)
import org.vaadin.crudui.crud.CrudOperationException;
#end
#if($hasValuePackage)

import ${valueType};
#end

import ${packageName}.repository.${ClassName}Repository;
import ${packageName}.service.${ClassName}Service;

/**
 * ${functionName} Service 实现（基于 Ignite cache：${table.cacheName}）
 * <p>
 * 实现 Hilla {@code CrudService} 契约的三个方法：{@code list(pageable, filter)}、
 * {@code save(model)}、{@code delete(id)}；其余为 crudui 视图与 REST 用的应用侧方法。
 * <p>
 * {@code @BrowserCallable} 把它暴露成 Hilla 端点（端点名 = 类名 {@code ${ClassName}ServiceImpl}，
 * 前端 {@code Frontend/generated/endpoints} 里可 import），生成的
 * {@code frontend/views/${clusterName}/${tableName}.tsx} 里的 {@code AutoCrud} 就连这个端点；
 * {@code @AnonymousAllowed} 与 REST 一致（REST 未加鉴权），端点至少在未配置登录时也能调通。
 * <p>
 * 注意：这里不能出现同名重载 —— Hilla 端点方法按方法名小写存进 {@code Map<String, Method>}，
 * 所以两参保存方法叫 {@code saveById} 而不是 {@code save}。
 * <p>
 * Key/Value 分离：缓存 key 类型 ${pkJavaType}，value 为已有 model 类 ${modelType}。
 * model 的 @Id 字段 ${keyProperty} 没有 setter，保存时用 Key Field 构造器重建实例
 * 并复制 value 字段，保证 value 内的 ${keyProperty} 与缓存 key 一致。
 *
 * @author ${author}
 * @date ${datetime}
 */
@Service
@BrowserCallable
@AnonymousAllowed
public class ${ClassName}ServiceImpl implements ${ClassName}Service {

    private final ${ClassName}Repository repository;

    public ${ClassName}ServiceImpl(${ClassName}Repository repository) {
        this.repository = repository;
    }

    @Override
    public List<${modelType}> findAll() {
        List<${modelType}> result = new ArrayList<>(20);
        repository.findAll().forEach(result::add);
        return result;
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    /**
     * Hilla 列表端点：filter 暂未下推到 Ignite 查询，先取全量再在内存中分页。
     */
    @Override
    public List<${modelType}> list(Pageable pageable, Filter filter) {
        String query = FilterUtil.toLuceneQuery(filter);
        Iterable<${modelType}> iterable = repository.findAllByText(query, pageable);
        List<${modelType}> list = new ArrayList<>(10);
        iterable.forEach(list::add);
        return list;
    }

    @Override
    public ${modelType} findById(${pkJavaType} id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * FormService 保存端点：key 由 {@code getKeyOf(model)} 从 model 自身推导，
     * #if($compositeKey)缺 key 时在下面的 saveById(id, model) 里抛 CrudOperationException#else未分配时在下面的 saveById(id, model) 里生成 nextKey()#end，
     * 因此 key 判定只有一份实现。
     *
     * @param model 表单/Hilla 传入的行对象
     * @return 保存后的对象
     */
    @Override
    public ${modelType} save(${modelType} model) {
        return saveById(getKeyOf(model), model);
    }

    /**
     * 带显式 key 的保存（REST / {@link #save(Object)} 复用）。不叫 {@code save(id, model)}：
     * 端点按方法名分发，同名重载会互相覆盖。
     */
    @Override
    public ${modelType} saveById(${pkJavaType} id, ${modelType} model) {
        ${pkJavaType} key = id;
        if (${absentKeyExpr}) {
#if($compositeKey)
            throw new CrudOperationException("复合主键不可为空：请通过 Key 对象或 REST 接口提供 ${keyProperty}");
#else
            key = nextKey();
#end
        }
        // model 的 ${keyProperty} 字段没有 setter：复制出 key 一致的实例再落库
        return repository.save(key, withKey(model, key));
    }

    /**
     * FormService 删除端点：直接删缓存中的 key；接口里的 {@code deleteById(id)} 是它的别名。
     */
    @Override
    public void delete(${pkJavaType} id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

#if(!$compositeKey)
    /**
     * 生成与已有数据不冲突的 key（已有数据最大 key + 1）。
     * 不能用每 JVM 自增序列：那样会与已加载的存量数据撞 key。
     *
     * @return 新 key
     */
    private ${pkJavaType} nextKey() {
#if($stringKey)
        return java.util.UUID.randomUUID().toString();
#elseif($numericKey)
        long max = 0L;
        for (${modelType} item : findAll()) {
            ${pkJavaType} k = getKeyOf(item);
            if (k != null && k.longValue() > max) {
                max = k.longValue();
            }
        }
        return ${newKeyExpr};
#else
        throw new IllegalStateException("暂不支持自动生成主键类型：${pkJavaType}");
#end
    }

#end
    /**
     * 复制 value 字段并把 key 写进 ${keyProperty}（该字段只有 Key Field 构造器，没有 setter）。
     *
     * @param model 表单/接口传入的行对象
     * @param key 要落库的缓存 key
     * @return key 与 value 一致的新实例
     */
    private ${modelType} withKey(${modelType} model, ${pkJavaType} key) {
        ${modelType} value = new ${modelType}(key);
#foreach ($column in $columns)
#if(!$column.isPk())
        value.set${column.capJavaField}(model.get${column.capJavaField}());
#end
#end
        return value;
    }
}
