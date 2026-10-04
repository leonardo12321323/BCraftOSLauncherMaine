package BCraftOSproject1.BCraftOS1;



import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Catálogo dos tipos de SERVIDOR que o launcher sabe criar, e das versões de cada um.
 *
 * Segue a mesma ideia do catálogo de clientes (CatalogoVersoes): as versões de Minecraft vêm
 * do manifesto oficial da Mojang, a partir de VERSAO_MINIMA. A diferença é que o servidor NÃO
 * tem limite de versão nova: aparece tudo que a Mojang lançou, inclusive a linha 26.x
 * (o Minecraft trocou de 1.21.x para 26.1, 26.2... e essas versões exigem Java 25).
 *
 * Tipos:
 *   Paper, Purpur, Spigot ... servidores com PLUGINS (pasta plugins/)
 *   Vanilla ................. servidor oficial da Mojang, sem mods nem plugins
 *   Forge, NeoForge, Fabric . servidores com MODS (pasta mods/)
 *   Velocity ................ NÃO é um servidor de jogo: é um proxy que liga vários servidores
 *
 * Fontes oficiais:
 *   Paper e Velocity: fill.papermc.io (API v3 da PaperMC)
 *   Purpur:           api.purpurmc.org
 *   Spigot:           BuildTools oficial (hub.spigotmc.org) — o Spigot não distribui o .jar pronto
 *   Vanilla:          servidores da Mojang, pelo manifesto de versões
 *   Forge/NeoForge:   instaladores oficiais nos mavens de cada projeto
 *   Fabric:           meta.fabricmc.net
 */
public class CatalogoServidores {

	public static final String PAPER = "Paper";
	public static final String PURPUR = "Purpur";
	public static final String SPIGOT = "Spigot";
	public static final String VANILLA = "Vanilla";
	public static final String FORGE = CatalogoVersoes.FORGE;
	public static final String NEOFORGE = CatalogoVersoes.NEOFORGE;
	public static final String FABRIC = CatalogoVersoes.FABRIC;
	public static final String VELOCITY = "Velocity";
	/** Híbrido: roda plugins (Bukkit/Spigot/Paper) E mods (Forge, NeoForge ou Fabric) no mesmo servidor. */
	public static final String ARCLIGHT = "Arclight";

	public static final String[] TIPOS = {PAPER, PURPUR, SPIGOT, VANILLA, FORGE, NEOFORGE, FABRIC, ARCLIGHT, VELOCITY};

	public static final String URL_BUILDTOOLS =
			"https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar";

	private static final int TIMEOUT_MS = 20000;
	/** A PaperMC exige um User-Agent que identifique o programa e tenha um contato. */
	private static final String USER_AGENT = "BCraftOS-Launcher/1.0 (https://www.youtube.com/@BCraftOS)";
	private static final String MANIFESTO_MOJANG =
			"https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
	private static final String FILL = "https://fill.papermc.io/v3/projects/";

	/** Mojang: id da versão -> endereço do JSON dela. Só lançamentos entre a mínima e a máxima. */
	private static Map<String, String> versoesMojang;

	/** O que o servidor traz para o jogador colocar: plugins, mods ou nada. */
	public enum Categoria { PLUGINS, MODS, HIBRIDO, PURO, PROXY }

	public static Categoria categoria(String tipo) {
		switch (tipo) {
			case PAPER:
			case PURPUR:
			case SPIGOT:
				return Categoria.PLUGINS;
			case FORGE:
			case NEOFORGE:
			case FABRIC:
				return Categoria.MODS;
			case ARCLIGHT:
				return Categoria.HIBRIDO;
			case VELOCITY:
				return Categoria.PROXY;
			default:
				return Categoria.PURO;
		}
	}

	/** Nome da pasta onde o jogador coloca os .jar deste tipo de servidor (ou null). */
	public static String pastaExtra(String tipo) {
		switch (categoria(tipo)) {
			case PLUGINS:
				return "plugins";
			case MODS:
				return "mods";
			case HIBRIDO:
				return "plugins"; // a principal; o mods/ também é criado (veja pastasExtras)
			default:
				return null;
		}
	}

	/** Todas as pastas onde o jogador coloca arquivos: uma para a maioria, duas para o Arclight. */
	public static String[] pastasExtras(String tipo) {
		if (categoria(tipo) == Categoria.HIBRIDO) {
			return new String[]{"plugins", "mods"};
		}
		String unica = pastaExtra(tipo);
		return unica == null ? new String[0] : new String[]{unica};
	}

	public static boolean ehProxy(String tipo) {
		return categoria(tipo) == Categoria.PROXY;
	}

	public static String descricao(String tipo) {
		switch (tipo) {
			case PAPER:
				return "Rápido e estável, aceita plugins. O mais recomendado para servidores de amigos.";
			case PURPUR:
				return "Baseado no Paper, com muitas opções extras de configuração. Aceita plugins.";
			case SPIGOT:
				return "O clássico dos plugins. Compila na hora (precisa do git no Linux/Mac e demora uns minutos).";
			case VANILLA:
				return "O servidor oficial da Mojang: sem mods e sem plugins.";
			case FORGE:
				return "Servidor com mods do Forge (pasta mods).";
			case NEOFORGE:
				return "Servidor com mods do NeoForge (pasta mods).";
			case FABRIC:
				return "Servidor leve com mods do Fabric (pasta mods). A primeira vez que liga baixa o resto.";
			case ARCLIGHT:
				return "[BUILD DE TESTE] Híbrido: aceita plugins (pasta plugins) E mods (pasta mods) no mesmo servidor. "
						+ "Combinar os dois pode dar incompatibilidades. Só há versões a partir da 1.18; na 1.21 só a 1.21.1. "
						+ "A primeira vez que liga baixa o resto.";
			case VELOCITY:
				return "Proxy: liga vários servidores em um só endereço. Não é um servidor de jogo.";
			default:
				return " ";
		}
	}

	/** Lista as versões disponíveis do tipo, da mais nova para a mais antiga. */
	public static List<CatalogoVersoes.ItemVersao> listarVersoes(String tipo) throws Exception {
		switch (tipo) {
			case FORGE:
				return listarForge();
			case NEOFORGE:
				return listarNeoForge();
			case FABRIC:
				return listarFabric();
			case VANILLA:
				return listarVanilla();
			case PAPER:
				return listarPelaPaperMC("paper");
			case PURPUR:
				return listarPurpur();
			case SPIGOT:
				return listarSpigot();
			case ARCLIGHT:
				return listarArclight();
			case VELOCITY:
				return listarVelocity();
			default:
				throw new IllegalArgumentException("Tipo de servidor desconhecido: " + tipo);
		}
	}

	// ------------------------------------------------------------------
	// Listas por tipo
	// ------------------------------------------------------------------

	private static List<CatalogoVersoes.ItemVersao> listarVanilla() throws Exception {
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		for (String versao : obterVersoesMojang().keySet()) {
			itens.add(new CatalogoVersoes.ItemVersao(versao, "Vanilla " + versao, versao));
		}
		ordenar(itens);
		return itens;
	}

	/** Paper (e só ele, aqui) publica pela API v3: {"versions": {"1.21": ["1.21.1", ...], ...}}. */
	private static List<CatalogoVersoes.ItemVersao> listarPelaPaperMC(String projeto) throws Exception {
		Map<String, String> oficiais = obterVersoesMojang();
		Map<String, Object> raiz = MiniJson.objeto(MiniJson.ler(baixarTexto(FILL + projeto)));
		Map<String, Object> grupos = raiz == null ? null : MiniJson.objeto(raiz.get("versions"));
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		if (grupos != null) {
			for (Object grupo : grupos.values()) {
				for (Object v : MiniJson.lista(grupo)) {
					String versao = MiniJson.texto(v);
					if (versao != null && oficiais.containsKey(versao)) {
						itens.add(new CatalogoVersoes.ItemVersao(versao,
								nomeBonito(projeto) + " " + versao, versao));
					}
				}
			}
		}
		ordenar(itens);
		return itens;
	}

	private static List<CatalogoVersoes.ItemVersao> listarPurpur() throws Exception {
		Map<String, String> oficiais = obterVersoesMojang();
		Map<String, Object> raiz = MiniJson.objeto(MiniJson.ler(baixarTexto("https://api.purpurmc.org/v2/purpur")));
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		if (raiz != null) {
			for (Object v : MiniJson.lista(raiz.get("versions"))) {
				String versao = MiniJson.texto(v);
				if (versao != null && oficiais.containsKey(versao)) {
					itens.add(new CatalogoVersoes.ItemVersao(versao, "Purpur " + versao, versao));
				}
			}
		}
		ordenar(itens);
		return itens;
	}

	/**
	 * O Spigot publica um arquivo "<versão>.json" para cada versão que o BuildTools sabe compilar.
	 * Se a página de listagem não puder ser lida, usa todas as versões oficiais: o BuildTools
	 * recusa sozinho as que não existem e o motivo aparece na tela.
	 */
	private static List<CatalogoVersoes.ItemVersao> listarSpigot() throws Exception {
		Map<String, String> oficiais = obterVersoesMojang();
		List<String> compilaveis = new ArrayList<>();
		try {
			String pagina = baixarTexto("https://hub.spigotmc.org/versions/");
			Matcher m = Pattern.compile("href=\"([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)\\.json\"").matcher(pagina);
			while (m.find()) {
				if (oficiais.containsKey(m.group(1)) && !compilaveis.contains(m.group(1))) {
					compilaveis.add(m.group(1));
				}
			}
		} catch (IOException e) {
			System.err.println("[BCraftOS Aviso] Não consegui ler a lista do Spigot: " + e.getMessage());
		}
		if (compilaveis.isEmpty()) {
			compilaveis.addAll(oficiais.keySet());
		}

		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		for (String versao : compilaveis) {
			itens.add(new CatalogoVersoes.ItemVersao(versao, "Spigot " + versao, versao));
		}
		ordenar(itens);
		return itens;
	}

	// ------------------------------------------------------------------
	// Arclight (híbrido: plugins + mods)
	// ------------------------------------------------------------------

	private static final String ARCLIGHT_RELEASES =
			"https://api.github.com/repos/IzzelAliz/Arclight/releases?per_page=100";

	/** codigo da versão -> endereço do .jar, preenchido quando a lista é montada. */
	private static final Map<String, String> ENDERECOS_ARCLIGHT = new java.util.concurrent.ConcurrentHashMap<>();

	private static List<CatalogoVersoes.ItemVersao> listarArclight() throws Exception {
		return interpretarArclight(baixarTexto(ARCLIGHT_RELEASES));
	}

	/**
	 * Lê a lista de lançamentos do Arclight no GitHub. Cada lançamento traz um .jar por loader
	 * (Forge, NeoForge, Fabric); o Minecraft de cada um vem do texto do lançamento ou do nome do arquivo.
	 * Fica só o lançamento mais novo de cada combinação (Minecraft + loader), sem pré-lançamentos.
	 */
	static List<CatalogoVersoes.ItemVersao> interpretarArclight(String json) {
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		java.util.Set<String> vistos = new java.util.HashSet<>();
		java.util.regex.Pattern mcNoTexto = java.util.regex.Pattern
				.compile("Minecraft\\s+(1\\.\\d{1,2}(?:\\.\\d{1,2})?|\\d{2}\\.\\d{1,2}(?:\\.\\d{1,2})?)");
		java.util.regex.Pattern mcNoNome = java.util.regex.Pattern
				.compile("(?<![0-9.])(1\\.(?:1[2-9]|2[0-9])(?:\\.\\d{1,2})?)(?![0-9])");

		for (Object item : MiniJson.lista(MiniJson.ler(json))) {
			Map<String, Object> lancamento = MiniJson.objeto(item);
			if (lancamento == null || Boolean.TRUE.equals(lancamento.get("prerelease"))
					|| Boolean.TRUE.equals(lancamento.get("draft"))) {
				continue;
			}
			String tag = MiniJson.texto(lancamento.get("tag_name"));
			String corpo = MiniJson.texto(lancamento.get("body"));
			String mcDoCorpo = null;
			if (corpo != null) {
				java.util.regex.Matcher m = mcNoTexto.matcher(corpo);
				if (m.find()) {
					mcDoCorpo = m.group(1);
				}
			}
			for (Object a : MiniJson.lista(lancamento.get("assets"))) {
				Map<String, Object> arquivo = MiniJson.objeto(a);
				if (arquivo == null) {
					continue;
				}
				String nome = MiniJson.texto(arquivo.get("name"));
				String url = MiniJson.texto(arquivo.get("browser_download_url"));
				if (nome == null || url == null || !nome.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
					continue;
				}
				String minusculo = nome.toLowerCase(java.util.Locale.ROOT);
				String loader;
				if (minusculo.contains("neoforge")) {
					loader = "NeoForge";
				} else if (minusculo.contains("forge")) {
					loader = "Forge";
				} else if (minusculo.contains("fabric")) {
					loader = "Fabric";
				} else {
					continue; // outros arquivos (ex.: código-fonte) não servem para rodar
				}
				String mc = mcDoCorpo;
				java.util.regex.Matcher doNome = mcNoNome.matcher(nome);
				if (doNome.find()) {
					mc = doNome.group(1); // o nome do arquivo é mais confiável que o texto
				}
				if (mc == null || !vistos.add(mc + "|" + loader)) {
					continue; // sem versão do jogo, ou já pegou um lançamento mais novo desta combinação
				}
				String codigo = loader + "-" + mc + "-" + (tag == null ? "build" : tag.replaceAll("[^A-Za-z0-9._-]", "_"));
				ENDERECOS_ARCLIGHT.put(codigo, url);
				itens.add(new CatalogoVersoes.ItemVersao(codigo,
						"Arclight (teste) " + mc + "  ·  " + loader + "  ·  " + (tag == null ? "" : tag), mc));
			}
		}
		itens.sort((x, y) -> {
			int c = CatalogoVersoes.comparar(y.versaoMc, x.versaoMc);
			return c != 0 ? c : x.rotulo.compareTo(y.rotulo);
		});
		return itens;
	}

	/** Endereço do .jar do Arclight para o código escolhido na lista. */
	public static String enderecoArclight(String codigo) throws Exception {
		String url = ENDERECOS_ARCLIGHT.get(codigo);
		if (url == null) {
			listarArclight(); // a lista ainda não foi montada nesta execução
			url = ENDERECOS_ARCLIGHT.get(codigo);
		}
		if (url == null) {
			throw new IOException("Não achei o download do Arclight " + codigo + " no GitHub. "
					+ "Escolha a versão de novo na lista.");
		}
		return url;
	}

	/** O Velocity não segue as versões do Minecraft: cada linha é uma versão do próprio proxy. */
	private static List<CatalogoVersoes.ItemVersao> listarVelocity() throws Exception {
		Map<String, Object> raiz = MiniJson.objeto(MiniJson.ler(baixarTexto(FILL + "velocity")));
		Map<String, Object> grupos = raiz == null ? null : MiniJson.objeto(raiz.get("versions"));
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		if (grupos != null) {
			// A API já entrega da mais nova para a mais antiga: a ordem é mantida.
			for (Object grupo : grupos.values()) {
				for (Object v : MiniJson.lista(grupo)) {
					String versao = MiniJson.texto(v);
					if (versao != null) {
						itens.add(new CatalogoVersoes.ItemVersao(versao, "Velocity " + versao, versao));
					}
				}
			}
		}
		return itens;
	}

	private static String nomeBonito(String projeto) {
		return Character.toUpperCase(projeto.charAt(0)) + projeto.substring(1);
	}

	private static void ordenar(List<CatalogoVersoes.ItemVersao> itens) {
		itens.sort((a, b) -> CatalogoVersoes.comparar(b.versaoMc, a.versaoMc));
	}

	// ------------------------------------------------------------------
	// Forge, NeoForge e Fabric (sem limite de versão nova, ao contrário do cliente)
	// ------------------------------------------------------------------

	/** Só o build mais novo de cada versão do jogo, como no catálogo de clientes. */
	private static List<CatalogoVersoes.ItemVersao> listarForge() throws Exception {
		String xml = baixarTexto("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
		return montarForge(xml, obterVersoesMojang().keySet());
	}

	static List<CatalogoVersoes.ItemVersao> montarForge(String xml, java.util.Set<String> oficiais) {
		Map<String, String> melhorBuild = new LinkedHashMap<>();
		Matcher matcher = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
		while (matcher.find()) {
			String completo = matcher.group(1).trim();
			int traco = completo.indexOf('-');
			if (traco <= 0) {
				continue;
			}
			String mc = completo.substring(0, traco);
			String build = completo.substring(traco + 1);
			if (!oficiais.contains(mc)) {
				continue;
			}
			String jaTem = melhorBuild.get(mc);
			if (jaTem == null || compararBuild(build, jaTem) > 0) {
				melhorBuild.put(mc, build);
			}
		}
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		for (Map.Entry<String, String> item : melhorBuild.entrySet()) {
			itens.add(new CatalogoVersoes.ItemVersao(item.getKey() + "-" + item.getValue(),
					"Forge " + item.getKey() + "  ·  build " + item.getValue(), item.getKey()));
		}
		ordenar(itens);
		return itens;
	}

	/** O Fabric usa a própria versão do jogo como versão do loader. Uma linha por versão. */
	private static List<CatalogoVersoes.ItemVersao> listarFabric() throws Exception {
		Map<String, String> oficiais = obterVersoesMojang();
		String json = baixarTexto("https://meta.fabricmc.net/v2/versions/game");
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		Matcher matcher = Pattern.compile("\\{[^{}]*\"version\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}").matcher(json);
		while (matcher.find()) {
			String versao = matcher.group(1);
			if (CatalogoVersoes.comparar(versao, CatalogoVersoes.FABRIC_MINIMO) < 0) {
				continue; // abaixo disso o Fabric não publica servidor
			}
			if (oficiais.containsKey(versao)) {
				itens.add(new CatalogoVersoes.ItemVersao(versao, "Fabric " + versao, versao));
			}
		}
		ordenar(itens);
		return itens;
	}

	private static List<CatalogoVersoes.ItemVersao> listarNeoForge() throws Exception {
		return montarNeoForge(baixarTexto(
				"https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"));
	}

	/**
	 * Para cada versão do jogo fica o build estável mais novo; se só existirem builds "-beta"
	 * (é o caso de várias 1.21.x), fica o beta mais novo, marcado no nome.
	 */
	static List<CatalogoVersoes.ItemVersao> montarNeoForge(String json) {
		Map<String, String> melhor = new LinkedHashMap<>();
		Matcher matcher = Pattern.compile("\"([0-9][0-9.]*(?:-beta)?)\"").matcher(json);
		while (matcher.find()) {
			String bruto = matcher.group(1);
			String mc = converterVersaoNeoForge(bruto);
			if (mc == null) {
				continue;
			}
			String jaTem = melhor.get(mc);
			if (jaTem == null || melhorQue(bruto, jaTem)) {
				melhor.put(mc, bruto);
			}
		}
		List<CatalogoVersoes.ItemVersao> itens = new ArrayList<>();
		for (Map.Entry<String, String> item : melhor.entrySet()) {
			itens.add(new CatalogoVersoes.ItemVersao(item.getValue(),
					"NeoForge " + item.getKey() + "  ·  build " + item.getValue(), item.getKey()));
		}
		ordenar(itens);
		return itens;
	}

	/** Estável sempre ganha de beta; entre iguais, o número maior ganha. */
	private static boolean melhorQue(String candidato, String atual) {
		boolean betaCandidato = candidato.endsWith("-beta");
		boolean betaAtual = atual.endsWith("-beta");
		if (betaCandidato != betaAtual) {
			return !betaCandidato;
		}
		return compararBuild(candidato.replace("-beta", ""), atual.replace("-beta", "")) > 0;
	}

	/**
	 * Descobre para qual Minecraft é um build do NeoForge:
	 *   "21.1.72"   -> 1.21.1    (esquema antigo: 21 = 1.21, 1 = .1)
	 *   "26.2.0.64" -> 26.2      (esquema novo, a partir da 26.1: a.b.c.build, com c = 0 omitido)
	 *   "26.1.2.95" -> 26.1.2
	 * Os builds "47.x" são do NeoForge da 1.20.1, que usa outro sistema e não entra na lista.
	 */
	static String converterVersaoNeoForge(String versao) {
		String[] partes = versao.replace("-beta", "").split("\\.");
		if (partes.length < 2) {
			return null;
		}
		try {
			int primeiro = Integer.parseInt(partes[0]);
			int segundo = Integer.parseInt(partes[1]);
			if (primeiro >= 26 && primeiro < 47) { // 47 é o NeoForge antigo da 1.20.1, não o ano 2047
				int terceiro = partes.length > 2 ? Integer.parseInt(partes[2]) : 0;
				return primeiro + "." + segundo + (terceiro != 0 ? "." + terceiro : "");
			}
			if (primeiro >= 20 && primeiro <= 25) {
				return "1." + primeiro + "." + segundo;
			}
			return null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Compara os números do build do loader (ex.: 47.4.0 é mais novo que 47.2.0). */
	private static int compararBuild(String a, String b) {
		String[] partesA = a.split("[.-]");
		String[] partesB = b.split("[.-]");
		int tamanho = Math.min(partesA.length, partesB.length);
		for (int i = 0; i < tamanho; i++) {
			if (partesA[i].equals(partesB[i])) {
				continue;
			}
			boolean numericoA = partesA[i].chars().allMatch(Character::isDigit);
			boolean numericoB = partesB[i].chars().allMatch(Character::isDigit);
			if (numericoA && numericoB) {
				return Integer.compare(Integer.parseInt(partesA[i]), Integer.parseInt(partesB[i]));
			}
			return partesA[i].compareTo(partesB[i]);
		}
		return Integer.compare(partesA.length, partesB.length);
	}

	/**
	 * Java exigido por versão do jogo. Da 26.1 em diante é o Java 25; para as versões
	 * anteriores vale a mesma regra do cliente (8, 17 ou 21).
	 */
	public static String javaNecessarioPara(String versaoMc) {
		if (versaoMc != null && CatalogoVersoes.comparar(versaoMc, "26.1") >= 0) {
			return "25";
		}
		return MinecraftLauncher.javaNecessarioPara(versaoMc);
	}

	// ------------------------------------------------------------------
	// Mojang
	// ------------------------------------------------------------------

	/** Lançamentos oficiais a partir da versão mínima do launcher, com o JSON de cada um. */
	private static synchronized Map<String, String> obterVersoesMojang() throws Exception {
		if (versoesMojang != null) {
			return versoesMojang;
		}
		Map<String, Object> raiz = MiniJson.objeto(MiniJson.ler(baixarTexto(MANIFESTO_MOJANG)));
		Map<String, String> encontradas = new LinkedHashMap<>();
		if (raiz != null) {
			for (Object item : MiniJson.lista(raiz.get("versions"))) {
				Map<String, Object> versao = MiniJson.objeto(item);
				if (versao == null || !"release".equals(MiniJson.texto(versao.get("type")))) {
					continue;
				}
				String id = MiniJson.texto(versao.get("id"));
				String url = MiniJson.texto(versao.get("url"));
				if (id != null && url != null
						&& CatalogoVersoes.comparar(id, CatalogoVersoes.VERSAO_MINIMA) >= 0) {
					encontradas.put(id, url);
				}
			}
		}
		if (encontradas.isEmpty()) {
			throw new IOException("A Mojang não devolveu nenhuma versão. Verifique sua internet.");
		}
		versoesMojang = encontradas;
		return versoesMojang;
	}

	// ------------------------------------------------------------------
	// Endereços de download (só o endereço: quem baixa é o GerenciadorServidores)
	// ------------------------------------------------------------------

	/** Servidor oficial da Mojang: o JSON da versão aponta para o .jar. */
	public static String enderecoVanilla(String versaoMc) throws Exception {
		String urlJson = obterVersoesMojang().get(versaoMc);
		if (urlJson == null) {
			throw new IOException("A Mojang não tem a versão " + versaoMc + ".");
		}
		Map<String, Object> json = MiniJson.objeto(MiniJson.ler(baixarTexto(urlJson)));
		Map<String, Object> downloads = json == null ? null : MiniJson.objeto(json.get("downloads"));
		Map<String, Object> servidor = downloads == null ? null : MiniJson.objeto(downloads.get("server"));
		String url = servidor == null ? null : MiniJson.texto(servidor.get("url"));
		if (url == null) {
			throw new IOException("A Mojang não publica servidor para a versão " + versaoMc + ".");
		}
		return url;
	}

	/** Paper e Velocity: o build estável mais novo (ou o mais novo de todos, se não houver estável). */
	public static String enderecoPelaPaperMC(String projeto, String versao) throws Exception {
		return escolherBuild(baixarTexto(FILL + projeto + "/versions/" + versao + "/builds"), projeto, versao);
	}

	/** Lê a resposta da API (lista de builds, do mais novo ao mais antigo) e devolve o endereço do .jar. */
	static String escolherBuild(String jsonBuilds, String projeto, String versao) throws IOException {
		String qualquer = null;
		for (Object item : MiniJson.lista(MiniJson.ler(jsonBuilds))) {
			Map<String, Object> build = MiniJson.objeto(item);
			if (build == null) {
				continue;
			}
			Map<String, Object> downloads = MiniJson.objeto(build.get("downloads"));
			Map<String, Object> servidor = downloads == null ? null : MiniJson.objeto(downloads.get("server:default"));
			String url = servidor == null ? null : MiniJson.texto(servidor.get("url"));
			if (url == null) {
				continue;
			}
			if ("STABLE".equals(MiniJson.texto(build.get("channel")))) {
				return url;
			}
			if (qualquer == null) {
				qualquer = url;
			}
		}
		if (qualquer == null) {
			throw new IOException("Não achei nenhum build de " + nomeBonito(projeto) + " para a versão " + versao + ".");
		}
		return qualquer;
	}

	public static String enderecoPurpur(String versao) {
		return "https://api.purpurmc.org/v2/purpur/" + versao + "/latest/download";
	}

	/** O Fabric monta o .jar do servidor na hora, com o loader e o instalador estáveis mais novos. */
	public static String enderecoFabric(String versaoMc) throws Exception {
		String loader = primeiraVersaoEstavel("https://meta.fabricmc.net/v2/versions/loader");
		String instalador = primeiraVersaoEstavel("https://meta.fabricmc.net/v2/versions/installer");
		return "https://meta.fabricmc.net/v2/versions/loader/" + versaoMc + "/" + loader + "/" + instalador
				+ "/server/jar";
	}

	private static String primeiraVersaoEstavel(String endereco) throws Exception {
		for (Object item : MiniJson.lista(MiniJson.ler(baixarTexto(endereco)))) {
			Map<String, Object> mapa = MiniJson.objeto(item);
			if (mapa != null && Boolean.TRUE.equals(mapa.get("stable")) && MiniJson.texto(mapa.get("version")) != null) {
				return MiniJson.texto(mapa.get("version"));
			}
		}
		throw new IOException("Não achei uma versão estável em " + endereco);
	}

	public static String enderecoInstaladorForge(String codigo) {
		return "https://maven.minecraftforge.net/net/minecraftforge/forge/" + codigo
				+ "/forge-" + codigo + "-installer.jar";
	}

	public static String enderecoInstaladorNeoForge(String codigo) {
		return "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + codigo
				+ "/neoforge-" + codigo + "-installer.jar";
	}

	// ------------------------------------------------------------------
	// Rede
	// ------------------------------------------------------------------

	/** Abre a conexão já com o User-Agent que a PaperMC e os outros serviços pedem. */
	public static HttpURLConnection abrir(String endereco) throws Exception {
		HttpURLConnection conexao = (HttpURLConnection) new URI(endereco).toURL().openConnection();
		conexao.setConnectTimeout(TIMEOUT_MS);
		conexao.setReadTimeout(TIMEOUT_MS);
		conexao.setRequestProperty("User-Agent", USER_AGENT);
		conexao.setInstanceFollowRedirects(true);
		return conexao;
	}

	static String baixarTexto(String endereco) throws Exception {
		HttpURLConnection conexao = abrir(endereco);
		try {
			int codigo = conexao.getResponseCode();
			if (codigo != HttpURLConnection.HTTP_OK) {
				throw new IOException("O servidor respondeu " + codigo + " em " + endereco);
			}
			try (InputStream entrada = conexao.getInputStream();
					BufferedReader leitor = new BufferedReader(
							new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
				StringBuilder texto = new StringBuilder();
				String linha;
				while ((linha = leitor.readLine()) != null) {
					texto.append(linha).append('\n');
				}
				return texto.toString();
			}
		} finally {
			conexao.disconnect();
		}
	}
}
