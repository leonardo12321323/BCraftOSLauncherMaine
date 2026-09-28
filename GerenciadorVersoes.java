package BCraftOSproject1.BCraftOS1;



import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Cuida da pasta "versoes", organizada por catálogo de loader:
 *
 *   versoes/Forge/1.20.1-47.4.0/...
 *   versoes/Fabric/1.21.1/...
 *   versoes/NeoForge/21.1.72/...
 *
 * Dentro da pasta de cada versao ficam o MDK daquela versao e a pasta "modpacks".
 * A pasta antiga (versoes/nome-solto) continua sendo lida, para nao perder nada do que ja existe.
 *
 * Cada versão pertence à conta que a baixou:
 *
 *   conta protegida (Bw1_1Bw) -> versoes/Loader/codigo         (o mesmo lugar de sempre)
 *   qualquer outra conta      -> versoes/contas/NomeDaConta/Loader/codigo
 *
 * Assim uma conta nunca vê, joga nem mexe nas versões e modpacks de outra.
 */
public class GerenciadorVersoes {

	private static final String NOME_PASTA_VERSOES = "versoes";
	/** Pasta que guarda as versões de cada conta comum: versoes/contas/NomeDaConta/... */
	private static final String NOME_PASTA_CONTAS = "contas";
	/** Conta que está logada agora; define de quem são as versões que o launcher enxerga. */
	private static String contaAtiva = null;
	private static final String VERSAO_PADRAO = "1.21.1-NeoForge";
	private static final String NOME_MDK_ANTIGO = "MDK-1.21.1-ModDevGradle-main";
	private static final String NOME_MODPACKS_ANTIGO = "modpacks";

	public static File obterPastaVersoes() {
		return new File(System.getProperty("user.dir"), NOME_PASTA_VERSOES);
	}

	/** Chamado no login: a partir daqui, tudo que for baixado ou listado é dessa conta. */
	public static void definirContaAtiva(String nomeConta) {
		contaAtiva = nomeConta == null ? null : nomeConta.trim();
	}

	/**
	 * Pasta onde ficam as versões da conta logada.
	 * A conta protegida (e o launcher sem ninguém logado) usa a pasta versoes/ de sempre,
	 * então o que você já tinha baixado continua no lugar.
	 */
	public static File obterPastaVersoesDaConta() {
		File base = obterPastaVersoes();
		if (contaAtiva == null || contaAtiva.isEmpty()
				|| BCraftOSproject1.BCraftOS1login.InfoUsuarios.contaProtegida(contaAtiva)) {
			return base;
		}
		// Só caracteres seguros no nome da pasta, para o nome nunca apontar para fora dela.
		String nomeSeguro = contaAtiva.replaceAll("[^a-zA-Z0-9 _-]", "_");
		return new File(new File(base, NOME_PASTA_CONTAS), nomeSeguro);
	}

	public static void configurarDiretoriosIniciais() {
		File pastaVersoes = obterPastaVersoes();
		if (pastaVersoes.exists()) {
			return;
		}
		pastaVersoes.mkdirs();

		File raiz = new File(System.getProperty("user.dir"));
		File mdkAntigo = new File(raiz, NOME_MDK_ANTIGO);
		File modpacksAntigo = new File(raiz, NOME_MODPACKS_ANTIGO);

		if (mdkAntigo.exists()) {
			File pastaVersaoPadrao = new File(pastaVersoes, VERSAO_PADRAO);
			pastaVersaoPadrao.mkdirs();
			try {
				Files.move(mdkAntigo.toPath(), new File(pastaVersaoPadrao, NOME_MDK_ANTIGO).toPath());
				System.out.println("[BCraftOS] Estrutura antiga migrada: " + NOME_MDK_ANTIGO + " -> versoes/" + VERSAO_PADRAO);
				if (modpacksAntigo.exists()) {
					Files.move(modpacksAntigo.toPath(), new File(pastaVersaoPadrao, NOME_MODPACKS_ANTIGO).toPath());
				}
			} catch (IOException e) {
				System.err.println("[BCraftOS Erro] Falha ao migrar a estrutura antiga automaticamente: " + e.getMessage());
				System.err.println("[BCraftOS] Mova manualmente " + NOME_MDK_ANTIGO + " e " + NOME_MODPACKS_ANTIGO
						+ " para dentro de versoes/" + VERSAO_PADRAO + "/");
			}
		}
	}

	/** Pasta da versão no formato do catálogo: versoes/Loader/codigoDaVersao. */
	public static File obterPastaDaVersao(String loader, String codigoVersao) {
		return new File(new File(obterPastaVersoesDaConta(), loader), codigoVersao);
	}

	/** Nome da pasta que guarda o MDK dentro da pasta da versão. */
	public static String nomePastaMDK(String loader, String codigoVersao) {
		return "MDK-" + codigoVersao + "-" + loader;
	}

	/** Caminho que aparece para o usuário, no formato que já era usado antes. */
	public static String rotuloVersao(String loader, String codigoVersao) {
		return loader + "/" + codigoVersao;
	}

	/**
	 * Acha a pasta do MDK dentro da pasta da versão. Procura pelo arquivo "gradlew",
	 * então funciona com qualquer nome de pasta de MDK.
	 */
	public static File obterPastaMDK(File pastaVersao) {
		if (pastaVersao == null || !pastaVersao.isDirectory()) {
			return null;
		}
		File[] subpastas = pastaVersao.listFiles(File::isDirectory);
		if (subpastas != null) {
			for (File subpasta : subpastas) {
				if (new File(subpasta, "gradlew").exists()) {
					return subpasta;
				}
			}
		}
		return null;
	}

	/** Também aceita a pasta da versão direto, para quem já tinha a estrutura antiga. */
	public static File obterPastaMDK(String nomePastaVersao) {
		File direta = new File(obterPastaVersoes(), nomePastaVersao);
		if (direta.isDirectory()) {
			return obterPastaMDK(direta);
		}
		return null;
	}

	/** Lista as versões que já estão no disco, no formato "Loader/codigo". */
	public static String[] listarVersoesInstaladas() {
		File pastaVersoes = obterPastaVersoesDaConta();
		File[] pastasLoader = pastaVersoes.listFiles(File::isDirectory);
		if (pastasLoader == null) {
			return new String[0];
		}

		String[] loaders = CatalogoVersoes.LOADERS;
		java.util.List<String> encontradas = new java.util.ArrayList<>();

		for (File pastaLoader : pastasLoader) {
			File[] versoes = pastaLoader.listFiles(File::isDirectory);
			if (versoes == null) {
				continue;
			}
			for (File versao : versoes) {
				boolean ehLoaderConhecido = false;
				for (String loader : loaders) {
					if (loader.equalsIgnoreCase(pastaLoader.getName())) {
						ehLoaderConhecido = true;
						break;
					}
				}
				if (ehLoaderConhecido && obterPastaMDK(versao) != null) {
					encontradas.add(rotuloVersao(pastaLoader.getName(), versao.getName()));
				}
			}
		}

		// Estrutura antiga (pasta solta dentro de versoes/) continua aparecendo.
		for (File candidata : pastasLoader) {
			if (!candidata.isDirectory() || obterPastaMDK(candidata) != null) {
				continue;
			}
			if (candidata.getName().equalsIgnoreCase(NOME_PASTA_CONTAS)) {
				continue; // versoes/contas guarda as contas comuns, não é uma versão solta
			}
			boolean ehPastaDeLoader = false;
			for (String loader : loaders) {
				if (loader.equalsIgnoreCase(candidata.getName())) {
					ehPastaDeLoader = true;
					break;
				}
			}
			if (!ehPastaDeLoader && candidata.getName().equalsIgnoreCase("Vanilla")) {
				ehPastaDeLoader = true; // pasta antiga, sem download oficial
			}
			if (ehPastaDeLoader) {
				continue;
			}
			File[] sub = candidata.listFiles(File::isDirectory);
			boolean ehCatalogo = false;
			if (sub != null) {
				for (File s : sub) {
					if (obterPastaMDK(s) != null) {
						ehCatalogo = true;
						break;
					}
				}
			}
			if (!ehCatalogo) {
				encontradas.add(candidata.getName());
			}
		}

		String[] resultado = encontradas.toArray(new String[0]);
		Arrays.sort(resultado);
		return resultado;
	}
}
