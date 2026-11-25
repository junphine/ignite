package org.apache.ignite.console.generator.code;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * 用 src/test/resources/mock/（Ignite Web Console 导出的集群/模型/缓存配置）
 * 离线验证 Vaadin Flow CRUD 代码生成。
 * <p>
 * 运行方式（模块根目录下）：
 * <pre>
 *   mvn -q test-compile
 *   mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
 *   java -cp "target/classes:target/test-classes:$(cat target/classpath.txt)" \
 *       org.apache.ignite.console.agent.code.CrudUICodeGeneratorTest
 * </pre>
 */
public class CrudUICodeGeneratorTest {

    private static final String DEST = "target/generated-crud";

    /** mock 模型 valueType 均为 com.lr.drupal.model.*：包名取 valueType 包路径但不含 model 子包 → com.lr.drupal */
    private static final String JAVA_ROOT = DEST + "/src/main/java/com/lr/drupal";

    /** valueType 为简短名时回退全局配置包名 org.demo 的用例输出 */
    private static final String SIMPLE_DEST = "target/generated-crud-simple";

    /** context.generateFrontend=false 的用例输出 */
    private static final String NO_FE_DEST = "target/generated-crud-no-frontend";

    CrudUICodeGenerator gen = new CrudUICodeGenerator();

    public void setUp() throws Exception {
        // 先清空输出目录：上一次运行（或旧版本带 entity）的残留文件会让断言误判
        deleteRecursively(Paths.get(DEST));
        deleteRecursively(Paths.get(SIMPLE_DEST));
        deleteRecursively(Paths.get(NO_FE_DEST));
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("清理输出目录失败：" + p, e);
                }
            });
        }
    }

    public void tearDown() throws Exception {
    }

    public void testGenCode() throws Exception {
        // 1. context 来自 mock/cluster.json（用户基于 Ignite 的数据模型配置）
        Path clusterFile = Path.of(CrudUICodeGeneratorTest.class.getResource("/mock/cluster.json").toURI());
        JsonObject cluster = new JsonObject(Files.readString(clusterFile, StandardCharsets.UTF_8));
        Map<String, Object> context = cluster.getMap();

        // 2. 生成（validTokens=null → 回退本地 mock 目录，断言不依赖本机是否起了 Web Console）
        List<String> errors = gen.generator(DEST, context, null);
        assertTrue(errors.isEmpty(), "生成失败：" + errors);

        // 3. 不生成 entity：行对象直接用已有 model 类 com.lr.drupal.model.Users
        assertNotExists(Paths.get(JAVA_ROOT, "entity/Users.java"));
        assertNotExists(Paths.get(JAVA_ROOT, "entity/UcCity.java"));

        Path usersRepository = file("repository/UsersRepository.java");
        assertContains(usersRepository, "IgniteRepository<Users, Integer>");
        assertContains(usersRepository, "cacheName = \"UsersCache\"");
        assertContains(usersRepository, "autoCreateCache = true");

        // Service 继承 Hilla CrudService；键类型 = 缓存 keyType（Users 为 Integer）
        Path usersService = file("service/UsersService.java");
        assertContains(usersService, "public interface UsersService extends CrudService<Users, Integer>");
        // CrudService = ListService + FormService：三个契约方法都要在接口里显式声明
        assertContains(usersService, "import com.vaadin.hilla.Nullable;");
        assertContains(usersService, "List<Users> list(Pageable pageable, @Nullable Filter filter)");
        assertContains(usersService, "Users save(Users model);");
        assertContains(usersService, "void delete(Integer id);");
        // 应用侧方法（crudui 视图 / REST）
        assertContains(usersService, "List<Users> findAll()");
        assertContains(usersService, "Users findById(Integer id)");
        // 两参保存叫 saveById（Hilla 端点按方法名分发，不能与 FormService 的 save(T) 重载）
        assertContains(usersService, "Users saveById(Integer id, Users model)");
        assertNotContains(usersService, "Users save(Integer id, Users model)");
        assertContains(usersService, "default void deleteById(Integer id)");
        assertContains(usersService, "void deleteAll()");
        assertContains(usersService, "default Integer getKeyOf(Users model)");
        // model 上 uid 是 long、缓存 keyType 是 Integer → 显式收窄（而不是静默截断）
        assertContains(usersService, "return Math.toIntExact(model.getUid());");
        // 不存在的类（旧模板引用）
        assertNotContains(usersService, "KeyedItem");

        Path usersImpl = file("service/impl/UsersServiceImpl.java");
        assertContains(usersImpl, "public List<Users> list(Pageable pageable, Filter filter)");
        assertContains(usersImpl, "repository.findById(id).orElse(null)");
        assertContains(usersImpl, "repository.deleteById(id)");
        assertContains(usersImpl, "repository.deleteAll()");
        // FormService 三个方法的实现：save(model) 复用 saveById(id, model)，delete(id) 直连 repository
        assertContains(usersImpl, "return saveById(getKeyOf(model), model);");
        assertContains(usersImpl, "public void delete(Integer id)");
        assertNotContains(usersImpl, "public void deleteById(Integer id)");
        // @BrowserCallable 暴露成 Hilla 端点（端点名 = 类名 UsersServiceImpl），
        // 且实现类里不能有同名重载（端点按方法名小写存进 Map<String, Method>）
        assertContains(usersImpl, "@BrowserCallable");
        assertContains(usersImpl, "@AnonymousAllowed");
        assertContains(usersImpl, "public Users saveById(Integer id, Users model)");
        assertNotContains(usersImpl, "public Users save(Integer id, Users model)");
        // Key/Value 分离：model 的 uid 没有 setter → Key Field 构造器重建 + 复制 value 字段
        assertContains(usersImpl, "repository.save(key, withKey(model, key))");
        assertContains(usersImpl, "Users value = new Users(key);");
        assertContains(usersImpl, "value.setName(model.getName());");
        // 新 key = 已有数据 max+1；不再用每 JVM 自增序列（会与存量数据撞 key）
        assertContains(usersImpl, "return Math.toIntExact(max + 1);");
        assertNotContains(usersImpl, "sequence.incrementAndGet()");

        // 4. UcZones：Long 主键 + cache 反查 + 路由用 tableName
        assertContains(file("repository/UcZonesRepository.java"), "cacheName = \"UcZonesCache\"");
        assertContains(file("service/UcZonesService.java"), "UcZones findById(Long id)");
        // keyField 与 keyType 同为 Long → getKeyOf 直接取 getter
        assertContains(file("service/UcZonesService.java"), "return model.getZoneId();");
        Path zonesView = file("ui/view/UcZonesView.java");
        assertContains(zonesView, "@Route(\"admin/uc_zones\")");
        assertContains(zonesView, "GridCrud<UcZones>");
        assertContains(zonesView, "setColumns(\"zoneId\", \"zoneCountryId\"");

        // 5. ProfileFields：tableComment → functionName；Integer 主键（keyField 同为 Integer）
        assertContains(file("service/ProfileFieldsService.java"), "用户资料");
        assertContains(file("service/ProfileFieldsService.java"), "return model.getFid();");
        assertContains(file("repository/ProfileFieldsRepository.java"), "cacheName = \"ProfileFieldsCache\"");
        assertContains(file("service/impl/ProfileFieldsServiceImpl.java"), "Math.toIntExact(max + 1)");
        assertContains(file("ui/view/ProfileFieldsView.java"), "@Route(\"admin/profile_fields\")");

        // 6. ProfileValues：复合键（keyType=ProfileValuesKey，keyFields=[fid,uid]）
        Path pvRepository = file("repository/ProfileValuesRepository.java");
        assertContains(pvRepository, "IgniteRepository<ProfileValues, com.lr.drupal.model.ProfileValuesKey>");
        assertContains(pvRepository, "cacheName = \"ProfileValuesCache\"");

        Path pvService = file("service/ProfileValuesService.java");
        assertContains(pvService, "extends CrudService<ProfileValues, com.lr.drupal.model.ProfileValuesKey>");
        // Hilla 契约（复合键版本）：save(T) / delete(ID) 也显式声明，deleteById 只是别名
        assertContains(pvService, "ProfileValues save(ProfileValues model);");
        assertContains(pvService, "void delete(com.lr.drupal.model.ProfileValuesKey id);");
        assertContains(pvService, "default void deleteById(com.lr.drupal.model.ProfileValuesKey id)");
        // 复合键 Service：findById/save/deleteById 都接收外部 Key 类
        assertContains(pvService, "ProfileValues findById(com.lr.drupal.model.ProfileValuesKey id)");
        assertContains(pvService, "void deleteAll()");
        // 复合键在 model 上是单个 id（Key 对象）→ getKeyOf 直接取
        assertContains(pvService, "default com.lr.drupal.model.ProfileValuesKey getKeyOf(ProfileValues model)");
        assertContains(pvService, "return model.getId();");

        Path pvImpl = file("service/impl/ProfileValuesServiceImpl.java");
        assertContains(pvImpl, "ProfileValues value = new ProfileValues(key);");
        assertContains(pvImpl, "value.setValue(model.getValue());");
        assertContains(pvImpl, "repository.save(key, withKey(model, key))");
        assertContains(pvImpl, "repository.findById(id).orElse(null)");
        // 复合键无法自动生成 → CrudOperationException（crudui 显示成通知）
        assertContains(pvImpl, "throw new CrudOperationException(");
        assertNotContains(pvImpl, "nextKey()");

        Path pvView = file("ui/view/ProfileValuesView.java");
        assertContains(pvView, "@Route(\"admin/profile_values\")");
        assertContains(pvView, "GridCrud<ProfileValues>");
        // Grid 列：Key 对象转字符串单独成列，value 字段随后
        assertContains(pvView, "addColumn(item -> String.valueOf(item.getId())).setHeader(\"id\")");
        assertContains(pvView, "addColumns(\"value\")");
        // 表单只暴露 value 字段（fid/uid 属于 id 复合键，model 上没有 setter）
        assertContains(pvView, "setVisibleProperties(\"value\")");
        assertContains(pvView, "setAddOperationVisible(false)");
        assertNotContains(pvView, "MainLayout");
        assertNotContains(pvView, "com.lr.drupal.entity");

        // 7. 工程级文件：pom.xml / UiPlugin 实现 / services 描述符
        Path pom = Paths.get(DEST, "pom.xml");
        assertContains(pom, "<groupId>com.lr.drupal</groupId>");
        assertContains(pom, "<artifactId>drupal</artifactId>");
        assertContains(pom, "<artifactId>crudui</artifactId>");
        assertContains(pom, "<artifactId>ignite-spring-data-ext</artifactId>");
        // 未配置 modelDependency：只留说明（手动把模型工程加进 classpath），不输出依赖
        assertContains(pom, "不再生成 entity");
        assertNotContains(pom, "${modelGroupId}");
        assertNotContains(pom, "#if(");

        Path plugin = Paths.get(DEST, "src/main/java/com/lr/drupal/CrudUiPlugin.java");
        assertContains(plugin, "public class CrudUiPlugin implements UiPlugin");
        assertContains(plugin, "@EnableIgniteRepositories(basePackages = \"com.lr.drupal.repository\")");
        assertContains(plugin, "@EnableVaadin(\"com.lr.drupal.ui\")");

        Path descriptor = Paths.get(DEST, "src/main/resources/META-INF/services/org.vaadin.crudui.plugin.UiPlugin");
        assertContains(descriptor, "com.lr.drupal.CrudUiPlugin");

        // 8. @RestController CRUD API
        Path usersController = file("controller/UsersController.java");
        assertContains(usersController, "@RestController");
        assertContains(usersController, "@RequestMapping(\"/api/users\")");
        assertContains(usersController, "countAll()");
        assertContains(usersController, "@RequestBody Users model");
        // 返回类型是 List，import 也必须是 List（此前 import 的是 Collection，编译不过）
        assertContains(usersController, "import java.util.List;");
        assertContains(usersController, "public List<Users> findAll()");
        // 两参保存方法已改名（Service 上是 saveById，REST 路径不变）
        assertContains(usersController, "usersService.saveById(id, model)");
        assertNotContains(usersController, "import java.util.Collection;");
        assertNotContains(usersController, "MainLayout");
        // findById / deleteById / deleteAll（单键走路径变量）
        assertContains(usersController, "@GetMapping(\"/{id}\")");
        assertContains(usersController, "@PathVariable Integer id");
        assertContains(usersController, "@DeleteMapping(\"/{id}\")");
        assertContains(usersController, "@DeleteMapping(\"/all\")");
        // PUT = 修改（返回 boolean），POST = 新增（key 服务端生成，返回带 key 的对象）
        assertContains(usersController, "import org.springframework.web.bind.annotation.PutMapping;");
        assertContains(usersController, "@PutMapping(\"/{id}\")");
        assertContains(usersController, "public boolean update(@PathVariable Integer id, @RequestBody Users model)");
        assertContains(usersController, "usersService.saveById(id, model) != null");
        assertContains(usersController, "public Users save(@RequestBody Users model)");
        assertContains(usersController, "usersService.save(model)");
        // 旧的 save(@PathVariable, @RequestBody) 已改成 update
        assertNotContains(usersController, "public Users save(@PathVariable");

        // 复合键 Controller：查询参数组装 Key（无路径变量）
        Path pvController = file("controller/ProfileValuesController.java");
        assertContains(pvController, "@GetMapping(\"/findById\")");
        assertContains(pvController, "@RequestParam(\"fid\") Long fid, @RequestParam(\"uid\") Long uid");
        assertContains(pvController, "new com.lr.drupal.model.ProfileValuesKey(fid, uid)");
        assertContains(pvController, "@DeleteMapping(\"/deleteById\")");
        assertContains(pvController, "@DeleteMapping(\"/all\")");
        // 复合键同样：PUT = boolean update（key 组装 Key 对象），POST = 新增
        assertContains(pvController, "@PutMapping");
        assertContains(pvController, "public boolean update(");
        assertContains(pvController, ", model) != null");
        assertContains(pvController, "public ProfileValues save(@RequestBody ProfileValues model)");
        assertContains(pvController, "profileValuesService.save(model)");
        assertNotContains(pvController, "PathVariable");

        // 视图引用的是 model 类，不是被移除的 entity 包
        Path usersView = file("ui/view/UsersView.java");
        assertContains(usersView, "@Route(\"admin/users\")");
        assertContains(usersView, "GridCrud<Users>");
        assertContains(usersView, "setOperations");
        assertContains(usersView, "import com.lr.drupal.model.Users;");
        assertContains(usersView, "item -> usersService.deleteById(usersService.getKeyOf(item))");
        // 表单不含主键（key 字段无 setter）
        assertContains(usersView, "setVisibleProperties(\"name\", \"pass\"");
        assertNotContains(usersView, "com.lr.drupal.entity");

        // 9. 前端 Hilla React 视图：frontend/views/{集群名}/{tableName}.tsx（文件名 = 表名）
        Path usersTsx = Paths.get(DEST, "frontend/views/user_db/users.tsx");
        assertContains(usersTsx, "import { AutoCrud } from '@vaadin/hilla-react-crud';");
        // TS Model 路径 = valueType 的包路径 + Model；端点名 = 实现类类名
        assertContains(usersTsx, "import UsersModel from 'Frontend/generated/com/lr/drupal/model/UsersModel';");
        assertContains(usersTsx, "import { UsersServiceImpl } from 'Frontend/generated/endpoints';");
        assertContains(usersTsx, "export default function UsersCrud()");
        // 主键字段名必须显式给出（ModelInfo 只认 jakarta @Id 或叫 id 的属性，Users 的是 uid）
        assertContains(usersTsx, "itemIdProperty=\"uid\"");
        assertContains(usersTsx, "title: \"Users\"");
        assertContains(usersTsx, "loginRequired: true");
        assertContains(usersTsx, "rolesAllowed: ['ADMIN']");

        // 复合键模型：itemIdProperty = id（Key 对象），端点名同理
        Path pvTsx = Paths.get(DEST, "frontend/views/user_db/profile_values.tsx");
        assertContains(pvTsx, "import ProfileValuesModel from 'Frontend/generated/com/lr/drupal/model/ProfileValuesModel';");
        assertContains(pvTsx, "import { ProfileValuesServiceImpl } from 'Frontend/generated/endpoints';");
        assertContains(pvTsx, "itemIdProperty=\"id\"");
        assertContains(pvTsx, "export default function ProfileValuesCrud()");

        // 10. 打印生成树
        System.out.println("生成目录：" + DEST);
        try (Stream<Path> walk = Files.walk(Paths.get(DEST))) {
            walk.filter(Files::isRegularFile).sorted()
                    .forEach(p -> System.out.println("  " + Paths.get(DEST).relativize(p)));
        }
    }

    /**
     * valueType 的包被 strip 后为空（如 {@code model.SimpleThing} → 去掉 model 段）时，
     * packageName 回退到全局配置的 {@code org.platform.{集群名}}；
     * valueType 完全没有包名的模型无法引用已有 model 类，直接跳过。
     */
    public void testSimpleValueTypeFallback() {
        JsonObject model = new JsonObject()
                .put("id", "simple-0001")
                .put("valueType", "model.SimpleThing")
                .put("databaseTable", "simple_thing")
                .put("keyType", "Integer")
                .put("keyFieldName", "id")
                .put("fields", new JsonArray()
                        .add(new JsonObject().put("name", "id").put("className", "Integer"))
                        .add(new JsonObject().put("name", "title").put("className", "String").put("comment", "标题")))
                .put("keyFields", new JsonArray().add(new JsonObject()
                        .put("databaseFieldName", "id").put("databaseFieldType", "INTEGER")
                        .put("javaType", "Integer").put("javaFieldName", "id").put("javaFieldType", "int")))
                .put("valueFields", new JsonArray().add(new JsonObject()
                        .put("databaseFieldName", "title").put("databaseFieldType", "VARCHAR")
                        .put("javaType", "String").put("javaFieldName", "title").put("javaFieldType", "String")));

        Map<String, Object> context = new HashMap<>();
        context.put("name", "simple_test");
        // valueType 没有包名的模型：不生成 entity 后无法引用已有类 → 必须跳过（不能产出编译不过的代码）
        JsonObject bareModel = new JsonObject()
                .put("id", "simple-0002")
                .put("valueType", "NoPackageThing")
                .put("databaseTable", "no_package_thing");
        context.put("modelList", new JsonObject().put("model.SimpleThing", model).put("NoPackageThing", bareModel));
        context.put("cacheList", new JsonObject());
        // autoCreateCache 可由 context 覆盖
        context.put("autoCreateCache", false);
        // 模型工程坐标：写进生成的 pom，让生成工程能解析到已有 model 类
        context.put("modelDependency", "com.example:thing-model:1.2.3");

        List<String> errors = gen.generator(SIMPLE_DEST, context, null);
        assertTrue(errors.isEmpty(), "简短 valueType 回退生成失败：" + errors);

        // valueType 不含包名的模型被跳过：不产出任何文件
        assertNotExists(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/service/NoPackageThingService.java"));
        assertNotExists(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/repository/NoPackageThingRepository.java"));
        assertNotExists(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/ui/view/NoPackageThingView.java"));

        // 回退到全局配置包名：非 demo 模式 = org.platform.{集群名}（见 generator()）；不生成 entity
        assertNotExists(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/entity/SimpleThing.java"));
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/repository/SimpleThingRepository.java"),
                "cacheName = \"SimpleThingCache\"");
        // context 覆盖后的 autoCreateCache
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/repository/SimpleThingRepository.java"),
                "autoCreateCache = false");
        // 同样继承 CrudService，key 类型 = keyType
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/service/SimpleThingService.java"),
                "public interface SimpleThingService extends CrudService<SimpleThing, Integer>");
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/service/SimpleThingService.java"),
                "import model.SimpleThing;");
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/service/SimpleThingService.java"),
                "return model.getId();");
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/ui/view/SimpleThingView.java"),
                "@Route(\"admin/simple_thing\")");
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/ui/view/SimpleThingView.java"),
                "GridCrud<SimpleThing>");
        // 工程级文件（UiPlugin/services 描述符）同样遵循回退包名
        assertContains(Paths.get(SIMPLE_DEST, "src/main/java/org/platform/simple_test/CrudUiPlugin.java"),
                "public class CrudUiPlugin implements UiPlugin");
        assertContains(Paths.get(SIMPLE_DEST, "src/main/resources/META-INF/services/org.vaadin.crudui.plugin.UiPlugin"),
                "org.platform.simple_test.CrudUiPlugin");
        // 配置了 modelDependency → 生成的 pom 带上模型工程依赖（生成工程可独立编译）
        Path simplePom = Paths.get(SIMPLE_DEST, "pom.xml");
        assertContains(simplePom, "<groupId>com.example</groupId>");
        assertContains(simplePom, "<artifactId>thing-model</artifactId>");
        assertContains(simplePom, "<version>1.2.3</version>");
        assertNotContains(simplePom, "${modelGroupId}");

        // 前端视图同样按集群名落目录（context.name=simple_test），文件名 = 表名，TS Model 用 valueType 原包名
        Path simpleTsx = Paths.get(SIMPLE_DEST, "frontend/views/simple_test/simple_thing.tsx");
        assertContains(simpleTsx, "import SimpleThingModel from 'Frontend/generated/model/SimpleThingModel';");
        assertContains(simpleTsx, "import { SimpleThingServiceImpl } from 'Frontend/generated/endpoints';");
        assertContains(simpleTsx, "itemIdProperty=\"id\"");
        // 没有包名的模型整体被跳过 → 也不能有 tsx
        assertNotExists(Paths.get(SIMPLE_DEST, "frontend/views/simple_test/no_package_thing.tsx"));
    }

    /**
     * context.generateFrontend=false：只生成 Java/pom，不写 frontend/views/ 下的 tsx。
     */
    public void testFrontendDisabled() throws Exception {
        JsonObject model = new JsonObject()
                .put("id", "fe-0001")
                .put("valueType", "com.example.Thing")
                .put("databaseTable", "thing")
                .put("keyType", "Integer")
                .put("keyFieldName", "id")
                .put("keyFields", new JsonArray().add(new JsonObject()
                        .put("databaseFieldName", "id").put("databaseFieldType", "INTEGER")
                        .put("javaType", "Integer").put("javaFieldName", "id").put("javaFieldType", "int")))
                .put("valueFields", new JsonArray().add(new JsonObject()
                        .put("databaseFieldName", "title").put("databaseFieldType", "VARCHAR")
                        .put("javaType", "String").put("javaFieldName", "title").put("javaFieldType", "String")));

        Map<String, Object> context = new HashMap<>();
        context.put("name", "no_fe_cluster");
        context.put("modelList", new JsonObject().put("com.example.Thing", model));
        context.put("cacheList", new JsonObject());
        context.put("generateFrontend", false);

        List<String> errors = gen.generator(NO_FE_DEST, context, null);
        assertTrue(errors.isEmpty(), "关闭前端生成失败：" + errors);

        // Java 侧照常生成
        assertContains(Paths.get(NO_FE_DEST, "src/main/java/com/example/service/ThingService.java"),
                "public interface ThingService extends CrudService<Thing, Integer>");
        // 前端一个 tsx 都不写（目录本身也不创建）
        assertEquals(countFiles(Paths.get(NO_FE_DEST, "frontend"), ".tsx"), 0,
                "generateFrontend=false 不应生成 tsx");
        assertNotExists(Paths.get(NO_FE_DEST, "frontend/views/no_fe_cluster/thing.tsx"));
    }

    private static Path file(String relative) {
        Path path = Paths.get(JAVA_ROOT, relative);
        assertTrue(Files.isRegularFile(path), "缺少生成文件：" + path);
        return path;
    }

    private static void assertContains(Path file, String expected) {
        try {
            // 归一化行尾，断言可用 \n 书写跨行片段
            String content = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertTrue(content.contains(expected),
                    "文件 " + file + " 缺少预期内容：" + expected + "\n--- 实际内容 ---\n" + content);
        } catch (java.io.IOException e) {
            throw new AssertionError("读取文件失败：" + file, e);
        }
    }

    private static void assertNotContains(Path file, String unexpected) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertTrue(!content.contains(unexpected),
                    "文件 " + file + " 不应包含内容：" + unexpected);
        } catch (java.io.IOException e) {
            throw new AssertionError("读取文件失败：" + file, e);
        }
    }

    /** entity 已不再生成：路径必须不存在（目录不存在也成立）。 */
    private static void assertNotExists(Path path) {
        assertTrue(!Files.exists(path), "不应生成（entity 已移除）：" + path);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int actual, int expected, String message) {
        assertTrue(actual == expected, message + "（实际 " + actual + "，期望 " + expected + "）");
    }

    /** 统计目录树下指定后缀的文件个数（目录不存在按 0）。 */
    private static int countFiles(Path dir, String suffix) throws java.io.IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return (int) walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix)).count();
        }
    }

    public static void main(String[] args) throws Exception {
        CrudUICodeGeneratorTest testCase = new CrudUICodeGeneratorTest();
        testCase.setUp();
        try {
            testCase.testGenCode();
            testCase.testSimpleValueTypeFallback();
            testCase.testFrontendDisabled();
        } finally {
            testCase.tearDown();
        }
        System.out.println("BUILD SUCCESS：所有生成结果断言通过 → " + DEST + " / " + SIMPLE_DEST + " / " + NO_FE_DEST);
    }
}
