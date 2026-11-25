/**
 * ${functionName} Hilla 前端视图（生成文件，可按需修改）
 * <p>
 * {@code AutoCrud} 直连生成的 Hilla 端点：${endpointName} 上的 {@code @BrowserCallable}，
 * 行对象是已有 model 类 ${valueType}，其 TS Model 由 Hilla 生成器产出为
 * {@code Frontend/generated/.../${modelType}Model}。
 * <p>
 * itemIdProperty 必须显式给出：model 的主键字段是 ${keyProperty}，而
 * {@code ModelInfo} 只认 {@code jakarta.persistence.Id} 注解或名为 id 的属性。
 *
 * @author ${author}
 * @date ${datetime}
 */
import { AutoCrud } from '@vaadin/hilla-react-crud';
import ${modelType}Model from '${tsModelPath}';
import { ${endpointName} } from 'Frontend/generated/endpoints';
import type { ViewConfig } from "@vaadin/hilla-file-router/types.js";

export const config: ViewConfig = {
    loginRequired: true,
    rolesAllowed: ['ADMIN'],
    menu: {
        title: "${BusinessName}",
        icon: 'vaadin:table'
    }
};

export default function ${modelType}Crud() {
  return <AutoCrud service={${endpointName}} model={${modelType}Model} itemIdProperty="${keyProperty}" style={{ height: '99.8%' }} />;
}
