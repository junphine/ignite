package ${packageName}.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
#if($compositeKey)
import org.springframework.web.bind.annotation.RequestParam;
#else
import org.springframework.web.bind.annotation.PathVariable;
#end
#if($hasValuePackage)
import ${valueType};
#end
import ${packageName}.service.${ClassName}Service;

/**
 * ${functionName} CRUD REST API（${tableName}，Key/Value 分离：key 独立传参）
 * <p>
 * POST = 新增（key 未分配时服务端生成，返回带 key 的对象），
 * PUT = 按 key 修改（返回是否成功），GET/DELETE = 查询/删除。
 *
 * @author ${author}
 * @date ${datetime}
 */
@RestController
@RequestMapping("/${urlPrefix}/${tableName}")
public class ${ClassName}Controller {

    private final ${ClassName}Service ${className}Service;

    public ${ClassName}Controller(${ClassName}Service ${className}Service) {
        this.${className}Service = ${className}Service;
    }

    /**
     * 列表：带 key 的行（key 与 value 分离）
     */
    @GetMapping
    public List<${modelType}> findAll() {
        return ${className}Service.findAll();
    }

    @GetMapping("/count")
    public long count() {
        return ${className}Service.countAll();
    }

#if($compositeKey)
    @GetMapping("/findById")
    public ${modelType} findById(${pkRequestParamDecl}) {
        return ${className}Service.findById(new ${table.keyType}(${pkConstructorArgs}));
    }
#else
    @GetMapping("/{id}")
    public ${modelType} findById(@PathVariable ${pkJavaType} id) {
        return ${className}Service.findById(id);
    }
#end

#if($compositeKey)
    /**
     * 修改（PUT）：key 由查询参数组装成 Key 对象，显式落库。
     *
     * @return 是否保存成功
     */
    @PutMapping
    public boolean update(${pkRequestParamDecl}, @RequestBody ${modelType} model) {
        return ${className}Service.saveById(new ${table.keyType}(${pkConstructorArgs}), model) != null;
    }
#else
    /**
     * 修改（PUT）：按路径上的 key 显式落库。
     *
     * @return 是否保存成功
     */
    @PutMapping("/{id}")
    public boolean update(@PathVariable ${pkJavaType} id, @RequestBody ${modelType} model) {
        return ${className}Service.saveById(id, model) != null;
    }
#end

    /**
     * 新增（POST）：走 service 的 Hilla 保存端点 —— key 从 model 自身取，#if($compositeKey)复合键必须在 body 里带上 ${keyProperty}，否则抛 CrudOperationException#else未分配时服务端生成，返回值里带上新 key#end。
     *
     * @param model 行对象
     * @return 保存后的对象（含 key）
     */
    @PostMapping
    public ${modelType} save(@RequestBody ${modelType} model) {
        return ${className}Service.save(model);
    }

#if($compositeKey)
    @DeleteMapping("/deleteById")
    public void deleteById(${pkRequestParamDecl}) {
        ${className}Service.deleteById(new ${table.keyType}(${pkConstructorArgs}));
    }
#else
    @DeleteMapping("/{id}")
    public void deleteById(@PathVariable ${pkJavaType} id) {
        ${className}Service.deleteById(id);
    }
#end

    @DeleteMapping("/all")
    public void deleteAll() {
        ${className}Service.deleteAll();
    }
}
