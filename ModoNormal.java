package BCraftOSproject1.BCraftOS1;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Modo normal: abre o Minecraft + Forge do jeito que o launcher oficial abre,
 * com os nomes ofuscados de verdade e a pasta mods comum.
 *
 * É o modo que mods de jogo (e clientes como os "ghost clients") esperam. O modo do Gradle
 * (runClient) roda num ambiente de desenvolvimento com nomes diferentes, e esses mods não
 * conseguem se encaixar lá.
 *
 * Vale para o Forge até a 1.12.2, cujo instalador traz o "install_profile.json" com a lista
 * completa de bibliotecas. Os arquivos baixados ficam numa pasta de cache compartilhada
 * (cache-normal/), porque são iguais para todas as contas.
 */
public class ModoNormal {

	private static final String URL_MANIFESTO = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
	private static final String URL_ASSETS = "https://resources.download.minecraft.net/";
	private static final String URL_FORGE = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
	private static final String[] REPOSITORIOS = {
			"https://maven.minecraftforge.net/",
			"https://libraries.minecraft.net/",
			"https://repo1.maven.org/maven2/"
	};
	private static final String ULTIMA_VERSAO_SUPORTADA = "1.12.2";

	/** O modo normal só existe para o Forge de versões antigas. */
	public static boolean disponivelPara(String loader, String versaoMc) {
		return "Forge".equalsIgnoreCase(loader)
				&& versaoMc != null
				&& CatalogoVersoes.comparar(versaoMc, ULTIMA_VERSAO_SUPORTADA) <= 0;
	}

	/**
	 * Prepara tudo (baixa só o que falta) e abre o jogo. Lança IllegalStateException, na hora,
	 * se faltar o Java certo; o resto roda numa thread e o motivo de qualquer falha vai para o console.
	 */
	public static void iniciar(File pastaMDK, String versaoMc, String codigoForge, String nick,
			Runnable aoFinalizar) {
		String javaNecessario = MinecraftLauncher.javaNecessarioPara(versaoMc);
		String javaHome = MinecraftLauncher.procurarJava(javaNecessario);
		if (javaHome == null) {
			throw new IllegalStateException("Não encontrei o Java " + javaNecessario
					+ ", exigido pela versão " + versaoMc + ". Instale o Java " + javaNecessario
					+ " e abra o launcher de novo.");
		}
		File gameDir = new File(pastaMDK, "run");
		gameDir.mkdirs();

		new Thread(() -> {
			try {
				List<String> comando = prepararComando(javaHome, gameDir, versaoMc, codigoForge, nick);
				System.out.println("[BCraftOS] Modo normal: iniciando o jogo...");
				ProcessBuilder pb = new ProcessBuilder(comando);
				pb.directory(gameDir);
				pb.redirectErrorStream(true); // a saída vai para o console e para logs/
				Process processo = pb.start();
				Thread leitor = RegistroLogs.acompanhar(processo, "modo-normal");
				int codigo = processo.waitFor();
				leitor.join(3000);
				System.out.println(codigo == 0
						? "[BCraftOS] Minecraft encerrado normalmente."
						: "[BCraftOS] O jogo terminou com código " + codigo + ". Veja as mensagens acima.");
			} catch (Exception e) {
				System.err.println("[BCraftOS Erro] Modo normal falhou: " + e.getMessage());
				e.printStackTrace();
			} finally {
				if (aoFinalizar != null) {
					aoFinalizar.run();
				}
			}
		}).start();
	}

	// ------------------------------------------------------------------
	// Preparação: baixa o que falta e monta a linha de comando
	// ------------------------------------------------------------------

	private static List<String> prepararComando(String javaHome, File gameDir, String versaoMc,
			String codigoForge, String nick) throws Exception {
		File cache = new File(System.getProperty("user.dir"), "cache-normal");
		File pastaLibs = new File(cache, "libraries");
		File pastaNatives = new File(new File(cache, "natives"), versaoMc);

		// 1. Minecraft original (bibliotecas, jar do cliente, índice de assets)
		Map<String, Object> vanilla = obterJsonDaVersao(versaoMc, cache);

		// 2. Forge: o instalador traz a lista de bibliotecas e o jar do Forge
		Map<String, Object> perfil = obterPerfilForge(codigoForge, cache);
		Map<String, Object> install = MiniJson.objeto(perfil.get("install"));
		Map<String, Object> infoForge = MiniJson.objeto(perfil.get("versionInfo"));
		if (install == null || infoForge == null) {
			throw new IllegalStateException("Esse instalador do Forge tem um formato que o modo normal não "
					+ "suporta (só vai até a 1.12.2).");
		}
		File instalador = new File(new File(new File(cache, "forge"), codigoForge), "installer.jar");

		// Classpath: o Forge vem primeiro, então a versão dele de cada biblioteca vence a do Minecraft.
		Map<String, File> classpath = new LinkedHashMap<>();
		String nomeDoForge = MiniJson.texto(install.get("path"));
		for (Object item : MiniJson.lista(infoForge.get("libraries"))) {
			Map<String, Object> lib = MiniJson.objeto(item);
			if (lib == null || Boolean.FALSE.equals(lib.get("clientreq"))) {
				continue;
			}
			String nome = MiniJson.texto(lib.get("name"));
			File arquivo = new File(pastaLibs, caminhoMaven(nome));
			if (!arquivo.isFile() || arquivo.length() == 0) {
				if (nome.equals(nomeDoForge)) {
					System.out.println("[BCraftOS] Extraindo o Forge do instalador...");
					extrairDoZip(instalador, MiniJson.texto(install.get("filePath")), arquivo);
				} else {
					baixarDeRepositorios(caminhoMaven(nome), MiniJson.texto(lib.get("url")), arquivo);
				}
			}
			classpath.putIfAbsent(chaveDaBiblioteca(nome), arquivo);
		}

		List<File> jarsDeNatives = new ArrayList<>();
		for (Object item : MiniJson.lista(vanilla.get("libraries"))) {
			Map<String, Object> lib = MiniJson.objeto(item);
			if (lib == null || !regrasPermitem(lib.get("rules"))) {
				continue;
			}
			String nome = MiniJson.texto(lib.get("name"));
			Map<String, Object> downloads = MiniJson.objeto(lib.get("downloads"));

			Map<String, Object> artefato = downloads == null ? null : MiniJson.objeto(downloads.get("artifact"));
			String caminho = artefato != null && MiniJson.texto(artefato.get("path")) != null
					? MiniJson.texto(artefato.get("path")) : caminhoMaven(nome);
			boolean temNatives = lib.get("natives") != null;
			if (artefato != null || !temNatives) {
				File arquivo = new File(pastaLibs, caminho);
				garantirArquivo(arquivo, artefato == null ? null : MiniJson.texto(artefato.get("url")),
						artefato == null ? null : MiniJson.texto(artefato.get("sha1")),
						caminho, MiniJson.texto(lib.get("url")));
				classpath.putIfAbsent(chaveDaBiblioteca(nome), arquivo);
			}

			Map<String, Object> natives = MiniJson.objeto(lib.get("natives"));
			String classificador = natives == null ? null : MiniJson.texto(natives.get(nomeDoSistema()));
			if (classificador != null && downloads != null) {
				classificador = classificador.replace("${arch}", "64");
				Map<String, Object> classificadores = MiniJson.objeto(downloads.get("classifiers"));
				Map<String, Object> alvo = classificadores == null ? null
						: MiniJson.objeto(classificadores.get(classificador));
				if (alvo != null) {
					File arquivo = new File(pastaLibs, MiniJson.texto(alvo.get("path")));
					garantirArquivo(arquivo, MiniJson.texto(alvo.get("url")), MiniJson.texto(alvo.get("sha1")),
							MiniJson.texto(alvo.get("path")), null);
					jarsDeNatives.add(arquivo);
				}
			}
		}
		prepararNatives(jarsDeNatives, pastaNatives);

		// 3. Jar do cliente
		Map<String, Object> baixaveis = MiniJson.objeto(vanilla.get("downloads"));
		Map<String, Object> cliente = baixaveis == null ? null : MiniJson.objeto(baixaveis.get("client"));
		if (cliente == null) {
			throw new IllegalStateException("A descrição da versão " + versaoMc + " não traz o jar do cliente.");
		}
		File jarCliente = new File(new File(new File(cache, "versions"), versaoMc), versaoMc + ".jar");
		garantirArquivo(jarCliente, MiniJson.texto(cliente.get("url")), MiniJson.texto(cliente.get("sha1")),
				null, null);

		// 4. Assets (sons, idiomas, texturas de fora do jar)
		Map<String, Object> indice = MiniJson.objeto(vanilla.get("assetIndex"));
		String idIndice = MiniJson.texto(indice.get("id"));
		File raizAssets = prepararAssets(indice, cache);

		// 5. Linha de comando
		StringBuilder cp = new StringBuilder();
		for (File jar : classpath.values()) {
			cp.append(jar.getAbsolutePath()).append(File.pathSeparator);
		}
		cp.append(jarCliente.getAbsolutePath());

		String principal = MiniJson.texto(infoForge.get("mainClass"));
		if (principal == null) {
			principal = "net.minecraft.launchwrapper.Launch";
		}
		String argumentos = MiniJson.texto(infoForge.get("minecraftArguments"));
		if (argumentos == null) {
			argumentos = MiniJson.texto(vanilla.get("minecraftArguments"))
					+ " --tweakClass net.minecraftforge.fml.common.launcher.FMLTweaker";
		}

		String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nick).getBytes(StandardCharsets.UTF_8))
				.toString().replace("-", "");
		Map<String, String> valores = new LinkedHashMap<>();
		valores.put("${auth_player_name}", nick);
		valores.put("${version_name}", MiniJson.texto(infoForge.get("id")) == null ? versaoMc
				: MiniJson.texto(infoForge.get("id")));
		valores.put("${game_directory}", gameDir.getAbsolutePath());
		valores.put("${assets_root}", raizAssets.getAbsolutePath());
		valores.put("${game_assets}", raizAssets.getAbsolutePath());
		valores.put("${assets_index_name}", idIndice);
		valores.put("${auth_uuid}", uuid);
		valores.put("${auth_access_token}", "0");
		valores.put("${auth_session}", "token:0:" + uuid);
		valores.put("${user_properties}", "{}");
		valores.put("${user_type}", "legacy");

		List<String> comando = new ArrayList<>();
		comando.add(executavelJava(javaHome));
		// Memória conforme a RAM real do computador (em PC de 2 GB o jogo usa bem menos).
		PerfilMemoria memoria = PerfilMemoria.paraVersao(versaoMc);
		comando.add("-Xms" + memoria.jogoMinMb + "M");
		comando.add("-Xmx" + memoria.jogoMaxMb + "M");
		comando.addAll(memoria.flagsJogo);
		System.out.println("[BCraftOS] Modo normal - perfil de memória: " + memoria);
		comando.add("-Djava.library.path=" + pastaNatives.getAbsolutePath());
		comando.add("-Dminecraft.launcher.brand=BCraftOS");
		comando.add("-cp");
		comando.add(cp.toString());
		comando.add(principal);
		for (String parte : argumentos.trim().split("\\s+")) {
			String valor = parte;
			for (Map.Entry<String, String> e : valores.entrySet()) {
				valor = valor.replace(e.getKey(), e.getValue());
			}
			comando.add(valor);
		}
		System.out.println("[BCraftOS] Modo normal: Java " + executavelJava(javaHome));
		System.out.println("[BCraftOS] Modo normal: nick " + nick + ", pasta do jogo " + gameDir);
		return comando;
	}

	// ------------------------------------------------------------------
	// Descrições e instalador
	// ------------------------------------------------------------------

	private static Map<String, Object> obterJsonDaVersao(String versaoMc, File cache) throws Exception {
		File arquivo = new File(new File(new File(cache, "versions"), versaoMc), versaoMc + ".json");
		if (!arquivo.isFile() || arquivo.length() == 0) {
			System.out.println("[BCraftOS] Buscando a descrição do Minecraft " + versaoMc + "...");
			Map<String, Object> manifesto = MiniJson.objeto(MiniJson.ler(baixarTexto(URL_MANIFESTO)));
			String endereco = null;
			for (Object item : MiniJson.lista(manifesto.get("versions"))) {
				Map<String, Object> v = MiniJson.objeto(item);
				if (v != null && versaoMc.equals(MiniJson.texto(v.get("id")))) {
					endereco = MiniJson.texto(v.get("url"));
					break;
				}
			}
			if (endereco == null) {
				throw new IllegalStateException("A Mojang não lista a versão " + versaoMc + ".");
			}
			baixarArquivo(endereco, arquivo);
		}
		return MiniJson.objeto(MiniJson.ler(new String(Files.readAllBytes(arquivo.toPath()), StandardCharsets.UTF_8)));
	}

	private static Map<String, Object> obterPerfilForge(String codigoForge, File cache) throws Exception {
		File instalador = new File(new File(new File(cache, "forge"), codigoForge), "installer.jar");
		if (!instalador.isFile() || instalador.length() == 0) {
			System.out.println("[BCraftOS] Baixando o instalador do Forge " + codigoForge + "...");
			baixarArquivo(URL_FORGE + codigoForge + "/forge-" + codigoForge + "-installer.jar", instalador);
		}
		try (ZipFile zip = new ZipFile(instalador)) {
			ZipEntry entrada = zip.getEntry("install_profile.json");
			if (entrada == null) {
				throw new IllegalStateException("O instalador do Forge não traz o install_profile.json.");
			}
			try (InputStream in = zip.getInputStream(entrada)) {
				return MiniJson.objeto(MiniJson.ler(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
			}
		}
	}

	private static void extrairDoZip(File zipArquivo, String nomeDentro, File destino) throws IOException {
		try (ZipFile zip = new ZipFile(zipArquivo)) {
			ZipEntry entrada = zip.getEntry(nomeDentro);
			if (entrada == null) {
				throw new IOException("O instalador do Forge não traz o arquivo " + nomeDentro);
			}
			destino.getParentFile().mkdirs();
			try (InputStream in = zip.getInputStream(entrada)) {
				Files.copy(in, destino.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	// ------------------------------------------------------------------
	// Bibliotecas, natives e assets
	// ------------------------------------------------------------------

	private static void garantirArquivo(File arquivo, String endereco, String sha1, String caminhoRelativo,
			String urlBase) throws Exception {
		if (arquivo.isFile() && arquivo.length() > 0 && (sha1 == null || sha1.equalsIgnoreCase(sha1De(arquivo)))) {
			return;
		}
		if (endereco != null) {
			baixarArquivo(endereco, arquivo);
		} else if (caminhoRelativo != null) {
			baixarDeRepositorios(caminhoRelativo, urlBase, arquivo);
		} else {
			throw new IOException("Sem endereço para baixar " + arquivo.getName());
		}
	}

	private static void baixarDeRepositorios(String caminhoRelativo, String urlDaBiblioteca, File destino)
			throws Exception {
		Set<String> bases = new LinkedHashSet<>();
		if (urlDaBiblioteca != null && !urlDaBiblioteca.isEmpty()) {
			String https = urlDaBiblioteca.startsWith("http://") ? "https://" + urlDaBiblioteca.substring(7)
					: urlDaBiblioteca;
			bases.add(https.endsWith("/") ? https : https + "/");
		}
		for (String base : REPOSITORIOS) {
			bases.add(base);
		}
		Exception ultimo = null;
		for (String base : bases) {
			try {
				baixarArquivo(base + caminhoRelativo, destino);
				return;
			} catch (Exception e) {
				ultimo = e;
			}
		}
		throw new IOException("Não consegui baixar a biblioteca " + caminhoRelativo
				+ (ultimo == null ? "" : " (" + ultimo.getMessage() + ")"));
	}

	private static void prepararNatives(List<File> jars, File pastaNatives) throws IOException {
		File marcador = new File(pastaNatives, ".ok");
		if (marcador.isFile()) {
			return;
		}
		pastaNatives.mkdirs();
		for (File jar : jars) {
			try (ZipInputStream zip = new ZipInputStream(new FileInputStream(jar))) {
				ZipEntry entrada;
				while ((entrada = zip.getNextEntry()) != null) {
					String nome = entrada.getName();
					if (entrada.isDirectory() || nome.startsWith("META-INF/") || nome.contains("..")) {
						continue;
					}
					File saida = new File(pastaNatives, new File(nome).getName());
					try (OutputStream out = new FileOutputStream(saida)) {
						zip.transferTo(out);
					}
				}
			}
		}
		Files.write(marcador.toPath(), "ok".getBytes(StandardCharsets.UTF_8));
	}

	private static File prepararAssets(Map<String, Object> indice, File cache) throws Exception {
		String id = MiniJson.texto(indice.get("id"));
		// Se o Gradle já baixou os assets dessa versão, reaproveita e só completa o que faltar.
		File doGradle = new File(System.getProperty("user.home"), ".gradle/caches/minecraft/assets");
		File raiz = new File(doGradle, "indexes/" + id + ".json").isFile() ? doGradle : new File(cache, "assets");

		File arquivoIndice = new File(raiz, "indexes/" + id + ".json");
		if (!arquivoIndice.isFile() || arquivoIndice.length() == 0) {
			baixarArquivo(MiniJson.texto(indice.get("url")), arquivoIndice);
		}
		Map<String, Object> conteudo = MiniJson.objeto(MiniJson.ler(
				new String(Files.readAllBytes(arquivoIndice.toPath()), StandardCharsets.UTF_8)));
		Map<String, Object> objetos = MiniJson.objeto(conteudo.get("objects"));

		Map<String, Long> faltando = new LinkedHashMap<>();
		for (Object valor : objetos.values()) {
			Map<String, Object> o = MiniJson.objeto(valor);
			String hash = MiniJson.texto(o.get("hash"));
			long tamanho = ((Double) o.get("size")).longValue();
			File arquivo = new File(raiz, "objects/" + hash.substring(0, 2) + "/" + hash);
			if (!arquivo.isFile() || arquivo.length() != tamanho) {
				faltando.put(hash, tamanho);
			}
		}
		if (faltando.isEmpty()) {
			return raiz;
		}

		System.out.println("[BCraftOS] Baixando " + faltando.size() + " arquivos de assets (só na primeira vez)...");
		ExecutorService pool = Executors.newFixedThreadPool(PerfilMemoria.detectar().downloadsSimultaneos());
		AtomicInteger feitos = new AtomicInteger();
		List<Future<?>> tarefas = new ArrayList<>();
		for (String hash : faltando.keySet()) {
			tarefas.add(pool.submit(() -> {
				File arquivo = new File(raiz, "objects/" + hash.substring(0, 2) + "/" + hash);
				Exception erro = null;
				for (int tentativa = 0; tentativa < 3; tentativa++) {
					try {
						baixarArquivo(URL_ASSETS + hash.substring(0, 2) + "/" + hash, arquivo);
						erro = null;
						break;
					} catch (Exception e) {
						erro = e;
					}
				}
				if (erro != null) {
					throw new IOException("Falha ao baixar o asset " + hash + ": " + erro.getMessage());
				}
				int n = feitos.incrementAndGet();
				if (n % 100 == 0) {
					System.out.println("[BCraftOS] Assets: " + n + "/" + faltando.size());
				}
				return null;
			}));
		}
		pool.shutdown();
		for (Future<?> tarefa : tarefas) {
			tarefa.get();
		}
		return raiz;
	}

	// ------------------------------------------------------------------
	// Utilidades
	// ------------------------------------------------------------------

	/** "grupo:artefato:versao" vira "grupo/com/barras/artefato/versao/artefato-versao.jar". */
	static String caminhoMaven(String nome) {
		String[] p = nome.split(":");
		String grupo = p[0].replace('.', '/');
		String arquivo = p[1] + "-" + p[2] + (p.length > 3 ? "-" + p[3] : "") + ".jar";
		return grupo + "/" + p[1] + "/" + p[2] + "/" + arquivo;
	}

	/** Chave para não repetir a mesma biblioteca em duas versões: grupo:artefato[:classificador]. */
	static String chaveDaBiblioteca(String nome) {
		String[] p = nome.split(":");
		return p[0] + ":" + p[1] + (p.length > 3 ? ":" + p[3] : "");
	}

	/** Regras "allow/disallow" por sistema operacional, como nos arquivos de versão da Mojang. */
	static boolean regrasPermitem(Object regras) {
		List<Object> lista = MiniJson.lista(regras);
		if (lista.isEmpty()) {
			return true;
		}
		boolean permitido = false;
		for (Object item : lista) {
			Map<String, Object> regra = MiniJson.objeto(item);
			if (regra == null || regra.get("features") != null) {
				continue;
			}
			Map<String, Object> so = MiniJson.objeto(regra.get("os"));
			boolean vale = so == null || nomeDoSistema().equals(MiniJson.texto(so.get("name")));
			if (vale) {
				permitido = "allow".equals(MiniJson.texto(regra.get("action")));
			}
		}
		return permitido;
	}

	static String nomeDoSistema() {
		String so = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (so.contains("win")) {
			return "windows";
		}
		if (so.contains("mac") || so.contains("darwin")) {
			return "osx";
		}
		return "linux";
	}

	private static String executavelJava(String javaHome) {
		File unix = new File(javaHome, "bin/java");
		return unix.isFile() ? unix.getAbsolutePath() : new File(javaHome, "bin/java.exe").getAbsolutePath();
	}

	private static String sha1De(File arquivo) {
		try (InputStream in = new FileInputStream(arquivo)) {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] buffer = new byte[65536];
			int lido;
			while ((lido = in.read(buffer)) != -1) {
				md.update(buffer, 0, lido);
			}
			StringBuilder sb = new StringBuilder();
			for (byte b : md.digest()) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception e) {
			return "";
		}
	}

	/** Abre a conexão seguindo redirecionamentos, inclusive de http para https. */
	private static HttpURLConnection conectar(String endereco) throws Exception {
		String atual = endereco;
		for (int i = 0; i < 6; i++) {
			HttpURLConnection c = CatalogoVersoes.abrir(atual);
			c.setInstanceFollowRedirects(false);
			int codigo = c.getResponseCode();
			if (codigo == 301 || codigo == 302 || codigo == 303 || codigo == 307 || codigo == 308) {
				String destino = c.getHeaderField("Location");
				c.disconnect();
				if (destino == null) {
					break;
				}
				atual = destino;
				continue;
			}
			if (codigo != HttpURLConnection.HTTP_OK) {
				c.disconnect();
				throw new IOException("HTTP " + codigo + " em " + endereco);
			}
			return c;
		}
		throw new IOException("Redirecionamentos demais em " + endereco);
	}

	private static String baixarTexto(String endereco) throws Exception {
		HttpURLConnection c = conectar(endereco);
		try (InputStream in = c.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} finally {
			c.disconnect();
		}
	}

	private static void baixarArquivo(String endereco, File destino) throws Exception {
		destino.getParentFile().mkdirs();
		File parcial = new File(destino.getPath() + "." + System.nanoTime() + ".part");
		HttpURLConnection c = conectar(endereco);
		try (InputStream in = c.getInputStream()) {
			Files.copy(in, parcial.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} finally {
			c.disconnect();
		}
		Files.move(parcial.toPath(), destino.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}
}
