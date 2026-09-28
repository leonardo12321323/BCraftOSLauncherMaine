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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fala com a internet para descobrir quais versões de cada loader existem.
 * Nada é digitado na mão: tudo vem dos servidores oficiais (Mojang, Fabric, Forge, NeoForge).
 *
 * O catálogo cobre desde a 1.8.9. Vale lembrar que nem todo loader existe em toda versão:
 * o 1.8.9, por exemplo, só tem Forge — NeoForge nasceu no 1.20 e Fabric no 1.14.
 */
public class CatalogoVersoes {

	public static final String VANILLA = "Vanilla";
	public static final String FORGE = "Forge";
	public static final String FABRIC = "Fabric";
	public static final String NEOFORGE = "NeoForge";

	public static final String[] LOADERS = {VANILLA, FORGE, FABRIC, NEOFORGE};

	/** Versão mais antiga que o catálogo mostra. */
	public static final String VERSAO_MINIMA = "1.8.9";

	private static final int TIMEOUT_MS = 20000;

	/**
	 * Versões de Minecraft "jogáveis", da mais nova para a mais antiga.
	 * Só lançamentos oficiais: sem snapshots, sem betas, sem pré-releases.
	 */
	private static final List<String> VERSAO_OFICIAIS = List.of(
			"1.21.11", "1.21.10", "1.21.9", "1.21.8", "1.21.7", "1.21.6",
			"1.21.5", "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
			"1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
			"1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
			"1.18.2", "1.18.1", "1.18", "1.17.1", "1.17",
			"1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
			"1.15.2", "1.15.1", "1.15",
			"1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
			"1.13.2", "1.13.1", "1.13",
			"1.12.2", "1.12.1", "1.12",
			"1.11.2", "1.11.1", "1.11",
			"1.10.2", "1.10.1", "1.10",
			"1.9.4", "1.9.3", "1.9.2", "1.9.1", "1.9",
			"1.8.9", "1.8.8", "1.8.7");

	public static List<ItemVersao> listarVersoes(String loader) throws Exception {
		switch (loader) {
			case VANILLA:
				return listarVanilla();
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
		if (FABRIC.equals(loader) && comparar(versaoMc, "1.14") < 0) {
			return "O Fabric só existe a partir da 1.14. Para versões mais antigas, use Forge.";
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

	private static List<ItemVersao> listarVanilla() {
		List<ItemVersao> itens = new ArrayList<>();
		for (String versao : VERSAO_OFICIAIS) {
			itens.add(new ItemVersao(versao, "Vanilla " + versao, versao));
		}
		return itens;
	}

	private static List<ItemVersao> listarNeoForge() throws Exception {
		String json = baixarTexto("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");
		List<ItemVersao> itens = new ArrayList<>();
		Matcher matcher = Pattern.compile("\"([0-9][0-9.]*)\"").matcher(json);

		while (matcher.find()) {
			String bruto = matcher.group(1);
			String mc = converterVersaoNeoForge(bruto);
			if (mc != null) {
				itens.add(new ItemVersao(bruto, "NeoForge " + bruto + "  ·  MC " + mc, mc));
			}
		}
		ordenar(itens);
		return itens;
	}

	private static List<ItemVersao> listarFabric() throws Exception {
		String json = baixarTexto("https://meta.fabricmc.net/v2/versions/game");
		List<ItemVersao> itens = new ArrayList<>();
		Matcher matcher = Pattern
				.compile("\\{[^{}]*\"version\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"stable\"\\s*:\\s*(true|false)[^{}]*\\}")
				.matcher(json);

		while (matcher.find()) {
			String versao = matcher.group(1);
			if (matcher.group(2).equals("true") || VERSAO_OFICIAIS.contains(versao)) {
				itens.add(new ItemVersao(versao, "Fabric " + versao, versao));
			}
		}
		ordenar(itens);
		return itens;
	}

	private static List<ItemVersao> listarForge() throws Exception {
		String xml = baixarTexto("https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml");
		List<ItemVersao> itens = new ArrayList<>();
		Matcher matcher = Pattern.compile("<version>([^<]+)</version>").matcher(xml);

		while (matcher.find()) {
			String completo = matcher.group(1);
			String mc = completo.split("-")[0];
			if (!VERSAO_OFICIAIS.contains(mc)) {
				continue;
			}
			itens.add(new ItemVersao(completo, "Forge " + completo, mc));
		}
		ordenar(itens);
		return itens;
	}

	/**
	 * O NeoForge usa "21.1.72" para o MC 1.21.1 e "20.4.237" para o 1.20.4:
	 * o numero maior e o menor formam a versao do Minecraft.
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

	private static void ordenar(List<ItemVersao> itens) {
		itens.sort((a, b) -> {
			int porMc = Integer.compare(posicaoNaLista(a.versaoMc), posicaoNaLista(b.versaoMc));
			if (porMc != 0) {
				return porMc;
			}
			// Dentro da mesma versão do jogo, o build mais novo aparece primeiro.
			return compararBuild(b.codigo, a.codigo);
		});
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

	private static int posicaoNaLista(String versaoMc) {
		int indice = VERSAO_OFICIAIS.indexOf(versaoMc);
		return indice < 0 ? VERSAO_OFICIAIS.size() : indice;
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
