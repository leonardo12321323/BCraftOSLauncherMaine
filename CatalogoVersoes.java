package BCraftOSproject1.BCraftOS1;



import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fala com a internet para descobrir quais versões CARREGÁVEIS de cada loader existem.
 *
 * A ideia é simples: a lista mostra uma versão por linha, só a mais estável, sem encher
 * a tela com centenas de builds. Quando o Forge tem 47 builds para a 1.20.1, aparece só
 * o mais recente daquela versão — que é o que interessa para jogar.
 *
 * Os loaders são Forge, Fabric e NeoForge. O Vanilla (Minecraft puro) não tem kit de
 * desenvolvimento para baixar — é uma limitação do próprio jogo, não uma escolha do
 * launcher. Para jogar sem mods, escolha Forge ou NeoForge da versão desejada e deixe
 * o modpack sem nenhum mod: o resultado é o jogo vanilla.
 *
 * Nada é digitado na mão: as versões de Minecraft vêm do manifesto oficial da Mojang e
 * os builds vêm dos servidores oficiais de cada loader.
 */
public class CatalogoVersoes {

	public static final String FORGE = "Forge";
	public static final String FABRIC = "Fabric";
	public static final String NEOFORGE = "NeoForge";

	public static final String[] LOADERS = {FORGE, FABRIC, NEOFORGE};

	/** Versão mais antiga que o catálogo mostra. */
	public static final String VERSAO_MINIMA = "1.8.9";

	/** Versão mais nova que o catálogo mostra. */
	public static final String VERSAO_MAXIMA = "1.21.1";

	/**
	 * O exemplo oficial do Fabric só existe da 1.14.4 para cima. Abaixo disso o Fabric
	 * até tem loader, mas nada dele para baixar — por isso a lista começa aqui.
	 */
	public static final String FABRIC_MINIMO = "1.14.4";

	private static final int TIMEOUT_MS = 20000;
	private static final String MANIFESTO_MOJANG =
			"https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

	/** Lista oficial das versões do jogo, da mais nova para a mais antiga. É baixada uma vez só. */
	private static List<String> versoesOficiais;

	public static List<ItemVersao> listarVersoes(String loader) throws Exception {
		switch (loader) {
			case FORGE:
				return listarForge();
			case FABRIC:
				return listarFabric();
			case NEOFORGE:
				return listarNeoForge();
			default:
				throw new IllegalArgumentException("Loader desconhecido: " + loader);
		}
	}

	/** Diz se o loader costuma existir para aquela versão do jogo. Serve para avisar o usuário. */
	public static String avisoDoLoader(String loader, String versaoMc) {
		if (versaoMc == null) {
			return null;
		}
		if (NEOFORGE.equals(loader) && comparar(versaoMc, "1.20") < 0) {
			return "O NeoForge só existe a partir da 1.20. Para versões mais antigas, use Forge.";
		}
		if (FABRIC.equals(loader) && comparar(versaoMc, FABRIC_MINIMO) < 0) {
			return "O Fabric só tem kit para " + FABRIC_MINIMO + " ou mais novo. "
					+ "Para " + versaoMc + ", use Forge.";
		}
		return null;
	}

	/** Compara duas versões do tipo "1.8.9". Devolve negativo se a primeira for mais antiga. */
	public static int comparar(String a, String b) {
		String[] partesA = a.split("[.-]");
		String[] partesB = b.split("[.-]");
		int tamanho = Math.max(partesA.length, partesB.length);
		for (int i = 0; i < tamanho; i++) {
			int valorA = i < partesA.length ? numero(partesA[i]) : 0;
			int valorB = i < partesB.length ? numero(partesB[i]) : 0;
			if (valorA != valorB) {
				return Integer.compare(valorA, valorB);
			}
		}
		return 0;
	}

	private static int numero(String texto) {
		try {
			return Integer.parseInt(texto);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ------------------------------------------------------------------
	// Versões oficiais do jogo (fonte: Mojang)
	// ------------------------------------------------------------------

	/**
	 * Baixa da Mojang a lista de lançamentos oficiais e guarda em memória.
	 * Só lançamentos: snapshots, pré-releases e betas ficam de fora.
	 */
	private static synchronized List<String> obterVersoesOficiais() throws Exception {
		if (versoesOficiais != null) {
			return versoesOficiais;
		}

		List<String> encontradas = new ArrayList<>();
		String json = baixarTexto(MANIFESTO_MOJANG);
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"[^{}]*?\"type\"\\s*:\\s*\"release\"[^{}]*?\\}")
				.matcher(json);

		while (matcher.find()) {
			String versao = matcher.group(1);
			if (comparar(versao, VERSAO_MINIMA) >= 0 && comparar(versao, VERSAO_MAXIMA) <= 0) {
				encontradas.add(versao);
			}
		}

		if (encontradas.isEmpty()) {
			// Sem internet ou formato diferente: usa a lista de reserva para nunca travar.
			encontradas.addAll(List.of(
					"1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.18.1",
					"1.17.1", "1.16.5", "1.16.4", "1.15.2", "1.14.4", "1.13.2", "1.12.2", "1.11.2",
					"1.10.2", "1.9.4", "1.8.9"));
		}

		versoesOficiais = encontradas;
		return versoesOficiais;
	}

	// ------------------------------------------------------------------
	// Forge
	// ------------------------------------------------------------------

	/**
	 * O Forge publica centenas de builds. Aqui fica só o build mais novo de cada versão
	 * do jogo — aquele que a própria comunidade usa por padrão.
	 */
	private static List<ItemVersao> listarForge() throws Exception {
		List<String> oficiais = obterVersoesOficiais();
		String xml = baixarTexto("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");

		// Guarda o melhor build já visto para cada versão do jogo.
		java.util.Map<String, String> melhorBuild = new java.util.LinkedHashMap<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("<version>([^<]+)</version>").matcher(xml);

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

		List<ItemVersao> itens = new ArrayList<>();
		for (java.util.Map.Entry<String, String> item : melhorBuild.entrySet()) {
			String codigo = item.getKey() + "-" + item.getValue();
			itens.add(new ItemVersao(codigo, "Forge " + item.getKey() + "  ·  build " + item.getValue(),
					item.getKey()));
		}
		ordenarPorMc(itens);
		return itens;
	}

	// ------------------------------------------------------------------
	// Fabric
	// ------------------------------------------------------------------

	/** O Fabric usa a própria versão do jogo como versão do loader. Uma linha por versão. */
	private static List<ItemVersao> listarFabric() throws Exception {
		List<String> oficiais = obterVersoesOficiais();
		String json = baixarTexto("https://meta.fabricmc.net/v2/versions/game");
		List<ItemVersao> itens = new ArrayList<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\\{[^{}]*\"version\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}").matcher(json);

		while (matcher.find()) {
			String versao = matcher.group(1);
			// Só até a 1.14.4: abaixo disso o Fabric não publica exemplo para baixar,
			// então não adianta mostrar na lista.
			if (comparar(versao, FABRIC_MINIMO) < 0 || comparar(versao, VERSAO_MAXIMA) > 0) {
				continue;
			}
			if (oficiais.contains(versao)) {
				itens.add(new ItemVersao(versao, "Fabric " + versao, versao));
			}
		}
		ordenarPorMc(itens);
		return itens;
	}

	// ------------------------------------------------------------------
	// NeoForge
	// ------------------------------------------------------------------

	/** Mesma regra: só o build mais novo de cada versão, em vez da lista inteira. */
	private static List<ItemVersao> listarNeoForge() throws Exception {
		String json = baixarTexto(
				"https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");
		java.util.Map<String, String> melhorBuild = new java.util.LinkedHashMap<>();
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("\"([0-9][0-9.]*)\"").matcher(json);

		while (matcher.find()) {
			String bruto = matcher.group(1);
			String mc = converterVersaoNeoForge(bruto);
			if (mc == null || comparar(mc, VERSAO_MAXIMA) > 0) {
				continue;
			}
			String jaTem = melhorBuild.get(mc);
			if (jaTem == null || compararBuild(bruto, jaTem) > 0) {
				melhorBuild.put(mc, bruto);
			}
		}

		List<ItemVersao> itens = new ArrayList<>();
		for (java.util.Map.Entry<String, String> item : melhorBuild.entrySet()) {
			itens.add(new ItemVersao(item.getValue(),
					"NeoForge " + item.getKey() + "  ·  build " + item.getValue(), item.getKey()));
		}
		ordenarPorMc(itens);
		return itens;
	}

	/**
	 * O NeoForge usa "21.1.72" para o MC 1.21.1 e "20.4.237" para o 1.20.4:
	 * o primeiro e o segundo número formam a versão do Minecraft.
	 */
	private static String converterVersaoNeoForge(String versao) {
		String[] partes = versao.split("\\.");
		if (partes.length < 2) {
			return null;
		}
		try {
			int primeiro = Integer.parseInt(partes[0]);
			int segundo = Integer.parseInt(partes[1]);
			return "1." + primeiro + "." + segundo;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------
	// Ordenação e download
	// ------------------------------------------------------------------

	/** Da versão mais nova do jogo para a mais antiga. */
	private static void ordenarPorMc(List<ItemVersao> itens) {
		itens.sort((a, b) -> comparar(b.versaoMc, a.versaoMc));
	}

	/** Compara os números do build do loader (ex: 47.4.0 é mais novo que 47.2.0). */
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
				return Integer.compare(numero(partesA[i]), numero(partesB[i]));
			}
			return partesA[i].compareTo(partesB[i]);
		}
		return Integer.compare(partesA.length, partesB.length);
	}

	private static String baixarTexto(String endereco) throws Exception {
		HttpURLConnection conexao = abrir(endereco);
		try (InputStream entrada = conexao.getInputStream();
				BufferedReader leitor = new BufferedReader(
						new InputStreamReader(entrada, StandardCharsets.UTF_8))) {
			StringBuilder texto = new StringBuilder();
			String linha;
			while ((linha = leitor.readLine()) != null) {
				texto.append(linha).append('\n');
			}
			return texto.toString();
		} finally {
			conexao.disconnect();
		}
	}

	public static HttpURLConnection abrir(String endereco) throws Exception {
		URL url = new URI(endereco).toURL();
		HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
		conexao.setConnectTimeout(TIMEOUT_MS);
		conexao.setReadTimeout(TIMEOUT_MS);
		conexao.setRequestProperty("User-Agent", "BCraftOS-Launcher");
		conexao.setInstanceFollowRedirects(true);
		return conexao;
	}

	/** Uma versão do catálogo: o código que identifica ela e o texto que aparece na lista. */
	public static class ItemVersao {
		public final String codigo;
		public final String rotulo;
		public final String versaoMc;

		public ItemVersao(String codigo, String rotulo, String versaoMc) {
			this.codigo = codigo;
			this.rotulo = rotulo;
			this.versaoMc = versaoMc;
		}

		@Override
		public String toString() {
			return rotulo;
		}
	}
}
