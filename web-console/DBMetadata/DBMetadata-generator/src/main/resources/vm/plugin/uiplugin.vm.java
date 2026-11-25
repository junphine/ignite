package ${packageName};

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.springdata.repository.config.EnableIgniteRepositories;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vaadin.crudui.plugin.UiPlugin;
import ${clusterName}.config.ClientConfigurationFactory;

/**
 * crudui UI 插件入口：宿主应用通过
 * META-INF/services/org.vaadin.crudui.plugin.UiPlugin 发现该类，
 * 包扫描基准即本类所在包（${packageName}）。
 */
@Configuration
@EnableIgniteRepositories(basePackages = "${packageName}.repository")
@EnableVaadin("${packageName}.ui")
public class CrudUiPlugin implements UiPlugin {

    @Override
    public String getId() {
        return "${moduleName}";
    }

    @Override
    public String getName() {
        return "${moduleName} ui";
    }

    @Bean
    @ConditionalOnMissingBean
    public Ignite igniteInstance() throws Exception {
        // return Ignition.start("${clusterName}-client.xml");
        return Ignition.start(ClientConfigurationFactory.createConfiguration());
    }
}
