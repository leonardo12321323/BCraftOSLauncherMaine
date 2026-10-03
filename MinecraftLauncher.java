package BCraftOSproject1.BCraftOS1;



import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inicializador do cliente, feito para NÃO dar erro.
 *
 * O comando padrão é o do Gradle (gradlew runClient), mas antes de executar o launcher
 * faz uma checagem completa e resolve sozinho os problemas mais comuns:
 *
 *  1. Permissão de execução do gradlew no Linux/Mac -> marca automaticamente.
 *  2. Script certo para o sistema                   -> gradlew.bat no Windows, gradlew no resto.
 *  3. Java na versão correta para a versão do jogo   -> procura o Java certo e diz qual falta.
 *  4. Falta de dependência na primeira execução      -> usa internet na primeira vez e só
 *                                                        liga o --offline depois que deu certo.
 *  5. Diretório de trabalho errado                   -> roda sempre dentro da pasta do MDK.
 *  6. Erro sem explicação                            -> a saída fica visível na janela.
 *
 * Versões antigas (até 1.12.2) usam o Gradle Forge, que funciona de outro jeito:
 * lá a tarefa é "runClient" depois de preparar o ambiente com "setupDevWorkspace".
 */
public class MinecraftLauncher {

	/** Marcador de que o cache do Gradle daquela versão já está pronto. */
	private static final String MARCADOR_CACHE = ".bcraftos-cache-ok";

	/** Init script que injeta o --username na tarefa runClient de qualquer MDK. */
	private static final String NOME_INIT_SCRIPT = "bcraftos-nick.init.gradle";

	/** Propriedade de sistema usada para levar o nick até o init script. */
	private static final String PROPRIEDADE_NICK = "bcraftos.nick";

	/** Versões até aqui usam o Gradle Forge antigo, com setupDevWorkspace. */
	private static final String VERSAO_LIMITE_ANTIGA = "1.12.2";

	/** Onde o Java pode estar, além do que já está no PATH do sistema. */
	private static final String[] PASTAS_JAVA = {
			"C:\\Program Files\\Java",
			"C:\\Program Files\\Eclipse Adoptium",
			"C:\\Program Files\\Microsoft\\jdk",
			"C:\\Program Files\\Zulu",
			"C:\\Program Files\\Amazon Corretto",
			"C:\\Program Files\\BellSoft",
			"/usr/lib/jvm",
			"/usr/java",
			"/opt/java",
			"/Library/Java/JavaVirtualMachines",
			System.getProperty("user.home") + "/.jdks",
			System.getProperty("user.home") + "/.sdkman/candidates/java"
	};

	/** O que foi checado antes de iniciar, para mostrar ao usuário. */
	public static class Diagnostico {
		public boolean podeIniciar = true;
		public final List<String> avisos = new ArrayList<>();
		public final List<String> problemas = new ArrayList<>();
		public File scriptGradle;
		public String javaEncontrado;
		public String javaNecessario;
		public boolean primeiraExecucao;
		/** Verdadeiro quando falta só o Java: o launcher pode baixar sozinho (InstaladorJava). */
		public boolean faltaJava;
		public boolean semInternet;

		public String resumo() {
			StringBuilder texto = new StringBuilder();
			if (!problemas.isEmpty()) {
				texto.append("Não dá para iniciar ainda:\n\n");
				for (String problema : problemas) {
					texto.append("• ").append(problema).append('\n');
				}
			}
			if (!avisos.isEmpty()) {
				if (texto.length() > 0) {
					texto.append('\n');
				}
				texto.append("Antes de começar, saiba que:\n\n");
				for (String aviso : avisos) {
					texto.append("• ").append(aviso).append('\n');
				}
			}
			return texto.toString().trim();
		}
	}

	/** Confere tudo antes de iniciar. Nunca lança exceção: devolve o que encontrou. */
	public static Diagnostico checar(File pastaMDK, String versaoMc) {
		Diagnostico d = new Diagnostico();

		if (pastaMDK == null || !pastaMDK.isDirectory()) {
			d.podeIniciar = false;
			d.problemas.add("A pasta do MDK não existe. Baixe a versão pelo catálogo antes de jogar.");
			return d;
		}

		// 1 e 2 — script certo para o sistema
		boolean windows = ehWindows();
		File script = new File(pastaMDK, windows ? "gradlew.bat" : "gradlew");
		if (!script.isFile()) {
			File alternativa = new File(pastaMDK, windows ? "gradlew" : "gradlew.bat");
			if (alternativa.isFile()) {
				d.avisos.add("Encontrei só o script do outro sistema. Vou usar ele mesmo assim.");
				script = alternativa;
			} else {
				d.podeIniciar = false;
				d.problemas.add("Não achei o gradlew dentro de " + pastaMDK.getName()
						+ ". O download pode ter falhado: baixe a versão de novo.");
				return d;
			}
		}
		d.scriptGradle = script;

		// Script quebrado por fim de linha do Windows (CRLF): no Linux/Mac dá "bad interpreter" ou "not found".
		if (!windows) {
			corrigirFimDeLinha(script);
		}

		// Sem o jar do wrapper o gradlew nem começa: melhor avisar claramente do que deixar dar erro seco.
		File jarWrapper = new File(pastaMDK, "gradle/wrapper/gradle-wrapper.jar");
		File propsWrapper = new File(pastaMDK, "gradle/wrapper/gradle-wrapper.properties");
		if (!jarWrapper.isFile() || !propsWrapper.isFile()) {
			d.podeIniciar = false;
			d.problemas.add("Faltam arquivos do Gradle em " + pastaMDK.getName() + "/gradle/wrapper "
					+ "(gradle-wrapper.jar ou .properties). O download dessa versão ficou incompleto: "
					+ "apague a pasta da versão e baixe de novo.");
			return d;
		}

		// 1 — permissão de execução (só faz sentido fora do Windows)
		if (!windows && !script.canExecute()) {
			boolean conseguiu = script.setExecutable(true, false);
			if (conseguiu) {
				d.avisos.add("O gradlew estava sem permissão de execução e eu liberei automaticamente.");
			} else {
				d.podeIniciar = false;
				d.problemas.add("O gradlew está sem permissão de execução e não consegui liberar. "
						+ "No terminal, rode: chmod +x \"" + script.getAbsolutePath() + "\"");
			}
		}

		// 4 — primeira execução precisa de internet
		d.primeiraExecucao = !new File(pastaMDK, MARCADOR_CACHE).exists();
		if (d.primeiraExecucao) {
			d.avisos.add("Primeira execução desta versão: o Gradle precisa baixar dependências. "
					+ "Pode demorar bastante e exige internet.");
		}
		d.semInternet = !temInternet();
		if (d.semInternet && d.primeiraExecucao) {
			d.podeIniciar = false;
			d.problemas.add("Sem internet. A primeira execução de cada versão precisa baixar o Gradle e as "
					+ "dependências. Conecte e tente de novo (depois disso dá para jogar offline).");
		}
		PerfilMemoria perfil = PerfilMemoria.paraVersao(versaoMc);
		d.avisos.addAll(perfil.avisos());
		long livreMb = pastaMDK.getUsableSpace() / 1024 / 1024;
		if (d.primeiraExecucao && livreMb > 0 && livreMb < 2500) {
			d.avisos.add("Pouco espaço em disco (" + livreMb + " MB livres). A primeira execução usa cerca de "
					+ "2 GB entre Gradle e Minecraft.");
		}

		// 3 — Java na versão certa
		d.javaNecessario = javaNecessarioPara(versaoMc);
		d.javaEncontrado = procurarJava(d.javaNecessario);
		if (d.javaEncontrado == null) {
			d.faltaJava = true;
			d.podeIniciar = false;
			d.problemas.add(mensagemJavaFaltando(d.javaNecessario, versaoMc));
		}

		return d;
	}

	/** Inicia o cliente. Lança IllegalStateException com o motivo real quando não dá. */
	public static void iniciar(File pastaMDK, String versaoMc, String nickOffline, Runnable aoFinalizar) {
		Diagnostico d = checar(pastaMDK, versaoMc);
		if (!d.podeIniciar) {
			throw new IllegalStateException(d.resumo());
		}

		boolean versaoAntiga = CatalogoVersoes.comparar(versaoMc, VERSAO_LIMITE_ANTIGA) <= 0;
		PerfilMemoria perfil = PerfilMemoria.paraVersao(versaoMc);
		boolean offline = d.semInternet && !d.primeiraExecucao;
		List<String> comando = montarComando(d.scriptGradle, versaoAntiga, d.primeiraExecucao, nickOffline,
				perfil, offline);

		Map<String, String> ambiente = new LinkedHashMap<>();
		if (d.javaEncontrado != null) {
			ambiente.put("JAVA_HOME", d.javaEncontrado);
			// O Java certo também na frente do PATH: alguns scripts chamam "java" direto.
			String pathAtual = System.getenv("PATH") == null ? "" : System.getenv("PATH");
			ambiente.put("PATH", new File(d.javaEncontrado, "bin").getAbsolutePath() + File.pathSeparator + pathAtual);
		}
		// Mesma memória no cliente do Gradle e no org.gradle.jvmargs: assim ele roda numa JVM só,
		// em vez de abrir uma segunda JVM (que custaria mais ~300 MB num PC pequeno).
		ambiente.put("GRADLE_OPTS", perfil.opcoesGradle(d.primeiraExecucao));
		System.out.println("[BCraftOS] Perfil de memória: " + perfil);

		System.out.println("[BCraftOS] Iniciando dentro de: " + pastaMDK.getAbsolutePath());
		System.out.println("[BCraftOS] Java: " + d.javaEncontrado + " (versão " + d.javaNecessario + ")");
		System.out.println("[BCraftOS] Comando: " + String.join(" ", comando));

		try {
			criarInitScript(pastaMDK, perfil);
		} catch (IOException e) {
			throw new IllegalStateException("Não consegui criar o script do nick em "
					+ pastaMDK.getName() + ": " + e.getMessage());
		}
		System.out.println("[BCraftOS] Nick usado no jogo: " + nickValido(nickOffline));

		ProcessBuilder pb = new ProcessBuilder(comando);
		pb.directory(pastaMDK); // 5 — sempre dentro da pasta do MDK
		pb.redirectErrorStream(true); // 6 — a saída aparece no console E vai para logs/ (RegistroLogs)
		pb.environment().putAll(ambiente);

		new Thread(() -> {
			int codigoSaida = -1;
			try {
				Process processo = pb.start();
				Thread leitor = RegistroLogs.acompanhar(processo, "minecraft");
				// Sinal de vida: o Gradle pode ficar minutos sem escrever nada. Sem isso não dá para
				// saber se está trabalhando ou travou.
				Thread sinalDeVida = new Thread(() -> {
					long inicio = System.currentTimeMillis();
					try {
						while (processo.isAlive()) {
							Thread.sleep(30000);
							if (processo.isAlive()) {
								System.out.println("[BCraftOS] Ainda trabalhando... "
										+ (System.currentTimeMillis() - inicio) / 60000 + " min e "
										+ (System.currentTimeMillis() - inicio) / 1000 % 60
										+ " s. (A primeira execução de uma versão pode levar vários minutos.)");
							}
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}, "sinal-de-vida");
				sinalDeVida.setDaemon(true);
				sinalDeVida.start();
				codigoSaida = processo.waitFor();
				leitor.join(3000); // deixa a última parte da saída chegar ao log
			} catch (IOException e) {
				System.err.println("[BCraftOS Erro] Não consegui abrir o processo do Gradle: " + e.getMessage());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			if (codigoSaida == 0) {
				marcarCachePronto(pastaMDK);
				System.out.println("[BCraftOS] Minecraft encerrado normalmente.");
			} else {
				System.err.println("[BCraftOS] O Gradle terminou com código " + codigoSaida
						+ ". Veja as mensagens acima para saber o motivo.");
			}

			if (aoFinalizar != null) {
				aoFinalizar.run();
			}
		}).start();
	}

	/**
	 * Monta a linha de comando.
	 * Nas versões antigas, o ambiente precisa ser preparado antes: o Gradle Forge não
	 * cria a tarefa runClient sozinho.
	 */
	private static List<String> montarComando(File script, boolean versaoAntiga,
			boolean primeiraExecucao, String nickOffline, PerfilMemoria perfil, boolean offline) {
		List<String> comando = new ArrayList<>();
		if (ehWindows()) {
			comando.add("cmd");
			comando.add("/c");
			comando.add(script.getName());
		} else {
			comando.add("./" + script.getName());
		}

		if (versaoAntiga) {
			// Prepara o ambiente uma vez. Depois disso o runClient já existe.
			if (primeiraExecucao) {
				comando.add("setupDevWorkspace");
			}
			comando.add("runClient");
		} else {
			comando.add("runClient");
		}
		if (offline) {
			// Só vale offline quando de fato não há internet e o cache já está pronto.
			comando.add("--offline");
		}

		// Pouca memória: sem daemon (ele ficaria horas ocupando RAM depois do jogo),
		// poucas tarefas ao mesmo tempo e a JVM do Gradle com teto de memória.
		comando.add("--no-daemon");
		comando.add("--max-workers=" + perfil.trabalhadores);
		comando.add("-Dorg.gradle.jvmargs=" + perfil.opcoesGradle(primeiraExecucao));
		comando.add("-Dorg.gradle.parallel=false");

		// O nick chega ao Minecraft por um init script do Gradle (gerado em criarInitScript),
		// porque o "-P" sozinho não faz nada se o build.gradle do MDK não ler a propriedade.
		comando.add("--init-script");
		comando.add(NOME_INIT_SCRIPT);
		comando.add("-D" + PROPRIEDADE_NICK + "=" + nickValido(nickOffline));
		comando.add("--console=plain");
		comando.add("--stacktrace");
		return comando;
	}

	/**
	 * Escreve o init script que adiciona "--username <nick>" aos argumentos do runClient.
	 * Sem isso o jogo abre com o nick de desenvolvimento (Player123) e uma sessão falsa,
	 * e servidores com proteção de nick respondem "Invalid session".
	 */
	private static void criarInitScript(File pastaMDK, PerfilMemoria perfil) throws IOException {
		StringBuilder flags = new StringBuilder();
		for (String flag : perfil.flagsJogo) {
			flags.append("'").append(flag).append("', ");
		}
		String conteudo = ""
				+ "gradle.projectsEvaluated {\n"
				+ "  rootProject.allprojects { p ->\n"
				+ "    p.tasks.matching { it.name == 'runClient' }.all { t ->\n"
				+ "      def nick = System.getProperty('" + PROPRIEDADE_NICK + "')\n"
				+ "      if (nick != null && t instanceof JavaExec) {\n"
				+ "        t.args('--username', nick)\n"
				+ "      }\n"
				+ "      if (t instanceof JavaExec) {\n"
				+ "        // Memória do jogo escolhida pela RAM do computador (feito na hora de rodar,\n"
				+ "        // para valer mesmo que o build.gradle do MDK defina outro valor).\n"
				+ "        t.doFirst {\n"
				+ "          t.minHeapSize = '" + perfil.jogoMinMb + "m'\n"
				+ "          t.maxHeapSize = '" + perfil.jogoMaxMb + "m'\n"
				+ "          t.jvmArgs([" + flags + "])\n"
				+ "        }\n"
				+ "      }\n"
				+ "    }\n"
				+ "  }\n"
				+ "}\n";
		Files.writeString(new File(pastaMDK, NOME_INIT_SCRIPT).toPath(), conteudo);
	}

	/**
	 * Scripts .sh salvos com fim de linha do Windows (CRLF) quebram no Linux/Mac com erros como
	 * "bad interpreter: No such file or directory". Aqui o gradlew é consertado antes de rodar.
	 */
	private static void corrigirFimDeLinha(File script) {
		try {
			byte[] bytes = Files.readAllBytes(script.toPath());
			boolean temCr = false;
			for (byte b : bytes) {
				if (b == '\r') {
					temCr = true;
					break;
				}
			}
			if (!temCr) {
				return;
			}
			String texto = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
			Files.write(script.toPath(), texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			script.setExecutable(true, false);
			System.out.println("[BCraftOS] gradlew estava com fim de linha do Windows (CRLF) e foi corrigido.");
		} catch (IOException e) {
			System.err.println("[BCraftOS Aviso] Não consegui checar o fim de linha do gradlew: " + e.getMessage());
		}
	}

	/** Testa rápido se há internet (os servidores que o Gradle usa). Nunca demora mais que uns segundos. */
	static boolean temInternet() {
		String[] hospedes = {"services.gradle.org", "repo.maven.apache.org", "launchermeta.mojang.com"};
		for (String hospede : hospedes) {
			try (Socket socket = new Socket()) {
				socket.connect(new InetSocketAddress(hospede, 443), 2500);
				return true;
			} catch (IOException ignorado) {
				// tenta o próximo
			}
		}
		return false;
	}

	/**
	 * Deixa o nick no formato aceito pelo Minecraft: só letras, números e "_",
	 * de 3 a 16 caracteres. Contas antigas com espaço ou "-" continuam entrando no launcher.
	 */
	static String nickValido(String nick) {
		String limpo = nick == null ? "" : nick.trim().replaceAll("[^a-zA-Z0-9_]", "_");
		if (limpo.length() > 16) {
			limpo = limpo.substring(0, 16);
		}
		while (limpo.length() < 3) {
			limpo += "_";
		}
		return limpo;
	}

	private static void marcarCachePronto(File pastaMDK) {
		try {
			Files.writeString(new File(pastaMDK, MARCADOR_CACHE).toPath(), "ok");
		} catch (IOException ignorado) {
			// Se não conseguir marcar, o próximo boot só baixa de novo: nada quebra.
		}
	}

	private static boolean ehWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	// ------------------------------------------------------------------
	// Java
	// ------------------------------------------------------------------

	/** Cada faixa de Minecraft precisa de uma versão diferente do Java. */
	public static String javaNecessarioPara(String versaoMc) {
		if (versaoMc == null) {
			return "21";
		}
		// A partir da 26.1 o Minecraft exige o Java 25.
		if (CatalogoVersoes.comparar(versaoMc, "26.1") >= 0) {
			return "25";
		}
		if (CatalogoVersoes.comparar(versaoMc, "1.20.5") >= 0) {
			return "21";
		}
		if (CatalogoVersoes.comparar(versaoMc, "1.17") >= 0) {
			return "17";
		}
		return "8";
	}

	/**
	 * Acha o Java certo e, se não houver, BAIXA sozinho (Temurin, na pasta java/ do launcher).
	 * Pode demorar (uns 200 MB): chame fora da thread da janela.
	 */
	public static String garantirJava(String versaoMc, GerenciadorDownloads.Progresso progresso) throws Exception {
		String necessario = javaNecessarioPara(versaoMc);
		String existente = procurarJava(necessario);
		if (existente != null) {
			return existente;
		}
		return InstaladorJava.garantir(necessario, progresso);
	}

	private static String mensagemJavaFaltando(String necessario, String versaoMc) {
		return "Não encontrei o Java " + necessario + ", exigido pela versão " + versaoMc
				+ " do Minecraft, e não consegui baixar sozinho. Confira a internet e tente de novo "
				+ "(o launcher baixa o Java 8, 17, 21 ou 25 automaticamente). "
				+ "Se continuar, instale o Java " + necessario + " (Temurin/Adoptium) manualmente.";
	}

	/**
	 * Procura uma instalação do Java na versão pedida no JAVA_HOME, nas pastas
	 * de instalação conhecidas e no PATH. Devolve a pasta da instalação, ou null.
	 */
	public static String procurarJava(String versaoNecessaria) {
		// 1. O JDK que o próprio launcher baixou (pasta java/ do launcher): é o mais previsível.
		String baixado = InstaladorJava.casaInstalada(versaoNecessaria);
		if (baixado != null && versaoNecessaria.equals(versaoDe(executavelJava(new File(baixado))))) {
			return baixado;
		}

		String javaHome = System.getenv("JAVA_HOME");
		if (javaHome != null && versaoNecessaria.equals(versaoDe(executavelJava(new File(javaHome))))) {
			return javaHome;
		}

		for (String pasta : PASTAS_JAVA) {
			File[] candidatos = new File(pasta).listFiles(File::isDirectory);
			if (candidatos == null) {
				continue;
			}
			for (File candidato : candidatos) {
				if (versaoNecessaria.equals(versaoDe(executavelJava(candidato)))) {
					return candidato.getAbsolutePath();
				}
			}
		}

		String noPath = acharNoPath();
		if (noPath != null && versaoNecessaria.equals(versaoDe(noPath))) {
			File pai = new File(noPath).getParentFile();
			return pai == null ? null : (pai.getParentFile() == null ? null : pai.getParentFile().getAbsolutePath());
		}

		return null;
	}

	private static String executavelJava(File pastaInstalacao) {
		File unix = new File(pastaInstalacao, "bin/java");
		if (unix.isFile()) {
			return unix.getAbsolutePath();
		}
		File windows = new File(pastaInstalacao, "bin/java.exe");
		if (windows.isFile()) {
			return windows.getAbsolutePath();
		}
		return null;
	}

	private static String acharNoPath() {
		String caminho = System.getenv("PATH");
		if (caminho == null) {
			return null;
		}
		for (String parte : caminho.split(File.pathSeparator)) {
			File java = new File(parte, "java");
			if (java.isFile()) {
				return java.getAbsolutePath();
			}
			File javaExe = new File(parte, "java.exe");
			if (javaExe.isFile()) {
				return javaExe.getAbsolutePath();
			}
		}
		return null;
	}

	/** Lê a versão real de um Java rodando "java -version". */
	private static String versaoDe(String caminhoJava) {
		if (caminhoJava == null || !new File(caminhoJava).isFile()) {
			return null;
		}
		try {
			ProcessBuilder pb = new ProcessBuilder(caminhoJava, "-version");
			pb.redirectErrorStream(true);
			Process processo = pb.start();
			try (var leitor = new java.io.BufferedReader(
					new java.io.InputStreamReader(processo.getInputStream()))) {
				String linha;
				while ((linha = leitor.readLine()) != null) {
					String versao = extrairVersao(linha);
					if (versao != null) {
						processo.destroy();
						return versao;
					}
				}
			}
			processo.waitFor();
		} catch (IOException | InterruptedException e) {
			return null;
		}
		return null;
	}

	/** Transforma 'version "1.8.0_402"' em "8" e 'version "17.0.10"' em "17". */
	private static String extrairVersao(String linha) {
		int aspas = linha.indexOf('"');
		if (aspas < 0) {
			return null;
		}
		int fim = linha.indexOf('"', aspas + 1);
		if (fim < 0) {
			return null;
		}
		String[] partes = linha.substring(aspas + 1, fim).split("[._]");
		if (partes.length == 0) {
			return null;
		}
		if (partes[0].equals("1") && partes.length > 1) {
			return String.valueOf(numero(partes[1]));
		}
		return String.valueOf(numero(partes[0]));
	}

	private static int numero(String texto) {
		try {
			return Integer.parseInt(texto);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
