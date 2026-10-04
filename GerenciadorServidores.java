package BCraftOSproject1.BCraftOS1;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Cuida da pasta "servidores", com a mesma regra de contas das versões:
 *
 *   conta protegida (Bw1_1Bw) -> servidores/Tipo/versao/NomeDoServidor
 *   qualquer outra conta      -> servidores/contas/NomeDaConta/Tipo/versao/NomeDoServidor
 *
 * Cada servidor é uma pasta independente, com o .jar, o mundo, os plugins/mods e um arquivo
 * "bcraftos-servidor.properties" que diz ao launcher como iniciar aquele servidor.
 * Uma conta nunca vê nem mexe nos servidores de outra.
 *
 * Criar um servidor baixa tudo sozinho: nada precisa ser colocado na mão.
 */
public class GerenciadorServidores {

	private static final String NOME_PASTA_SERVIDORES = "servidores";
	public static final String ARQUIVO_CONFIG = "bcraftos-servidor.properties";

	/** Escolhas que a pessoa faz na tela ao criar um servidor. */
	public static class Opcoes {
		public int porta = 25565;
		public String ram = "2G";
		public boolean modoOnline = false;
		public boolean eulaAceito = false;
	}

	/** Um servidor que já existe em disco. */
	public static class Servidor {
		public final String tipo;
		public final String codigo;
		public final String versaoMc;
		public final String nome;
		public final File pasta;
		public final Properties config;

		Servidor(File pasta, Properties config) {
			this.pasta = pasta;
			this.config = config;
			this.tipo = config.getProperty("tipo", "?");
			this.codigo = config.getProperty("codigo", "?");
			this.versaoMc = config.getProperty("versaoMc", codigo);
			this.nome = config.getProperty("nome", pasta.getName());
		}

		public String ram() {
			return config.getProperty("ram", "2G");
		}

		public int porta() {
			try {
				return Integer.parseInt(config.getProperty("porta", "25565").trim());
			} catch (NumberFormatException e) {
				return 25565;
			}
		}

		/** Arquivo .jar para iniciar com "java -jar" (null quando o servidor usa arquivo de argumentos). */
		public String jar() {
			return config.getProperty("jar");
		}

		/** Arquivo de argumentos do Forge/NeoForge novos (unix_args.txt), ou null. */
		public String args() {
			return config.getProperty("args");
		}

		@Override
		public String toString() {
			return tipo + " " + versaoMc + "  ·  " + nome;
		}
	}

	// ------------------------------------------------------------------
	// Pastas
	// ------------------------------------------------------------------

	public static File obterPastaServidores() {
		return new File(System.getProperty("user.dir"), NOME_PASTA_SERVIDORES);
	}

	/**
	 * Pasta dos servidores da conta logada. Espelha o GerenciadorVersoes: a parte "contas/Nome"
	 * é tirada do caminho que ele já calcula, então as duas regras nunca ficam diferentes.
	 */
	public static File obterPastaServidoresDaConta() {
		File base = obterPastaServidores();
		Path relativo = GerenciadorVersoes.obterPastaVersoes().toPath()
				.relativize(GerenciadorVersoes.obterPastaVersoesDaConta().toPath());
		String texto = relativo.toString();
		return texto.isEmpty() ? base : new File(base, texto);
	}

	/** Deixa só caracteres seguros no nome da pasta, para o nome nunca apontar para fora dela. */
	public static String nomeSeguro(String nome) {
		String limpo = nome == null ? "" : nome.trim().replaceAll("[^a-zA-Z0-9 _-]", "_");
		if (limpo.length() > 32) {
			limpo = limpo.substring(0, 32).trim();
		}
		return limpo;
	}

	public static File pastaDoServidor(String tipo, String codigo, String nome) {
		return new File(new File(new File(obterPastaServidoresDaConta(), tipo), codigo), nome);
	}

	/** Todos os servidores da conta logada. */
	public static List<Servidor> listar() {
		List<Servidor> encontrados = new ArrayList<>();
		File base = obterPastaServidoresDaConta();
		for (String tipo : CatalogoServidores.TIPOS) {
			File[] versoes = new File(base, tipo).listFiles(File::isDirectory);
			if (versoes == null) {
				continue;
			}
			for (File versao : versoes) {
				File[] servidores = versao.listFiles(File::isDirectory);
				if (servidores == null) {
					continue;
				}
				for (File pasta : servidores) {
					Properties config = lerConfig(pasta);
					if (config != null) {
						encontrados.add(new Servidor(pasta, config));
					}
				}
			}
		}
		encontrados.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
		return encontrados;
	}

	private static Properties lerConfig(File pasta) {
		File arquivo = new File(pasta, ARQUIVO_CONFIG);
		if (!arquivo.isFile()) {
			return null;
		}
		Properties config = new Properties();
		try (Reader leitor = Files.newBufferedReader(arquivo.toPath(), StandardCharsets.UTF_8)) {
			config.load(leitor);
			return config;
		} catch (IOException e) {
			return null;
		}
	}

	// ------------------------------------------------------------------
	// Criar
	// ------------------------------------------------------------------

	/**
	 * Baixa e instala um servidor novo. Se qualquer etapa falhar, a pasta é apagada
	 * para não sobrar um servidor pela metade.
	 */
	public static Servidor criar(String tipo, CatalogoVersoes.ItemVersao versao, String nomeDigitado,
			Opcoes opcoes, GerenciadorDownloads.Progresso progresso) throws Exception {
		String nome = nomeSeguro(nomeDigitado);
		if (nome.isEmpty()) {
			throw new IllegalStateException("Dê um nome ao servidor (letras, números, espaço, '_' ou '-').");
		}
		boolean proxy = CatalogoServidores.ehProxy(tipo);
		if (!proxy && !opcoes.eulaAceito) {
			throw new IllegalStateException("Para criar o servidor é preciso aceitar o EULA do Minecraft "
					+ "(https://aka.ms/MinecraftEULA).");
		}
		if (opcoes.porta < 1024 || opcoes.porta > 65535) {
			throw new IllegalStateException("A porta precisa ficar entre 1024 e 65535.");
		}

		File pasta = pastaDoServidor(tipo, versao.codigo, nome);
		if (pasta.exists()) {
			if (new File(pasta, ARQUIVO_CONFIG).isFile()) {
				throw new IllegalStateException("Já existe um servidor chamado \"" + nome + "\" nessa versão.");
			}
			apagarPasta(pasta); // sobra de uma instalação que foi interrompida no meio
		}

		// Confere o Java antes de baixar qualquer coisa: se faltar, o motivo aparece logo.
		File java = ExecutorServidor.executavelJava(tipo, versao.versaoMc);

		pasta.mkdirs();
		Properties config = new Properties();
		config.setProperty("tipo", tipo);
		config.setProperty("codigo", versao.codigo);
		config.setProperty("versaoMc", versao.versaoMc);
		config.setProperty("nome", nome);
		config.setProperty("ram", opcoes.ram);
		config.setProperty("porta", String.valueOf(opcoes.porta));

		try {
			switch (tipo) {
				case CatalogoServidores.VANILLA:
					instalarJarDireto(pasta, config, CatalogoServidores.enderecoVanilla(versao.versaoMc),
							"server.jar", "Vanilla " + versao.versaoMc, progresso);
					break;
				case CatalogoServidores.PAPER:
					instalarJarDireto(pasta, config,
							CatalogoServidores.enderecoPelaPaperMC("paper", versao.versaoMc),
							"server.jar", "Paper " + versao.versaoMc, progresso);
					break;
				case CatalogoServidores.PURPUR:
					instalarJarDireto(pasta, config, CatalogoServidores.enderecoPurpur(versao.versaoMc),
							"server.jar", "Purpur " + versao.versaoMc, progresso);
					break;
				case CatalogoServidores.FABRIC:
					// O launcher do Fabric baixa o server.jar da Mojang na primeira vez que liga.
					// Por isso o nome dele NÃO pode ser server.jar, senão um apaga o outro.
					instalarJarDireto(pasta, config, CatalogoServidores.enderecoFabric(versao.versaoMc),
							"fabric-server-launcher.jar", "Fabric " + versao.versaoMc, progresso);
					break;
				case CatalogoServidores.ARCLIGHT:
					// Um único .jar que roda o loader escolhido; baixa o resto na primeira vez que liga.
					instalarJarDireto(pasta, config, CatalogoServidores.enderecoArclight(versao.codigo),
							"arclight.jar", "Arclight " + versao.versaoMc, progresso);
					break;
				case CatalogoServidores.VELOCITY:
					instalarJarDireto(pasta, config,
							CatalogoServidores.enderecoPelaPaperMC("velocity", versao.codigo),
							"velocity.jar", "Velocity " + versao.codigo, progresso);
					break;
				case CatalogoServidores.FORGE:
					instalarPorInstalador(pasta, config, java,
							CatalogoServidores.enderecoInstaladorForge(versao.codigo), "Forge " + versao.versaoMc,
							progresso);
					break;
				case CatalogoServidores.NEOFORGE:
					instalarPorInstalador(pasta, config, java,
							CatalogoServidores.enderecoInstaladorNeoForge(versao.codigo),
							"NeoForge " + versao.versaoMc, progresso);
					break;
				case CatalogoServidores.SPIGOT:
					instalarSpigot(pasta, config, java, versao.versaoMc, progresso);
					break;
				default:
					throw new IllegalStateException("Tipo de servidor desconhecido: " + tipo);
			}

			progresso.atualizar("Criando arquivos de configuração...", 97);
			escreverArquivosBase(pasta, tipo, opcoes);
			salvarConfig(pasta, config);
		} catch (Exception erro) {
			apagarPasta(pasta);
			pasta.getParentFile().delete(); // só some se ficou vazia
			throw erro;
		}

		progresso.atualizar("Pronto.", 100);
		return new Servidor(pasta, config);
	}

	/** Paper, Purpur, Vanilla, Fabric e Velocity: o download já é o .jar que roda. */
	private static void instalarJarDireto(File pasta, Properties config, String endereco, String nomeDoJar,
			String rotulo, GerenciadorDownloads.Progresso progresso) throws Exception {
		File jar = new File(pasta, nomeDoJar);
		baixar(endereco, jar, progresso, "Baixando " + rotulo, 5, 95);
		config.setProperty("jar", nomeDoJar);
	}

	/** Forge e NeoForge: baixa o instalador oficial e roda com --installServer. */
	private static void instalarPorInstalador(File pasta, Properties config, File java, String endereco,
			String rotulo, GerenciadorDownloads.Progresso progresso) throws Exception {
		File instalador = new File(pasta, "instalador.jar");
		baixar(endereco, instalador, progresso, "Baixando instalador do " + rotulo, 3, 15);

		List<String> comando = new ArrayList<>();
		comando.add(java.getAbsolutePath());
		comando.add("-jar");
		comando.add(instalador.getName());
		comando.add("--installServer");
		executar(comando, pasta, progresso, "Instalando " + rotulo + " (baixa bibliotecas, pode demorar)", 15, 95);

		instalador.delete();
		new File(pasta, "instalador.jar.log").delete();
		detectarLancamento(pasta, config);
	}

	/**
	 * Spigot não distribui o .jar pronto: o BuildTools oficial baixa o código e compila.
	 * No Windows o BuildTools traz o próprio git; no Linux e no Mac o git precisa estar instalado.
	 */
	private static void instalarSpigot(File pasta, Properties config, File java, String versaoMc,
			GerenciadorDownloads.Progresso progresso) throws Exception {
		if (!ehWindows() && !gitInstalado()) {
			throw new IOException("O Spigot precisa do git para ser compilado e ele não está instalado.\n"
					+ "No Linux Mint/Ubuntu: sudo apt install git\n"
					+ "Ou escolha o Paper, que faz o mesmo papel sem compilar nada.");
		}
		File pastaBuild = new File(pasta, ".buildtools");
		pastaBuild.mkdirs();
		baixar(CatalogoServidores.URL_BUILDTOOLS, new File(pastaBuild, "BuildTools.jar"), progresso,
				"Baixando o BuildTools do Spigot", 3, 10);

		List<String> comando = new ArrayList<>();
		comando.add(java.getAbsolutePath());
		comando.add("-jar");
		comando.add("BuildTools.jar");
		comando.add("--rev");
		comando.add(versaoMc);
		executar(comando, pastaBuild, progresso, "Compilando o Spigot " + versaoMc + " (leva alguns minutos)", 10, 95);

		File[] prontos = pastaBuild.listFiles((dir, nome) -> nome.startsWith("spigot-") && nome.endsWith(".jar"));
		if (prontos == null || prontos.length == 0) {
			throw new IOException("O BuildTools terminou mas não gerou o spigot-" + versaoMc + ".jar.");
		}
		Files.move(prontos[0].toPath(), new File(pasta, "server.jar").toPath(), StandardCopyOption.REPLACE_EXISTING);
		apagarPasta(pastaBuild);
		config.setProperty("jar", "server.jar");
	}

	private static boolean gitInstalado() {
		try {
			Process p = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
			p.getInputStream().readAllBytes();
			return p.waitFor() == 0;
		} catch (IOException | InterruptedException e) {
			return false;
		}
	}

	/**
	 * Depois do instalador, descobre como esse servidor Forge/NeoForge liga:
	 *  - versões novas (1.17+) criam um arquivo de argumentos (unix_args.txt);
	 *  - versões antigas criam um .jar do Forge para rodar com "java -jar".
	 */
	private static void detectarLancamento(File pasta, Properties config) throws IOException {
		File args = acharArquivo(new File(pasta, "libraries"), "unix_args.txt");
		if (args != null) {
			String relativo = pasta.toPath().relativize(args.toPath()).toString().replace(File.separatorChar, '/');
			config.setProperty("args", relativo);
			return;
		}

		File[] jars = pasta.listFiles((dir, nome) -> nome.toLowerCase(Locale.ROOT).endsWith(".jar"));
		File escolhido = null;
		if (jars != null) {
			for (File jar : jars) {
				String nome = jar.getName().toLowerCase(Locale.ROOT);
				if (nome.startsWith("forge-") && nome.contains("universal")) {
					escolhido = jar;
					break;
				}
			}
			if (escolhido == null) {
				for (File jar : jars) {
					String nome = jar.getName().toLowerCase(Locale.ROOT);
					if (nome.startsWith("forge-") && !nome.contains("installer") && !nome.contains("shim")) {
						escolhido = jar;
						break;
					}
				}
			}
		}
		if (escolhido == null) {
			throw new IOException("A instalação terminou, mas não achei o arquivo que liga o servidor. "
					+ "Tente criar de novo.");
		}
		config.setProperty("jar", escolhido.getName());
	}

	private static File acharArquivo(File pasta, String nome) {
		File[] filhos = pasta.listFiles();
		if (filhos == null) {
			return null;
		}
		for (File filho : filhos) {
			if (filho.isFile() && filho.getName().equals(nome)) {
				return filho;
			}
		}
		for (File filho : filhos) {
			if (filho.isDirectory()) {
				File dentro = acharArquivo(filho, nome);
				if (dentro != null) {
					return dentro;
				}
			}
		}
		return null;
	}

	/** eula.txt, server.properties e a pasta de plugins/mods. O proxy não usa nenhum dos dois primeiros. */
	private static void escreverArquivosBase(File pasta, String tipo, Opcoes opcoes) throws IOException {
		for (String extra : CatalogoServidores.pastasExtras(tipo)) {
			new File(pasta, extra).mkdirs();
		}
		if (CatalogoServidores.ehProxy(tipo)) {
			return;
		}
		String eula = "# Aceito por quem criou o servidor no BCraftOS Launcher (https://aka.ms/MinecraftEULA)"
				+ System.lineSeparator() + "eula=true" + System.lineSeparator();
		Files.writeString(new File(pasta, "eula.txt").toPath(), eula, StandardCharsets.UTF_8);

		StringBuilder props = new StringBuilder();
		props.append("server-port=").append(opcoes.porta).append('\n');
		props.append("online-mode=").append(opcoes.modoOnline).append('\n');
		if (!opcoes.modoOnline) {
			// Sem contas oficiais não há chaves de chat; sem isso o jogo mais novo reclama do chat.
			props.append("enforce-secure-profile=false\n");
		}
		props.append("motd=Servidor BCraftOS\n");
		// Distâncias menores deixam o servidor bem mais leve (e dá para aumentar depois se sobrar).
		props.append("view-distance=8\n");
		props.append("simulation-distance=5\n");
		Files.writeString(new File(pasta, "server.properties").toPath(), props.toString(), StandardCharsets.UTF_8);
	}

	static void salvarConfig(File pasta, Properties config) throws IOException {
		try (Writer escritor = Files.newBufferedWriter(new File(pasta, ARQUIVO_CONFIG).toPath(),
				StandardCharsets.UTF_8)) {
			config.store(escritor, "BCraftOS - dados deste servidor. Pode editar a RAM e a porta aqui.");
		}
	}

	// ------------------------------------------------------------------
	// Excluir
	// ------------------------------------------------------------------

	/** Apaga a pasta inteira do servidor (mundo incluso). Só age dentro da pasta servidores/. */
	public static void excluir(Servidor servidor) throws IOException {
		Path base = obterPastaServidores().getCanonicalFile().toPath();
		Path alvo = servidor.pasta.getCanonicalFile().toPath();
		if (!alvo.startsWith(base) || alvo.equals(base)) {
			throw new IOException("Essa pasta não está dentro de servidores/. Não vou apagar.");
		}
		apagarPasta(servidor.pasta);
		// Limpa as pastas de versão e de tipo que ficaram vazias (só some se estiver vazia).
		File versao = servidor.pasta.getParentFile();
		if (versao != null && versao.delete() && versao.getParentFile() != null) {
			versao.getParentFile().delete();
		}
	}

	// ------------------------------------------------------------------
	// Download e execução
	// ------------------------------------------------------------------

	private static void baixar(String endereco, File destino, GerenciadorDownloads.Progresso progresso,
			String rotulo, int de, int ate) throws Exception {
		HttpURLConnection conexao = CatalogoServidores.abrir(endereco);
		int codigo = conexao.getResponseCode();
		if (codigo != HttpURLConnection.HTTP_OK) {
			conexao.disconnect();
			throw new IOException("O servidor respondeu " + codigo + " ao baixar " + endereco);
		}
		long total = conexao.getContentLengthLong();
		progresso.atualizar(rotulo + "...", de);
		try (InputStream entrada = conexao.getInputStream();
				FileOutputStream saida = new FileOutputStream(destino)) {
			byte[] buffer = new byte[16384];
			long baixado = 0;
			int lido;
			int ultimo = -1;
			while ((lido = entrada.read(buffer)) != -1) {
				saida.write(buffer, 0, lido);
				baixado += lido;
				if (total > 0) {
					int percentual = de + (int) (baixado * (ate - de) / total);
					if (percentual != ultimo) {
						ultimo = percentual;
						progresso.atualizar(rotulo + "... " + (baixado / 1024 / 1024) + " MB de "
								+ (total / 1024 / 1024) + " MB", percentual);
					}
				}
			}
		} finally {
			conexao.disconnect();
		}
	}

	/** Roda um programa mostrando a última linha da saída como andamento. Falha com o final da saída. */
	private static void executar(List<String> comando, File pasta, GerenciadorDownloads.Progresso progresso,
			String etapa, int de, int ate) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(comando);
		pb.directory(pasta);
		pb.redirectErrorStream(true);
		Process processo = pb.start();

		Deque<String> ultimas = new ArrayDeque<>();
		int linhas = 0;
		try (BufferedReader leitor = new BufferedReader(
				new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
			String linha;
			while ((linha = leitor.readLine()) != null) {
				linhas++;
				ultimas.addLast(linha);
				if (ultimas.size() > 15) {
					ultimas.removeFirst();
				}
				int percentual = de + Math.min(ate - de - 1, linhas / 12);
				String curta = linha.length() > 60 ? linha.substring(0, 60) + "..." : linha;
				progresso.atualizar(etapa + " — " + curta, percentual);
			}
		}
		int saida = processo.waitFor();
		if (saida != 0) {
			throw new IOException(etapa + " falhou (código " + saida + "). Final da saída:\n\n"
					+ String.join("\n", ultimas));
		}
	}

	private static boolean ehWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}

	static void apagarPasta(File pasta) {
		if (!pasta.exists() && !Files.isSymbolicLink(pasta.toPath())) {
			return;
		}
		if (Files.isSymbolicLink(pasta.toPath())) {
			pasta.delete(); // atalho: apaga só o atalho, nunca o que tem dentro do destino
			return;
		}
		if (pasta.isDirectory()) {
			File[] filhos = pasta.listFiles();
			if (filhos != null) {
				for (File filho : filhos) {
					apagarPasta(filho);
				}
			}
		}
		pasta.delete();
	}
}
