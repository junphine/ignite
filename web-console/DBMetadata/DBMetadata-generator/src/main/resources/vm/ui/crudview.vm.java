package ${packageName}.ui.view;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

import org.vaadin.crudui.crud.impl.GridCrud;
import org.vaadin.crudui.layout.impl.WindowBasedCrudLayout;
#if($hasValuePackage)
import ${valueType};
#end
import ${packageName}.service.${ClassName}Service;

/**
 * ${functionName} CRUD 页面（路由：${tableName}）
 * <p>
 * 行对象为已有 model 类 ${modelType}：表单只暴露 value 字段，主键 ${keyProperty} 没有 setter，
 * 由 service 的 getKeyOf / Key Field 构造器负责 key；删除用 getKeyOf 还原缓存 key。
 *
 * @author ${author}
 * @date ${datetime}
 */
@Route("${layout}/${tableName}")
@Menu(title="${BusinessName}",icon="vaadin:table")
public class ${ClassName}View extends VerticalLayout {

    public ${ClassName}View(${ClassName}Service ${className}Service) {
        WindowBasedCrudLayout crudLayout = new WindowBasedCrudLayout();
        crudLayout.setFormWindowWidth("80%");
        GridCrud<${modelType}> crud = new GridCrud<>(${modelType}.class,crudLayout);
        crud.setOperations(
                ${className}Service::findAll,
                ${className}Service::save,
                ${className}Service::save,
                item -> ${className}Service.deleteById(${className}Service.getKeyOf(item)));
#if($compositeKey)
        // 复合主键在 model 上是单个 ${keyProperty}（Key 对象）：单独转字符串成列，其余为 value 字段；
        // key 无法在表单里填写（没有 setter），隐藏“新增”按钮，REST 接口仍可新增
        crud.getGrid().removeAllColumns();
        crud.getGrid().addColumn(item -> String.valueOf(item.get${capKeyProperty}())).setHeader("${keyProperty}");
        crud.getGrid().addColumns(${valueProperties});
        crud.setAddOperationVisible(false);
#else
        // 全部字段建列，首列即主键 ${keyProperty}
        crud.getGrid().setColumns(${gridColumns});
#end
        crud.getCrudFormFactory().setVisibleProperties(${valueProperties});
        add(crud);
        setSizeFull();
    }
}
