package org.apache.ignite.console.generator.code;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ignite.console.generator.common.config.GenConfig;
import org.apache.ignite.console.generator.domain.GenTable;
import org.apache.ignite.console.generator.domain.GenTableData;
import org.apache.ignite.console.generator.mapper.impl.GenMapperImpl;
import org.apache.ignite.console.generator.mapper.impl.GenTableColumnMapperImpl;
import org.apache.ignite.console.generator.service.GenService;
import org.apache.ignite.console.generator.service.impl.GenServiceImpl;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * 根据 Ignite Web Console 的数据模型配置生成 Vaadin Flow + crudui 的 CRUD 代码。
 * <p>
 * 元数据来源：优先 Web Console 服务端（validTokens），失败或未提供 token 时
 * 回退到本地 mock 配置目录（context.mockDir，默认 classpath /mock，
 * 参考 src/test/resources/mock/：cluster.json + models/*.json + caches/*.json）。
 * <p>
 * 输出：按目录结构展开写入 destPath（如 target/generated-crud/src/main/java/...），
 * 每个模型生成 IgniteRepository + Service + GridCrud 视图 + REST Controller（不生成 entity，
 * 直接引用模型 valueType 指向的已有 model 类），并额外生成 Hilla React 前端视图
 * {@code frontend/views/{集群名}/{tableName}.tsx}（context.generateFrontend=false 可关）。
 * Service 实现类带 {@code @BrowserCallable}，生成的 tsx 里的 AutoCrud 直接连它。
 */
public class CrudUICodeGenerator {

	private final GenService genService;

	/** */
	private final HttpClient httpClient;

	private String serverUri = "http://127.0.0.1:3000";

	public CrudUICodeGenerator(){

		genService = new GenServiceImpl(new GenMapperImpl(),new GenTableColumnMapperImpl());

		httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(60))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
	}

	private void generatorTable(GenConfig config,Map<String,Object> context) throws IOException {
		List<GenTable> genTables = genService.selectDbTableList(config,context);
		if (genTables == null || genTables.isEmpty()) {
			throw new IOException("未解析到任何模型配置（modelList 为空），请检查 Web Console 服务端或本地 mock 目录");
		}
		List<GenTableData> genTableData = genService.buildTableInfo(genTables,config,context);
		int generated = genService.generatorCode(genTableData, config.getFileDownLoadPath());
		if (generated == 0) {
			// 模型被跳过（没有可用字段 / 找不到列）时不能静默返回“成功”
			throw new IOException("没有任何模型成功生成代码：请检查模型的 valueType（必须是含包名的已有 model 类）与 keyFields/valueFields 配置");
		}
		System.err.println("<=================代码已经生成： " + config.getFileDownLoadPath() + " =================>");
	}

	/**
	 *
	 * @param destPath 代码输出根目录（目录树结构）
	 * @param context 服务端配置文件，参考 src/test/resources/mock/cluster.json；
	 *                可选 context.mockDir 指定本地 mock 目录；
	 *                可选 context.modelDependency = groupId:artifactId:version（模型类所在 Maven 工程，
	 *                不生成 entity 后写进生成的 pom，否则需自行把模型工程加入生成工程 classpath）；
	 *                context.generateFrontend=false 时只生成 Java（不写 frontend/views/{集群名}/ 的 tsx）；
	 *                另有 context.autoCreateCache / context.isLastNode / context.demo
	 * @param validTokens 57bb7348-630a-42bf-bb88-2e9c0fadd768 for test，null 时直接读本地
	 * @return 错误信息，空列表表示成功
	 */
	public List<String> generator(String destPath, Map<String,Object> context, Collection<String> validTokens) {
		List<String> message = new ArrayList<>();
		GenConfig config = new GenConfig();
		if (!asBoolean(context.get("isLastNode"), true)) {
			return message;
		}

		try {
			config.setFileDownLoadPath(destPath);

			// @RepositoryConfig.autoCreateCache：context 可覆盖，默认 true
			Object autoCreateCache = context.get("autoCreateCache");
			if (autoCreateCache != null) {
				config.setAutoCreateCache(Boolean.parseBoolean(autoCreateCache.toString()));
			}

			// 是否生成前端 Hilla 视图（frontend/views/{集群名}/*.tsx），默认生成
			Object generateFrontend = context.get("generateFrontend");
			if (generateFrontend != null) {
				config.setGenerateFrontend(asBoolean(generateFrontend, true));
			}

			// 模型工程坐标（可选）：groupId:artifactId:version。不生成 entity 后生成的代码直接引用
			// 已有 model 类，写进 pom 才能让生成工程独立编译；格式不对只告警，不中断生成。
			Object modelDependency = context.get("modelDependency");
			if (modelDependency != null && !modelDependency.toString().trim().isEmpty()) {
				String gav = modelDependency.toString().trim();
				if (gav.split(":").length == 3) {
					config.setModelDependency(gav);
				} else {
					System.err.println("[WARN] modelDependency 应为 groupId:artifactId:version，已忽略：" + gav);
				}
			}

			// 先解析元数据：context.name 在本地 mock 回退时可能由 cluster.json 补齐
			resolveMetadata(context, validTokens, message);

			// 包名与 author 都依赖 context.name，必须放在元数据解析之后
			applyNaming(config, context, validTokens);

			generatorTable(config, context);

		} catch (IOException e) {
			e.printStackTrace();
			message.add(e.getMessage());
		}

		return message;
	}

	/**
	 * 包名 = {@code org.{demo|platform}.{集群名}}（集群名转成合法包名段）；
	 * author 优先取 Web Console 账号 firstName，服务端不可用时沿用默认值，不中断生成。
	 */
	private void applyNaming(GenConfig config, Map<String, Object> context, Collection<String> validTokens) {
		String name = context.get("name") == null ? null : context.get("name").toString();
		String packageSegment = sanitizePackageSegment(name);
		if (asBoolean(context.get("demo"), false)) {
			// default demo mode package "org.demo";
			config.setPackageName("org.demo." + packageSegment);
			config.setAuthor("demo");
		} else {
			// default none demo mode package "org.platform";
			config.setPackageName("org.platform." + packageSegment);
			config.setAuthor("system");
		}

		if (validTokens == null || name == null) {
			return;
		}
		for (String token : validTokens) {
			try {
				JsonObject account = getAccount(this.serverUri, token);
				// 账号接口异常（如返回错误体）时不覆盖已有 author，避免生成 ${author} 字面量
				String firstName = account.getString("firstName");
				if (firstName != null && !firstName.isEmpty()) {
					config.setAuthor(firstName);
				}
				break;
			} catch (Throwable e) {
				System.err.println("[WARN] 获取作者信息失败，沿用默认 author：" + e);
				break;
			}
		}
	}

	/**
	 * 布尔解析：兼容 Boolean 与 "true"/"false" 字符串（配置可能来自 JSON/YAML），无法识别时取默认值。
	 */
	private static boolean asBoolean(Object value, boolean defaultValue) {
		if (value instanceof Boolean flag) {
			return flag;
		}
		if (value == null) {
			return defaultValue;
		}
		String text = value.toString().trim();
		if ("true".equalsIgnoreCase(text)) {
			return true;
		}
		if ("false".equalsIgnoreCase(text)) {
			return false;
		}
		return defaultValue;
	}

	/**
	 * 集群名转成合法包名段：非 {@code [A-Za-z0-9_]} 替换为 '_'，首字符不能作为标识符起始时前置 '_'。
	 * 否则 {@code my-cluster} 之类的名字会生成编译不过的包。
	 */
	private static String sanitizePackageSegment(String value) {
		if (value == null || value.isEmpty()) {
			return "cluster";
		}
		StringBuilder sb = new StringBuilder(value.length() + 1);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			sb.append(Character.isJavaIdentifierPart(c) ? c : '_');
		}
		if (!Character.isJavaIdentifierStart(sb.charAt(0))) {
			sb.insert(0, '_');
		}
		return sb.toString();
	}

	/**
	 * 解析模型/缓存元数据：
	 * context 已带非空 modelList → 直接使用；
	 * 否则（validTokens 非空且有集群名时）先请求 Web Console 服务端；
	 * 服务端不可用或结果为空 → 回退本地 mock 目录。
	 */
	private void resolveMetadata(Map<String,Object> context, Collection<String> validTokens, List<String> message) {
		if (isNotEmpty(context.get("modelList"))) {
			return;
		}
		String name = context.get("name") == null ? null : context.get("name").toString();
		if (validTokens != null && name != null) {
			for (String token : validTokens) {
				try {
					JsonObject modelList = getMetadata(this.serverUri, name, token);
					JsonObject cacheList = getCacheMetadata(this.serverUri, name, token);
					if (isNotEmpty(modelList)) {
						context.put("modelList", modelList);
						context.put("cacheList", cacheList);
						return;
					}
				} catch (Throwable e) {
					System.err.println("[WARN] 拉取 Web Console 元数据失败，换下一个 token：" + e);
					continue;
				}
			}
		}
		try {
			loadLocalMetadata(context, message);
		} catch (IOException e) {
			message.add("读取本地模型配置失败：" + e.getMessage());
		}
	}

	/**
	 * 从本地 mock 目录加载模型/cache 配置，产出与服务端接口同构的
	 * modelList（valueType → model）与 cacheList（cache name → cache）。
	 * cluster.json 用于补齐 name/models/caches；目录中缺失的 id 跳过并告警。
	 */
	private void loadLocalMetadata(Map<String,Object> context, List<String> message) throws IOException {
		Path mockRoot = resolveMockRoot(context);
		if (mockRoot == null) {
			message.add("未找到本地 mock 配置目录（可用 context.mockDir 指定，如 src/test/resources/mock）");
			return;
		}

		JsonObject cluster = null;
		Path clusterFile = mockRoot.resolve("cluster.json");
		if (Files.isRegularFile(clusterFile)) {
			cluster = readJson(clusterFile);
		}
		if (context.get("name") == null && cluster != null) {
			context.put("name", cluster.getString("name"));
		}

		JsonObject modelList = context.get("modelList") instanceof JsonObject m ? m : new JsonObject();
		for (String id : idList(context.get("models"), cluster, "models")) {
			Path file = mockRoot.resolve("models").resolve(id + ".json");
			if (!Files.isRegularFile(file)) {
				System.err.println("[WARN] 缺少模型配置文件，跳过：" + file);
				continue;
			}
			JsonObject model = readJson(file);
			String valueType = model.getString("valueType");
			if (valueType != null) {
				modelList.put(valueType, model);
			}
		}

		JsonObject cacheList = context.get("cacheList") instanceof JsonObject c ? c : new JsonObject();
		for (String id : idList(context.get("caches"), cluster, "caches")) {
			Path file = mockRoot.resolve("caches").resolve(id + ".json");
			if (!Files.isRegularFile(file)) {
				System.err.println("[WARN] 缺少缓存配置文件，跳过：" + file);
				continue;
			}
			JsonObject cache = readJson(file);
			String cacheName = cache.getString("name");
			if (cacheName != null) {
				cacheList.put(cacheName, cache);
			}
		}

		context.put("modelList", modelList);
		context.put("cacheList", cacheList);
		System.err.println("[INFO] 使用本地模型配置：" + mockRoot
				+ "（模型 " + modelList.fieldNames().size() + " 个，缓存 " + cacheList.fieldNames().size() + " 个）");
	}

	/**
	 * 定位 mock 根目录：context.mockDir（文件或 classpath）→ classpath /mock → 常见相对路径。
	 */
	private Path resolveMockRoot(Map<String,Object> context) {
		Object dir = context.get("mockDir");
		if (dir != null) {
			String value = dir.toString();
			Path path = Paths.get(value);
			if (Files.isDirectory(path)) {
				return path;
			}
			URL url = getClass().getResource(value.startsWith("/") ? value : "/" + value);
			if (url != null && "file".equals(url.getProtocol())) {
				return toPath(url);
			}
		}
		URL url = getClass().getResource("/mock");
		if (url != null && "file".equals(url.getProtocol())) {
			Path path = toPath(url);
			if (path != null) {
				return path;
			}
		}
		for (String candidate : new String[] { "src/test/resources/mock",
				"DBMetadata-generator/src/test/resources/mock" }) {
			Path path = Paths.get(candidate);
			if (Files.isDirectory(path)) {
				return path;
			}
		}
		return null;
	}

	private static Path toPath(URL url) {
		try {
			return Paths.get(url.toURI());
		} catch (URISyntaxException | IllegalArgumentException e) {
			return null;
		}
	}

	private static JsonObject readJson(Path file) throws IOException {
		return new JsonObject(Files.readString(file, StandardCharsets.UTF_8));
	}

	/** id 列表：优先 context 中的 List，缺省回退 cluster.json 的对应数组。 */
	private static List<String> idList(Object fromContext, JsonObject cluster, String key) {
		List<String> ids = new ArrayList<>();
		if (fromContext instanceof List<?> list) {
			for (Object item : list) {
				if (item != null) {
					ids.add(item.toString());
				}
			}
		} else if (cluster != null) {
			JsonArray array = cluster.getJsonArray(key);
			if (array != null) {
				for (int i = 0; i < array.size(); i++) {
					String id = array.getString(i);
					if (id != null) {
						ids.add(id);
					}
				}
			}
		}
		return ids;
	}

	private static boolean isNotEmpty(Object value) {
		return value instanceof JsonObject obj && !obj.fieldNames().isEmpty();
	}

	/**
	 * @param url Request URL.
	 * @param params Request parameters.
	 * @return Request result.
	 * @throws IOException If failed to parse REST result.
	 * @throws Throwable If failed to send request.
	 */
	public HttpResponse<String> sendMetaServerRequest(String url, JsonObject params,String token) throws Throwable {

		final StringBuilder fields = new StringBuilder();
		if(params!=null) {
			params.getMap().forEach((k, v) ->{
				if(v!=null) {
					if(fields.length()>0) {
						fields.append("&");
					}
					fields.append(k);
					fields.append("=");
					fields.append(URLEncoder.encode(String.valueOf(v), StandardCharsets.UTF_8));
				}
			});
		}
		if(fields.length()>0) {
			url = url+"?"+fields.toString();
		}
		URL urlO = new URL(url);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(urlO.toURI())
				.timeout(Duration.ofMinutes(1))
				.header("Content-Type", "application/json")
				.header("Authorization", "Token "+token)
				.GET()
				.build();

		// 不要 catch 后再 throw e.getCause()：cause 为 null（如 InterruptedException）时会变成 NPE，原始异常丢失
		try {
			return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		}

	}

	/**
	 *
	 * @param serverUri
	 * @param clusterId
	 * @param token
	 * @return json: id,valueType,tableComment,fields
	 * @throws Throwable
	 */
	public JsonObject getMetadata(String serverUri, String clusterId, String token) throws Throwable {

		String clusterConfigGetUrl = serverUri+"/api/v1/configuration/clusters/";
		HttpResponse<String> modelsResp = sendMetaServerRequest(clusterConfigGetUrl+clusterId+"/models", null,token);
		String body = ensureOk(modelsResp, "模型列表");
		if(!body.startsWith("[")) {
			return new JsonObject(body);
		}
		JsonArray models = new JsonArray(body);
		JsonObject result = new JsonObject();
		for(int i=0;i<models.size();i++) {
			String modelConfigGetUrl = serverUri+"/api/v1/configuration/domains/";
			JsonObject model = models.getJsonObject(i);

			HttpResponse<String> modelResp = sendMetaServerRequest(modelConfigGetUrl+model.getString("id"), null,token);
			JsonObject modelJson = new JsonObject(ensureOk(modelResp, "模型 " + model.getString("id")));

			String valueType = modelJson.getString("valueType");
			if (valueType == null || valueType.isEmpty()) {
				// valueType 是 modelList 的 key，缺失时 put(null) 会直接 NPE
				System.err.println("[WARN] 模型缺少 valueType，跳过：" + model.getString("id"));
				continue;
			}
			result.put(valueType, modelJson);
		}

		return result;
	}

	/**
	 *
	 * @param serverUri
	 * @param clusterId
	 * @param token
	 * @return json: id,valueType,tableComment,fields
	 * @throws Throwable
	 */
	public JsonObject getCacheMetadata(String serverUri, String clusterId, String token) throws Throwable {

		String clusterConfigGetUrl = serverUri+"/api/v1/configuration/clusters/";
		HttpResponse<String> modelsResp = sendMetaServerRequest(clusterConfigGetUrl+clusterId+"/caches", null,token);
		String body = ensureOk(modelsResp, "缓存列表");
		if(!body.startsWith("[")) {
			return new JsonObject(body);
		}
		JsonArray models = new JsonArray(body);
		JsonObject result = new JsonObject();
		for(int i=0;i<models.size();i++) {
			String modelConfigGetUrl = serverUri+"/api/v1/configuration/caches/";
			JsonObject model = models.getJsonObject(i);

			HttpResponse<String> modelResp = sendMetaServerRequest(modelConfigGetUrl+model.getString("id"), null,token);
			JsonObject modelJson = new JsonObject(ensureOk(modelResp, "缓存 " + model.getString("id")));

			String name = modelJson.getString("name");
			if (name == null || name.isEmpty()) {
				// name 是 cacheList 的 key，缺失时 put(null) 会直接 NPE
				System.err.println("[WARN] 缓存缺少 name，跳过：" + model.getString("id"));
				continue;
			}
			result.put(name, modelJson);
		}

		return result;
	}

	/**
	 *
	 * @param serverUri
	 * @param token
	 * @return json: id,valueType,tableComment,fields
	 * @throws Throwable
	 */
	public JsonObject getAccount(String serverUri, String token) throws Throwable {
		String userGetUrl = serverUri+"/api/v1/user";
		HttpResponse<String> modelsResp = sendMetaServerRequest(userGetUrl, null,token);
		return new JsonObject(ensureOk(modelsResp, "账号信息"));
	}

	/**
	 * 校验 HTTP 状态与响应体：非 2xx 或空响应体时抛 {@link IOException}，
	 * 避免把登录页/错误体当成模型元数据继续解析（进而当成 modelList 用）。
	 *
	 * @return 响应体原文
	 */
	private static String ensureOk(HttpResponse<String> response, String what) throws IOException {
		int status = response.statusCode();
		String body = response.body();
		if (status < 200 || status >= 300) {
			String snippet = body == null ? "" : body.replaceAll("\\s+", " ").trim();
			if (snippet.length() > 200) {
				snippet = snippet.substring(0, 200) + "...";
			}
			throw new IOException("获取" + what + "失败：HTTP " + status + (snippet.isEmpty() ? "" : " " + snippet));
		}
		if (body == null || body.isEmpty()) {
			throw new IOException("获取" + what + "失败：响应体为空");
		}
		return body;
	}

}
