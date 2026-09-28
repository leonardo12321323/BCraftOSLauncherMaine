package BCraftOSproject1.BCraftOS1;



import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Organiza as pastas do BCraftOS sozinho.
 *
 * Você NÃO precisa criar nada na mão. Este arquivo monta a estrutura inteira
 * na primeira execução e conserta qualquer coisa fora do lugar:
 *
 *   BCraftOS/                     <- pasta onde o launcher roda
 *   ├── versoes/
 *   │   ├── Forge/
 *   │   ├── Fabric/
 *   │   └── NeoForge/
 *   ├── usuarios/
 *   ├── cache-normal/            <- criada só quando você usa o Modo normal
 *   ├── ATUALIZE-OS-ARQUIVOS.txt
 *   └── COMO-USAR.txt
 *
 * Dentro de cada versão (criada no momento do download) ficam:
 *   versoes/Loader/codigoDaVersao/MDK-codigoDaVersao-Loader/   <- o projeto do Gradle
 *   versoes/Loader/codigoDaVersao/modpacks/                    <- seus mods (.jar)
 *
 * Também renomeia os arquivos .java que vieram com "[1]", "(1)" ou " - Copia"
 * no nome, que é a causa mais comum de erro de compilação depois de baixar.
 */
public class OrganizadorPastas {

	/** Pastas que o launcher precisa ter. Se não existirem, ele cria. */
	private static final String[] PASTAS_OBRIGATORIAS = {
			"versoes",
			"versoes/Forge",
			"versoes/Fabric",
			"versoes/NeoForge",
			"usuarios"
	};

	/** " - Copia" é o sufixo que o Windows usa ao duplicar arquivo. */
	private static final String SUFIXO_COPIA = " - Copia";

	/** Monta a estrutura de pastas. Pode chamar quantas vezes quiser: não apaga nada. */
	public static void organizar() {
		File raiz = new File(System.getProperty("user.dir"));

		for (String caminho : PASTAS_OBRIGATORIAS) {
			File pasta = new File(raiz, caminho);
			if (!pasta.isDirectory() && pasta.mkdirs()) {
				System.out.println("[BCraftOS] Pasta criada: " + caminho + "/");
			}
		}

		removerPastaVanillaAntiga(raiz);
		renomearArquivosDuplicados(raiz);
		renomearPastaDeContasAntiga(raiz);
		criarLeiaMe(raiz);
	}

	/**
	 * A opção Vanilla saiu do launcher porque o Minecraft puro não tem kit para baixar.
	 * Se a pasta vazia ficou no disco, ela é removida — só se estiver vazia, nunca com conteúdo.
	 */
	private static void removerPastaVanillaAntiga(File raiz) {
		File vanilla = new File(raiz, "versoes/Vanilla");
		if (!vanilla.isDirectory()) {
			return;
		}
		File[] conteudo = vanilla.listFiles();
		if (conteudo != null && conteudo.length > 0) {
			System.out.println("[BCraftOS] A pasta versoes/Vanilla tem conteúdo e foi mantida. "
					+ "O Vanilla saiu do launcher: para jogar sem mods use Forge ou NeoForge sem mod nenhum.");
			return;
		}
		if (vanilla.delete()) {
			System.out.println("[BCraftOS] Pasta vazia versoes/Vanilla removida (o Vanilla saiu do catálogo).");
		}
	}

	/**
	 * Renomeia apenas os arquivos com sufixo de duplicata.
	 * A regra é estrita de propósito: só o padrão que o Windows e os navegadores usam,
	 * para nunca tocar num arquivo que tenha nome legítimo parecido.
	 */
	private static void renomearArquivosDuplicados(File raiz) {
		File[] pastasDeCodigo = {raiz, new File(raiz, "src")};
		for (File pasta : pastasDeCodigo) {
			if (!pasta.isDirectory()) {
				continue;
			}
			File[] arquivos = pasta.listFiles(File::isFile);
			if (arquivos == null) {
				continue;
			}
			for (File arquivo : arquivos) {
				String nome = arquivo.getName();
				if (!nome.endsWith(".java")) {
					continue;
				}
				String limpo = limparNome(nome);
				if (limpo == null) {
					continue;
				}
				File destino = new File(arquivo.getParentFile(), limpo);
				if (destino.exists()) {
					continue; // já existe o certo: não mexe em nada
				}
				try {
					Files.move(arquivo.toPath(), destino.toPath());
					System.out.println("[BCraftOS] Arquivo renomeado: " + nome + " -> " + limpo);
				} catch (IOException e) {
					System.err.println("[BCraftOS Aviso] Não consegui renomear " + nome
							+ " (renomeie na mão para " + limpo + ")");
				}
			}
		}
	}

	/** "BCraftOS1(1).java" vira "BCraftOS1.java". Devolve null quando não há o que limpar. */
	private static String limparNome(String nome) {
		String base = nome.substring(0, nome.length() - ".java".length());
		String limpa = base;

		if (limpa.endsWith(SUFIXO_COPIA)) {
			limpa = limpa.substring(0, limpa.length() - SUFIXO_COPIA.length());
		}
		while (limpa.endsWith(")")) {
			int abre = limpa.lastIndexOf('(');
			if (abre < 0 || abre == 0) {
				break;
			}
			String dentro = limpa.substring(abre + 1, limpa.length() - 1).trim();
			boolean ehNumero = !dentro.isEmpty() && dentro.chars().allMatch(Character::isDigit);
			if (!ehNumero && !dentro.equalsIgnoreCase("copia")
					&& !dentro.equalsIgnoreCase("copy")) {
				break;
			}
			limpa = limpa.substring(0, abre).trim();
		}

		if (limpa.equals(base) || limpa.isEmpty()) {
			return null;
		}
		return limpa + ".java";
	}

	/** Se as contas antigas ficaram em src/BCraftOSproject1/Info_Usuarios, traz para usuarios/. */
	private static void renomearPastaDeContasAntiga(File raiz) {
		File antiga = new File(raiz, "src/BCraftOSproject1/Info_Usuarios");
		File nova = new File(raiz, "usuarios");
		if (!antiga.isDirectory() || nova.exists()) {
			return;
		}
		try {
			Files.move(antiga.toPath(), nova.toPath());
			System.out.println("[BCraftOS] Contas movidas de src/BCraftOSproject1/Info_Usuarios para usuarios/");
		} catch (IOException e) {
			System.err.println("[BCraftOS Aviso] Mova na mão a pasta src/BCraftOSproject1/Info_Usuarios "
					+ "para a raiz com o nome usuarios");
		}
	}

	/** Deixa no projeto um texto explicando a estrutura, para você não precisar decorar. */
	private static void criarLeiaMe(File raiz) {
		String texto = String.join(System.lineSeparator(),
				"BCraftOS - ESTRUTURA DE PASTAS",
				"",
				"Você não precisa criar pasta nenhuma na mão. O launcher cria tudo sozinho",
				"quando abre. Só coloque os .java em src/BCraftOSproject1/ e rode.",
				"",
				"COMO FICA:",
				"",
				"BCraftOS/                          (pasta onde o launcher roda)",
				"  src/BCraftOSproject1/",
				"    BCraftOS1/         -> BCraftOS1.java, CatalogoVersoes.java, GerenciadorDownloads.java,",
				"                          GerenciadorVersoes.java, GerenciadorModpacks.java,",
				"                          MinecraftLauncher.java, OrganizadorPastas.java,",
				"                          ModoNormal.java, MiniJson.java",
				"    BCraftOS1login/    -> BCraftOS1login.java, InfoUsuarios.java",
				"  versoes/                         (criada sozinha, com uma pasta por loader)",
				"    Forge/",
				"    Fabric/",
				"    NeoForge/",
				"  usuarios/                        (criada sozinha; uma pasta por conta)",
				"  cache-normal/                    (criada no Modo normal; Minecraft e Forge compartilhados)",
				"",
				"DEPOIS DE BAIXAR UMA VERSÃO NO LAUNCHER, ela aparece assim:",
				"",
				"  versoes/",
				"    Forge/",
				"      1.8.9-11.15.1.2318-1.8.9/",
				"        MDK-1.8.9-11.15.1.2318-1.8.9-Forge/   (projeto do Gradle, com o gradlew)",
				"        modpacks/                              (coloque seus .jar aqui)",
				"          Sem-Mods/",
				"    NeoForge/",
				"      21.1.72/",
				"        MDK-21.1.72-NeoForge/",
				"        modpacks/",
				"",
				"VERSOES POR CONTA:",
				"  A conta Bw1_1Bw usa versoes/<Loader>/<versao>/ (como sempre).",
				"  As outras contas usam versoes/contas/<Conta>/<Loader>/<versao>/.",
				"  Cada conta so ve e joga as versoes que ela mesma baixou.",
				"",
				"ONDE COLOCAR SEUS MODS:",
				"  versoes/<Loader>/<versão>/modpacks/<nome do modpack>/*.jar",
				"  O launcher copia esses .jar para o jogo na hora de iniciar.",
				"",
				"NÃO APAGUE:",
				"  - a pasta usuarios (é onde as contas ficam)",
				"  - a pasta versoes (é o jogo já baixado)"
		);
		try {
			File arquivo = new File(raiz, "COMO-USAR.txt");
			if (!arquivo.exists()) {
				Files.writeString(arquivo.toPath(), texto, StandardCharsets.UTF_8);
				System.out.println("[BCraftOS] Guia de pastas criado: COMO-USAR.txt");
			}
		} catch (IOException ignorado) {
			// Se não conseguir escrever o texto, nada quebra: é só um guia.
		}
	}

	/** Mostra no console onde está a raiz do projeto, para facilitar achar as pastas. */
	public static void mostrarResumo() {
		File raiz = new File(System.getProperty("user.dir"));
		File pastasVersoes = GerenciadorVersoes.obterPastaVersoesDaConta();
		System.out.println("[BCraftOS] Pasta do launcher: " + raiz.getAbsolutePath());
		System.out.println("[BCraftOS] Versões instaladas em: " + pastasVersoes.getAbsolutePath());
		GerenciadorVersoes.configurarDiretoriosIniciais();
		String[] instaladas = GerenciadorVersoes.listarVersoesInstaladas();
		if (instaladas.length == 0) {
			System.out.println("[BCraftOS] Nenhuma versão instalada ainda: escolha uma no launcher e clique em baixar.");
		} else {
			System.out.println("[BCraftOS] " + instaladas.length + " versão(ões) já instalada(s): "
					+ String.join(", ", instaladas));
		}
	}

	/** Também aceita ser executado direto, só para arrumar as pastas. */
	public static void main(String[] args) {
		organizar();
		mostrarResumo();
	}
}
