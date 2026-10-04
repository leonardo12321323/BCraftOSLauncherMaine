package BCraftOSproject1.BCraftOS1;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Traz para o launcher um servidor que NÃO foi criado por ele (uma pasta que você já tinha).
 *
 * Em duas etapas:
 *
 *  1. IMPORTAR: descobre o tipo (Paper, Forge, Fabric...) e a versão olhando os arquivos da pasta,
 *     copia ou move a pasta para servidores/Tipo/versao/Nome e cria o arquivo de configuração
 *     que o launcher precisa. O mundo, os plugins, os mods e as configs vão junto, sem mudar nada.
 *
 *  2. LIMPAR E ARRUMAR, na primeira vez que o servidor liga: move para uma pasta separada o que o
 *     launcher não usa (scripts de início, instalador, logs velhos, crash reports) e coloca na pasta
 *     certa os plugins/mods que estavam soltos. NADA É APAGADO: tudo que sai do lugar vai para
 *     "_arquivos-removidos-pelo-launcher/", com um LEIA-ME dizendo o que foi movido.
 */
public final class ImportadorServidores {

	private ImportadorServidores() {
	}

	public static final String PASTA_REMOVIDOS = "_arquivos-removidos-pelo-launcher";

	private static final Pattern VERSAO_MC = Pattern
			.compile("(?<![0-9])(1\\.\\d{1,2}(?:\\.\\d{1,2})?|\\d{2}\\.\\d{1,2}(?:\\.\\d{1,2})?)(?![0-9])");

	/** O que foi descoberto sobre a pasta. Qualquer campo pode ficar null se não deu para saber. */
	public static class Analise {
		public String tipo;
		public String versaoMc;
		public String jar;
		public String args;
		public int porta = 25565;
		public boolean eulaAceito;
		public final List<String> notas = new ArrayList<>();
	}

	// ------------------------------------------------------------------
	// Analisar
	// ------------------------------------------------------------------

	public static Analise analisar(File pasta) {
		Analise a = new Analise();
		if (pasta == null || !pasta.isDirectory()) {
			a.notas.add("Pasta inválida.");
			return a;
		}
		a.porta = lerPorta(pasta);
		a.eulaAceito = eulaAceito(pasta);

		List<File> jars = jarsDaRaiz(pasta);

		// Forge/NeoForge novos: a pasta libraries/ tem o arquivo de argumentos.
		File neoArgs = acharArquivo(new File(pasta, "libraries/net/neoforged/neoforge"), "unix_args.txt");
		File forgeArgs = acharArquivo(new File(pasta, "libraries/net/minecraftforge/forge"), "unix_args.txt");

		if (new File(pasta, "velocity.toml").isFile() || temJarComNome(jars, "velocity")) {
			a.tipo = CatalogoServidores.VELOCITY;
			a.jar = jarComNome(jars, "velocity");
		} else if (neoArgs != null) {
			a.tipo = CatalogoServidores.NEOFORGE;
			a.args = relativo(pasta, neoArgs);
			String versaoLoader = neoArgs.getParentFile().getName();
			a.versaoMc = CatalogoVersoes.converterVersaoNeoForge(versaoLoader);
		} else if (forgeArgs != null) {
			a.tipo = CatalogoServidores.FORGE;
			a.args = relativo(pasta, forgeArgs);
			String versaoLoader = forgeArgs.getParentFile().getName(); // ex.: 1.20.1-47.2.0
			int traco = versaoLoader.indexOf('-');
			a.versaoMc = traco > 0 ? versaoLoader.substring(0, traco) : null;
		} else if (new File(pasta, "fabric-server-launch.jar").isFile()) {
			a.tipo = CatalogoServidores.FABRIC;
			a.jar = "fabric-server-launch.jar";
			File[] versoes = new File(pasta, ".fabric/server").listFiles(File::isDirectory);
			if (versoes != null && versoes.length > 0) {
				a.versaoMc = versoes[0].getName();
			}
		} else if (temJarComNome(jars, "arclight")) {
			a.tipo = CatalogoServidores.ARCLIGHT;
			a.jar = jarComNome(jars, "arclight");
		} else if (temJarComNome(jars, "purpur") || new File(pasta, "purpur.yml").isFile()) {
			a.tipo = CatalogoServidores.PURPUR;
			a.jar = jarComNome(jars, "purpur");
		} else if (temJarComNome(jars, "paper") || new File(pasta, "paper.yml").isFile()
				|| new File(pasta, "config/paper-global.yml").isFile()) {
			a.tipo = CatalogoServidores.PAPER;
			a.jar = jarComNome(jars, "paper");
		} else if (temJarComNome(jars, "spigot") || temJarComNome(jars, "craftbukkit")
				|| new File(pasta, "spigot.yml").isFile()) {
			a.tipo = CatalogoServidores.SPIGOT;
			a.jar = jarComNome(jars, "spigot");
			if (a.jar == null) {
				a.jar = jarComNome(jars, "craftbukkit");
			}
		} else {
			// Forge antigo (até a 1.16): um forge-<mc>-<build>.jar na raiz.
			for (File jar : jars) {
				String n = jar.getName().toLowerCase(Locale.ROOT);
				if (n.startsWith("forge-") && !n.contains("installer")) {
					a.tipo = CatalogoServidores.FORGE;
					a.jar = jar.getName();
					break;
				}
			}
			if (a.tipo == null) {
				for (File jar : jars) {
					String n = jar.getName().toLowerCase(Locale.ROOT);
					if (n.equals("server.jar") || n.startsWith("minecraft_server")) {
						a.tipo = CatalogoServidores.VANILLA;
						a.jar = jar.getName();
						break;
					}
				}
			}
		}

		if (a.versaoMc == null) {
			a.versaoMc = descobrirVersao(pasta, a.jar);
		}
		if (a.tipo == null) {
			a.notas.add("Não consegui identificar o tipo do servidor. Escolha na tela.");
		}
		if (a.versaoMc == null) {
			a.notas.add("Não consegui descobrir a versão do Minecraft. Digite na tela.");
		}
		return a;
	}

	/** Procura a versão no nome do jar, no version_history.json do Paper e no último log. */
	private static String descobrirVersao(File pasta, String jar) {
		if (jar != null) {
			Matcher m = VERSAO_MC.matcher(jar);
			if (m.find()) {
				return m.group(1);
			}
		}
		File historico = new File(pasta, "version_history.json");
		if (historico.isFile()) {
			try {
				Matcher m = Pattern.compile("MC:\\s*([0-9][0-9.]*)")
						.matcher(Files.readString(historico.toPath(), StandardCharsets.UTF_8));
				if (m.find()) {
					return m.group(1);
				}
			} catch (IOException ignorado) {
				// tenta o próximo
			}
		}
		File log = new File(pasta, "logs/latest.log");
		if (log.isFile()) {
			try (Stream<String> linhas = Files.lines(log.toPath(), StandardCharsets.ISO_8859_1)) {
				Pattern p = Pattern.compile("Starting minecraft server version\\s+([0-9][0-9.]*)");
				for (String linha : (Iterable<String>) linhas.limit(400)::iterator) {
					Matcher m = p.matcher(linha);
					if (m.find()) {
						return m.group(1);
					}
				}
			} catch (IOException | RuntimeException ignorado) {
				// sem log, sem versão: a tela pede
			}
		}
		return null;
	}

	private static List<File> jarsDaRaiz(File pasta) {
		File[] arquivos = pasta.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".jar"));
		List<File> lista = new ArrayList<>();
		if (arquivos != null) {
			for (File f : arquivos) {
				if (f.isFile()) {
					lista.add(f);
				}
			}
		}
		return lista;
	}

	private static boolean temJarComNome(List<File> jars, String parte) {
		return jarComNome(jars, parte) != null;
	}

	private static String jarComNome(List<File> jars, String parte) {
		for (File j : jars) {
			String n = j.getName().toLowerCase(Locale.ROOT);
			if (n.contains(parte) && !n.contains("installer")) {
				return j.getName();
			}
		}
		return null;
	}

	private static File acharArquivo(File raiz, String nome) {
		if (!raiz.isDirectory()) {
			return null;
		}
		try (Stream<Path> arvore = Files.walk(raiz.toPath(), 4)) {
			return arvore.filter(p -> p.getFileName().toString().equals(nome)).map(Path::toFile).findFirst()
					.orElse(null);
		} catch (IOException e) {
			return null;
		}
	}

	private static String relativo(File base, File arquivo) {
		return base.toPath().relativize(arquivo.toPath()).toString().replace(File.separatorChar, '/');
	}

	private static int lerPorta(File pasta) {
		File props = new File(pasta, "server.properties");
		if (props.isFile()) {
			try {
				for (String linha : Files.readAllLines(props.toPath(), StandardCharsets.UTF_8)) {
					if (linha.startsWith("server-port=")) {
						return Integer.parseInt(linha.substring("server-port=".length()).trim());
					}
				}
			} catch (IOException | NumberFormatException ignorado) {
				// usa o padrão
			}
		}
		return 25565;
	}

	private static boolean eulaAceito(File pasta) {
		File eula = new File(pasta, "eula.txt");
		if (!eula.isFile()) {
			return false;
		}
		try {
			for (String linha : Files.readAllLines(eula.toPath(), StandardCharsets.UTF_8)) {
				if (linha.trim().equalsIgnoreCase("eula=true")) {
					return true;
				}
			}
		} catch (IOException ignorado) {
			// conta como não aceito
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Importar
	// ------------------------------------------------------------------

	/**
	 * Traz a pasta para dentro de servidores/ e deixa pronta para ligar.
	 *
	 * @param mover true move a pasta (rápido, some do lugar antigo); false copia (mantém o original)
	 */
	public static GerenciadorServidores.Servidor importar(File origem, String tipo, String versaoMc,
			String nomeDigitado, boolean mover, boolean aceitarEula) throws IOException {
		if (origem == null || !origem.isDirectory()) {
			throw new IOException("A pasta escolhida não existe.");
		}
		boolean tipoValido = false;
		for (String t : CatalogoServidores.TIPOS) {
			tipoValido |= t.equals(tipo);
		}
		if (!tipoValido) {
			throw new IOException("Tipo de servidor inválido: " + tipo);
		}
		String versao = versaoMc == null ? "" : versaoMc.trim().replaceAll("[^A-Za-z0-9._-]", "");
		if (versao.isEmpty()) {
			throw new IOException("Informe a versão do Minecraft (ex.: 1.21.1).");
		}
		String nome = GerenciadorServidores.nomeSeguro(nomeDigitado);
		if (nome.isEmpty()) {
			throw new IOException("Dê um nome ao servidor (letras, números, espaço, '_' ou '-').");
		}

		File base = GerenciadorServidores.obterPastaServidores().getCanonicalFile();
		File origemReal = origem.getCanonicalFile();
		if (origemReal.toPath().startsWith(base.toPath())) {
			throw new IOException("Essa pasta já está dentro de servidores/. Escolha uma pasta de fora.");
		}
		if (base.toPath().startsWith(origemReal.toPath())) {
			throw new IOException("Essa pasta contém a pasta do launcher. Escolha a pasta do servidor.");
		}

		Analise a = analisar(origem);
		boolean proxy = CatalogoServidores.ehProxy(tipo);
		String jar = a.jar;
		String args = a.args;
		if (args == null && jar == null) {
			jar = adivinharJar(origem, tipo);
		}
		if (args != null && !tipo.equals(CatalogoServidores.FORGE) && !tipo.equals(CatalogoServidores.NEOFORGE)
				&& !tipo.equals(CatalogoServidores.ARCLIGHT)) {
			args = null; // o arquivo de argumentos só vale para Forge/NeoForge
		}
		if (args == null && jar == null) {
			throw new IOException("Não achei o .jar que liga este servidor na pasta escolhida. "
					+ "Ele precisa estar solto na pasta (ex.: paper-1.21.1.jar).");
		}
		if (!a.eulaAceito && !proxy && !aceitarEula) {
			throw new IOException("É preciso aceitar o EULA do Minecraft (https://aka.ms/MinecraftEULA).");
		}

		File destino = GerenciadorServidores.pastaDoServidor(tipo, versao, nome);
		if (destino.exists()) {
			String[] conteudo = destino.list();
			if (conteudo != null && conteudo.length > 0) {
				throw new IOException("Já existe um servidor chamado \"" + nome + "\" nessa versão.");
			}
		}
		destino.getParentFile().mkdirs();

		if (mover) {
			try {
				Files.move(origemReal.toPath(), destino.toPath());
			} catch (IOException e) {
				// Outro disco: copia e só apaga o original se a cópia deu certo.
				copiarPasta(origemReal.toPath(), destino.toPath());
				GerenciadorServidores.apagarPasta(origemReal);
			}
		} else {
			copiarPasta(origemReal.toPath(), destino.toPath());
		}

		if (!proxy && !eulaAceito(destino)) {
			Files.writeString(new File(destino, "eula.txt").toPath(),
					"# Aceito por quem importou o servidor no BCraftOS Launcher (https://aka.ms/MinecraftEULA)\n"
							+ "eula=true\n", StandardCharsets.UTF_8);
		}
		boolean mods = CatalogoServidores.categoria(tipo) == CatalogoServidores.Categoria.MODS
				|| CatalogoServidores.categoria(tipo) == CatalogoServidores.Categoria.HIBRIDO;
		Properties config = new Properties();
		config.setProperty("tipo", tipo);
		config.setProperty("codigo", versao);
		config.setProperty("versaoMc", versao);
		config.setProperty("nome", nome);
		config.setProperty("ram", PerfilMemoria.ramPadraoServidor(mods, proxy));
		config.setProperty("porta", String.valueOf(a.porta));
		if (args != null) {
			config.setProperty("args", args);
		} else {
			config.setProperty("jar", jar);
		}
		config.setProperty("importado", "true");
		config.setProperty("limpezaPendente", "true");
		GerenciadorServidores.salvarConfig(destino, config);
		return new GerenciadorServidores.Servidor(destino, config);
	}

	/** Se só há um .jar que não é instalador na raiz, é ele. Se há vários, não chuta. */
	private static String adivinharJar(File pasta, String tipo) {
		List<File> candidatos = new ArrayList<>();
		for (File j : jarsDaRaiz(pasta)) {
			String n = j.getName().toLowerCase(Locale.ROOT);
			if (!n.contains("installer") && !ehPlugin(j) && !ehMod(j)) {
				candidatos.add(j);
			}
		}
		if (candidatos.size() == 1) {
			return candidatos.get(0).getName();
		}
		String chave = tipo.toLowerCase(Locale.ROOT);
		for (File j : candidatos) {
			if (j.getName().toLowerCase(Locale.ROOT).contains(chave)) {
				return j.getName();
			}
		}
		return null;
	}

	private static void copiarPasta(Path origem, Path destino) throws IOException {
		try (Stream<Path> arvore = Files.walk(origem)) {
			for (Path p : (Iterable<Path>) arvore::iterator) {
				Path alvo = destino.resolve(origem.relativize(p).toString());
				if (Files.isDirectory(p)) {
					Files.createDirectories(alvo);
				} else {
					Files.createDirectories(alvo.getParent());
					Files.copy(p, alvo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		}
	}

	// ------------------------------------------------------------------
	// Limpar e arrumar (primeira vez que liga)
	// ------------------------------------------------------------------

	private static boolean ehPlugin(File jar) {
		return temEntrada(jar, "plugin.yml", "paper-plugin.yml");
	}

	private static boolean ehMod(File jar) {
		return temEntrada(jar, "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "fabric.mod.json",
				"quilt.mod.json");
	}

	private static boolean temEntrada(File jar, String... nomes) {
		try (ZipFile zip = new ZipFile(jar)) {
			for (String n : nomes) {
				if (zip.getEntry(n) != null) {
					return true;
				}
			}
		} catch (IOException ignorado) {
			// jar ilegível: não é classificado, fica onde está
		}
		return false;
	}

	/**
	 * Arruma a pasta de um servidor importado. Roda uma vez só. Nada é apagado: o que não é usado
	 * vai para _arquivos-removidos-pelo-launcher/, e dá para devolver ao lugar a qualquer momento.
	 */
	public static void limparEArrumar(GerenciadorServidores.Servidor s) {
		File pasta = s.pasta;
		List<String> movidos = new ArrayList<>();
		List<String> organizados = new ArrayList<>();
		File removidos = new File(pasta, PASTA_REMOVIDOS + "/"
				+ new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()));

		Set<String> protegidos = new HashSet<>();
		if (s.jar() != null) {
			protegidos.add(s.jar().toLowerCase(Locale.ROOT));
		}
		protegidos.add("server.jar");
		protegidos.add("fabric-server-launch.jar");
		File fabricProps = new File(pasta, "fabric-server-launcher.properties");
		if (fabricProps.isFile()) {
			try {
				for (String linha : Files.readAllLines(fabricProps.toPath(), StandardCharsets.UTF_8)) {
					if (linha.startsWith("serverJar=")) {
						protegidos.add(linha.substring("serverJar=".length()).trim().toLowerCase(Locale.ROOT));
					}
				}
			} catch (IOException ignorado) {
				// só deixa de proteger este nome extra
			}
		}

		CatalogoServidores.Categoria categoria = CatalogoServidores.categoria(s.tipo);
		boolean aceitaPlugins = categoria == CatalogoServidores.Categoria.PLUGINS
				|| categoria == CatalogoServidores.Categoria.HIBRIDO;
		boolean aceitaMods = categoria == CatalogoServidores.Categoria.MODS
				|| categoria == CatalogoServidores.Categoria.HIBRIDO;
		boolean servidorDePlugins = categoria == CatalogoServidores.Categoria.PLUGINS;

		File[] raiz = pasta.listFiles();
		if (raiz != null) {
			for (File f : raiz) {
				String n = f.getName();
				String m = n.toLowerCase(Locale.ROOT);
				if (!f.isFile() || n.equals(GerenciadorServidores.ARQUIVO_CONFIG)) {
					continue;
				}
				boolean protegido = protegidos.contains(m);

				// 1) lixo e sobras: scripts de início, instalador, travamentos, lixo do sistema
				boolean sobra = m.endsWith(".bat") || m.endsWith(".sh") || m.endsWith(".cmd") || m.endsWith(".ps1")
						|| m.endsWith(".command") || m.equals(".ds_store") || m.equals("thumbs.db")
						|| m.equals("desktop.ini") || m.startsWith("hs_err_pid") || m.endsWith(".mdmp")
						|| m.equals("installer.log") || m.endsWith(".jar.log")
						|| (m.endsWith(".jar") && m.contains("installer"));
				if (sobra && !protegido) {
					mover(f, new File(removidos, n), movidos, n);
					continue;
				}

				if (!m.endsWith(".jar") || protegido) {
					continue;
				}

				// 2) plugins e mods soltos na raiz vão para a pasta certa
				if (aceitaPlugins && ehPlugin(f)) {
					moverPara(f, new File(pasta, "plugins"), organizados, n + "  ->  plugins/");
				} else if (aceitaMods && ehMod(f)) {
					moverPara(f, new File(pasta, "mods"), organizados, n + "  ->  mods/");
				} else if (servidorDePlugins && m.matches(".*(paper|spigot|purpur|craftbukkit|bukkit|folia|"
						+ "minecraft_server|velocity|server).*")) {
					// 3) outro jar de servidor parado ali (versão antiga, por exemplo): sai do caminho
					mover(f, new File(removidos, n), movidos, n);
				}
			}
		}

		// 4) pastas que não servem para ligar: relatórios de travamento e logs velhos
		File crash = new File(pasta, "crash-reports");
		if (crash.isDirectory() && crash.list() != null && crash.list().length > 0) {
			mover(crash, new File(removidos, "crash-reports"), movidos, "crash-reports/");
		}
		File logs = new File(pasta, "logs");
		File[] logsVelhos = logs.listFiles(f -> f.isFile() && !f.getName().equals("latest.log"));
		if (logsVelhos != null) {
			for (File l : logsVelhos) {
				mover(l, new File(removidos, "logs/" + l.getName()), movidos, "logs/" + l.getName());
			}
		}

		// 5) garante as pastas onde o jogador coloca arquivos
		for (String extra : CatalogoServidores.pastasExtras(s.tipo)) {
			new File(pasta, extra).mkdirs();
		}

		if (!movidos.isEmpty() || !organizados.isEmpty()) {
			escreverRelatorio(removidos, movidos, organizados);
		}
		System.out.println("[BCraftOS] Servidor importado arrumado: " + movidos.size() + " arquivo(s) guardados em "
				+ PASTA_REMOVIDOS + "/ e " + organizados.size() + " organizado(s) nas pastas certas. "
				+ "Nada foi apagado.");

		s.config.setProperty("limpezaPendente", "false");
		try {
			GerenciadorServidores.salvarConfig(pasta, s.config);
		} catch (IOException e) {
			System.err.println("[BCraftOS Aviso] Não consegui marcar a limpeza como feita: " + e.getMessage());
		}
	}

	private static void mover(File origem, File destino, List<String> lista, String rotulo) {
		try {
			destino.getParentFile().mkdirs();
			Files.move(origem.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
			lista.add(rotulo);
		} catch (IOException e) {
			System.err.println("[BCraftOS Aviso] Não consegui mover " + rotulo + ": " + e.getMessage());
		}
	}

	private static void moverPara(File origem, File pastaDestino, List<String> lista, String rotulo) {
		File destino = new File(pastaDestino, origem.getName());
		if (destino.exists()) {
			return; // já existe um com esse nome lá: não mexe em nenhum dos dois
		}
		try {
			pastaDestino.mkdirs();
			Files.move(origem.toPath(), destino.toPath());
			lista.add(rotulo);
		} catch (IOException e) {
			System.err.println("[BCraftOS Aviso] Não consegui organizar " + rotulo + ": " + e.getMessage());
		}
	}

	private static void escreverRelatorio(File pasta, List<String> movidos, List<String> organizados) {
		StringBuilder t = new StringBuilder();
		t.append("BCraftOS - o que o launcher arrumou neste servidor importado\n\n");
		t.append("NADA FOI APAGADO. Os arquivos abaixo só saíram do lugar.\n");
		t.append("Para devolver algum, mova de volta para a pasta do servidor (a pasta acima desta).\n\n");
		if (!movidos.isEmpty()) {
			t.append("GUARDADOS NESTA PASTA (o launcher não usa):\n");
			for (String m : movidos) {
				t.append("  - ").append(m).append('\n');
			}
			t.append('\n');
		}
		if (!organizados.isEmpty()) {
			t.append("COLOCADOS NA PASTA CERTA:\n");
			for (String o : organizados) {
				t.append("  - ").append(o).append('\n');
			}
		}
		try {
			pasta.mkdirs();
			Files.writeString(new File(pasta, "LEIA-ME.txt").toPath(), t.toString(), StandardCharsets.UTF_8);
		} catch (IOException ignorado) {
			// o relatório é só um aviso
		}
	}
}
