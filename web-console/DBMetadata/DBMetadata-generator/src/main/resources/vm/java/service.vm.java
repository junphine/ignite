package ${packageName}.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.vaadin.hilla.Nonnull;
import com.vaadin.hilla.Nullable;
import com.vaadin.hilla.crud.CrudService;
import com.vaadin.hilla.crud.filter.Filter;
#if($hasValuePackage)

import ${valueType};
#end

/**
 * ${functionName} Service 接口
 * <p>
 * 继承 Hilla {@code CrudService<T, ID>} = {@code ListService<T>} + {@code FormService<T, ID>}，
 * 行对象为已有 model 类 ${modelType}（不生成 entity），键类型为 ${pkJavaType}（= 缓存 keyType）。
 * <p>
 * Hilla 契约的三个方法在这里显式声明（而不是只靠继承隐式存在）：
 * <ul>
 *   <li>{@code list(pageable, filter)} —— ListService 列表端点</li>
 *   <li>{@code save(model)} —— FormService 保存端点，key 取自 model 的 ${keyProperty}#if($compositeKey)（缺 key 抛 CrudOperationException）#else（未分配时服务端生成）#end</li>
 *   <li>{@code delete(id)} —— FormService 删除端点；{@link #deleteById} 是它的别名</li>
 * </ul>
 * 另提供 crudui 视图与 REST Controller 用的 findAll / countAll / findById / saveById(id, model) 等方法。
 *
 * @author ${author}
 * @date ${datetime}
 */
public interface ${ClassName}Service extends CrudService<${modelType}, ${pkJavaType}> {

    /* ------------------------- Hilla 契约：ListService / FormService ------------------------- */

    /**
     * ListService 列表端点。
     *
     * @param pageable 分页/排序，null 或 unpaged 时返回全部
     * @param filter 过滤条件（Hilla 契约允许为 null），暂未下推到 Ignite 查询
     * @return 本页数据
     */
    @Override
    @Nonnull
    List<${modelType}> list(Pageable pageable, @Nullable Filter filter);

    /**
     * FormService 保存端点（新增或更新，key 由 model 自身提供）。
     *
     * @param model 表单/Hilla 传入的行对象
     * @return 保存后的对象
     */
    @Override
    @Nullable
    ${modelType} save(${modelType} model);

    /**
     * FormService 删除端点。
     *
     * @param id 缓存 key
     */
    @Override
    void delete(${pkJavaType} id);

    /* ------------------------- 应用侧：crudui 视图与 REST Controller ------------------------- */

    /**
     * 查询全部${functionName}
     *
     * @return 全部行
     */
    List<${modelType}> findAll();

    /**
     * 统计${functionName}数量
     *
     * @return 行数
     */
    long countAll();

    /**
     * 按主键查询${functionName}
     *
     * @param id 主键
     * @return 对象，不存在时为 null
     */
    ${modelType} findById(${pkJavaType} id);

    /**
     * 新增/修改${functionName}（显式指定 key，REST 与内部复用）。
     * <p>
     * 名字必须与 {@link #save(Object)} 不同：本实现类带 {@code @BrowserCallable}（Hilla 端点），
     * 而 Hilla 是按「方法名小写」存进 {@code Map<String, Method>} 并以 {@code /端点/方法名} 暴露的，
     * 一旦重载就会互相覆盖，前端调用不确定打到哪个重载上。
     *
     * @param id 主键：单键为 null/0 时由服务端生成，复合键必填
     * @param model 值对象
     * @return 保存后的对象
     */
    ${modelType} saveById(${pkJavaType} id, ${modelType} model);

    /**
     * 按主键删除${functionName}：{@link #delete(Object)} 的别名，供 crudui 视图与 REST 使用。
     *
     * @param id 主键
     */
    default void deleteById(${pkJavaType} id) {
        delete(id);
    }

    /**
     * 删除全部${functionName}
     */
    void deleteAll();

    /**
     * 取出 model 的缓存 key：model 的 @Id 字段 ${keyProperty}（${keyFieldType}）#if(!$compositeKey)转换为 ${pkJavaType}#end。
     * 取 key 的单一事实来源 —— 视图删除、Hilla 保存、自增 key 推导都走这里。
     *
     * @param model 行对象
     * @return 缓存 key
     */
    default ${pkJavaType} getKeyOf(${modelType} model) {
        return ${keyOfExpr};
    }
}
