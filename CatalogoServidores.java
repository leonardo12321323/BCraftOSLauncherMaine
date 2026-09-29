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
 * Segue a mesma regra do catálogo de clientes (CatalogoVersoes): as versões de Minecraft vêm
 * do manifesto oficial da Mojang, ficam entre VERSAO_MINIMA e VERSAO_MAXIMA, e Forge, NeoForge
 * e Fabric usam exatamente a mesma lista do cliente (é o CatalogoVersoes quem responde).
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

	public static final String[] TIPOS = {PAPER, PURPUR, SPIGOT, VANILLA, FORGE, NEOFORGE, FABRIC, VELOCITY};

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
	public enum Categoria { PLUGINS, MODS, PURO, PROXY }

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
			default:
				return null;
		}
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
			case NEOFORGE:
			case FABRIC:
				// Mesma lista do cliente: o servidor combina 100% com a versão que se joga.
				return CatalogoVersoes.listarVersoes(tipo);
			case VANILLA:
				return listarVanilla();
			case PAPER:
				return listarPelaPaperMC("paper");
			case PURPUR:
				return listarPurpur();
			case SPIGOT:
				return listarSpigot();
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
			Matcher m = Pattern.compile("href=\"(1\\.[0-9]+(?:\\.[0-9]+)?)\\.json\"").matcher(pagina);
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
	// Mojang
	// ------------------------------------------------------------------

	/** Lançamentos oficiais entre a versão mínima e a máxima do launcher, com o JSON de cada um. */
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
						&& CatalogoVersoes.comparar(id, CatalogoVersoes.VERSAO_MINIMA) >= 0
						&& CatalogoVersoes.comparar(id, CatalogoVersoes.VERSAO_MAXIMA) <= 0) {
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
