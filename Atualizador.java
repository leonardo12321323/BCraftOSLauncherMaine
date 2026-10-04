import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Atualizador do código do BCraftOS. Roda ANTES da compilação (os scripts executar.sh e
 * EXECUTAR.bat chamam isto sozinhos):   java Atualizador.java
 *
 * Baixa o projeto do GitHub e troca só os arquivos .java que mudaram. NUNCA mexe em versoes/,
 * servidores/, usuarios/, java/, logs/ nem em mods: só em arquivos .java do launcher.
 *
 * Cuidados:
 *  - Sem internet ou com qualquer erro, ele só avisa e o launcher abre com os arquivos que já tem.
 *  - Se VOCÊ editou um arquivo, ele NÃO sobrescreve (guarda o seu). Para forçar:  --forcar
 *  - Antes de trocar, copia os arquivos antigos para backup-codigo/. Se a compilação falhar depois
 *    da atualização, os scripts voltam sozinhos para a versão anterior (--restaurar).
 *  - Para desligar as atualizações: crie um arquivo vazio chamado nao-atualizar.txt nesta pasta
 *    (ou defina a variável BCRAFTOS_SEM_ATUALIZAR=1).
 *
 * Códigos de saída: 0 = nada mudou (ou não foi possível checar), 10 = arquivos atualizados.
 * Configuração opcional em atualizador.properties:  repo=usuario/repositorio   branch=main
 */
public class Atualizador {

	private static final String REPO_PADRAO = "leonardo12321323/BCraftOSLauncherMaine";
	private static final String BRANCH_PADRAO = "main";
	private static final int BACKUPS_GUARDADOS = 5;
	/** Arquivos que o atualizador nunca troca: ele mesmo e o instalador (que mora em outra pasta). */
	private static final List<String> IGNORADOS = Arrays.asList("Atualizador.java", "InstaladorBCraftOS.java");
	/** Pastas de dados e de saída: nunca são vasculhadas. */
	private static final List<String> PASTAS_DE_DADOS = Arrays.asList("saida", "versoes", "servidores", "usuarios",
			"java", "logs", "backup-codigo", "cache-normal");
	private static final Pattern PACOTE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

	private static final Path RAIZ = Paths.get("").toAbsolutePath();
	private static final Path ARQ_ESTADO = RAIZ.resolve(".atualizador-estado");
	private static final Path ARQ_ULTIMO_BACKUP = RAIZ.resolve(".atualizador-ultimo-backup");
	/** Existe quando a última atualização quebrou a compilação e foi desfeita: pausa até --forcar. */
	private static final Path ARQ_PAUSADO = RAIZ.resolve(".atualizador-pausado");
	private static final Path PASTA_BACKUPS = RAIZ.resolve("backup-codigo");

	public static void main(String[] args) {
		List<String> opcoes = Arrays.asList(args);
		try {
			if (opcoes.contains("--restaurar")) {
				restaurar();
				return;
			}
			if (System.getenv("BCRAFTOS_SEM_ATUALIZAR") != null || Files.exists(RAIZ.resolve("nao-atualizar.txt"))) {
				System.out.println("[Atualizador] Atualizacoes desligadas (nao-atualizar.txt). Seguindo.");
				return;
			}
			if (Files.exists(ARQ_PAUSADO)) {
				if (!opcoes.contains("--forcar")) {
					System.out.println("[Atualizador] Atualizacoes pausadas: a ultima quebrou a compilacao e foi desfeita. "
							+ "Para tentar de novo:  java Atualizador.java --forcar");
					return;
				}
				Files.deleteIfExists(ARQ_PAUSADO);
			}
			boolean mudou = atualizar(opcoes.contains("--forcar"));
			System.exit(mudou ? 10 : 0);
		} catch (Exception e) {
			System.out.println("[Atualizador] Nao consegui verificar atualizacoes (" + e.getMessage()
					+ "). Seguindo com os arquivos que ja existem.");
		}
	}

	// ------------------------------------------------------------------
	// Atualizar
	// ------------------------------------------------------------------

	private static boolean atualizar(boolean forcar) throws Exception {
		Properties config = new Properties();
		Path arqConfig = RAIZ.resolve("atualizador.properties");
		if (Files.exists(arqConfig)) {
			try (InputStream in = Files.newInputStream(arqConfig)) {
				config.load(in);
			}
		}
		String repo = config.getProperty("repo", REPO_PADRAO).trim();
		String branch = config.getProperty("branch", BRANCH_PADRAO).trim();
		String endereco = System.getProperty("bcraftos.zip",
				"https://github.com/" + repo + "/archive/refs/heads/" + branch + ".zip");

		System.out.println("[Atualizador] Procurando atualizacoes em " + repo + " (" + branch + ")...");
		byte[] pacote = baixar(endereco);
		Map<String, byte[]> remotos = lerFontes(pacote);

		// Segurança: um repositório sem as classes principais não é uma atualização do launcher.
		if (!remotos.containsKey("BCraftOS1.java") || !remotos.containsKey("MinecraftLauncher.java")) {
			System.out.println("[Atualizador] O repositorio nao parece ter o launcher completo. Nada foi trocado.");
			return false;
		}

		Map<String, Path> locais = indexarLocais();
		Properties estado = lerEstado();
		boolean usaSrc = Files.isDirectory(RAIZ.resolve("src"));

		List<String> trocar = new ArrayList<>();
		List<String> novos = new ArrayList<>();
		List<String> mantidos = new ArrayList<>();
		Map<String, Path> destinos = new LinkedHashMap<>();

		for (Map.Entry<String, byte[]> r : remotos.entrySet()) {
			String nome = r.getKey();
			String hashRemoto = sha256(r.getValue());
			Path local = locais.get(nome);
			if (local == null) {
				novos.add(nome);
				destinos.put(nome, destinoNovo(nome, r.getValue(), usaSrc));
				continue;
			}
			String hashLocal = sha256(Files.readAllBytes(local));
			if (hashLocal.equals(hashRemoto)) {
				estado.setProperty(nome, hashRemoto); // já está igual: vira o ponto de partida
				continue;
			}
			String base = estado.getProperty(nome);
			if (forcar || hashLocal.equals(base)) {
				trocar.add(nome);
				destinos.put(nome, local);
			} else {
				mantidos.add(nome); // diferente e sem prova de que você não mexeu: não sobrescreve
			}
		}

		if (!mantidos.isEmpty()) {
			System.out.println("[Atualizador] " + mantidos.size() + " arquivo(s) diferem do GitHub e foram MANTIDOS "
					+ "(editados por voce, ou ainda nao enviados ao GitHub): " + String.join(", ", mantidos));
			System.out.println("[Atualizador] Para trocar mesmo assim:  java Atualizador.java --forcar");
		}
		if (trocar.isEmpty() && novos.isEmpty()) {
			salvarEstado(estado);
			System.out.println("[Atualizador] Codigo ja esta atualizado.");
			return false;
		}

		// Backup dos que serão trocados, antes de qualquer escrita.
		Path backup = PASTA_BACKUPS.resolve(new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()));
		for (String nome : trocar) {
			Path origem = destinos.get(nome);
			Path copia = backup.resolve(RAIZ.relativize(origem).toString());
			Files.createDirectories(copia.getParent());
			Files.copy(origem, copia, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.createDirectories(backup);
		List<String> novosRelativos = new ArrayList<>();
		for (String nome : novos) {
			novosRelativos.add(RAIZ.relativize(destinos.get(nome)).toString());
		}
		Files.write(backup.resolve("novos.txt"), novosRelativos, StandardCharsets.UTF_8);
		Files.write(ARQ_ULTIMO_BACKUP, backup.toString().getBytes(StandardCharsets.UTF_8));

		// Escreve cada arquivo num temporário e só então troca (nunca deixa um arquivo pela metade).
		for (String nome : concat(trocar, novos)) {
			Path destino = destinos.get(nome);
			Files.createDirectories(destino.getParent());
			Path temp = destino.resolveSibling(destino.getFileName() + ".novo");
			Files.write(temp, remotos.get(nome));
			Files.move(temp, destino, StandardCopyOption.REPLACE_EXISTING);
			estado.setProperty(nome, sha256(remotos.get(nome)));
		}
		salvarEstado(estado);
		podarBackups();

		System.out.println("[Atualizador] Atualizado: " + trocar.size() + " arquivo(s) trocado(s), " + novos.size()
				+ " novo(s). Copia dos antigos em " + RAIZ.relativize(backup));
		for (String nome : trocar) {
			System.out.println("              ~ " + nome);
		}
		for (String nome : novos) {
			System.out.println("              + " + nome);
		}
		return true;
	}

	/** Volta para os arquivos de antes da última atualização (usado se a compilação falhar). */
	private static void restaurar() throws IOException {
		if (!Files.exists(ARQ_ULTIMO_BACKUP)) {
			System.out.println("[Atualizador] Nao ha backup para restaurar.");
			return;
		}
		Path backup = Paths.get(new String(Files.readAllBytes(ARQ_ULTIMO_BACKUP), StandardCharsets.UTF_8).trim());
		if (!Files.isDirectory(backup)) {
			System.out.println("[Atualizador] A pasta de backup nao existe mais: " + backup);
			return;
		}
		Properties estado = lerEstado();
		try (Stream<Path> arvore = Files.walk(backup)) {
			for (Path arquivo : (Iterable<Path>) arvore.filter(Files::isRegularFile)::iterator) {
				if (arquivo.getFileName().toString().equals("novos.txt")) {
					continue;
				}
				Path destino = RAIZ.resolve(backup.relativize(arquivo).toString());
				Files.createDirectories(destino.getParent());
				Files.copy(arquivo, destino, StandardCopyOption.REPLACE_EXISTING);
				estado.remove(destino.getFileName().toString()); // não reaplica a atualização que quebrou
			}
		}
		Path novos = backup.resolve("novos.txt");
		if (Files.exists(novos)) {
			for (String relativo : Files.readAllLines(novos, StandardCharsets.UTF_8)) {
				if (!relativo.trim().isEmpty()) {
					Files.deleteIfExists(RAIZ.resolve(relativo.trim()));
					estado.remove(Paths.get(relativo.trim()).getFileName().toString());
				}
			}
		}
		salvarEstado(estado);
		Files.deleteIfExists(ARQ_ULTIMO_BACKUP);
		Files.write(ARQ_PAUSADO, "pausado".getBytes(StandardCharsets.UTF_8));
		System.out.println("[Atualizador] Voltei para os arquivos de antes da atualizacao. "
				+ "Ela nao sera reaplicada ate voce rodar:  java Atualizador.java --forcar");
	}

	// ------------------------------------------------------------------
	// Download e leitura
	// ------------------------------------------------------------------

	private static byte[] baixar(String endereco) throws IOException {
		URLConnection conexao = java.net.URI.create(endereco).toURL().openConnection();
		conexao.setConnectTimeout(8000); // sem internet: não segura a abertura do launcher
		conexao.setReadTimeout(20000);
		conexao.setRequestProperty("User-Agent", "BCraftOS-Atualizador");
		try (InputStream in = conexao.getInputStream()) {
			return in.readAllBytes();
		}
	}

	private static Map<String, byte[]> lerFontes(byte[] pacote) throws IOException {
		Map<String, byte[]> fontes = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pacote))) {
			ZipEntry entrada;
			while ((entrada = zip.getNextEntry()) != null) {
				String caminho = entrada.getName();
				if (entrada.isDirectory() || !caminho.endsWith(".java")) {
					continue;
				}
				String nome = caminho.substring(caminho.lastIndexOf('/') + 1);
				byte[] conteudo = zip.readAllBytes();
				if (IGNORADOS.contains(nome) || conteudo.length == 0) {
					continue;
				}
				String texto = new String(conteudo, StandardCharsets.UTF_8);
				Matcher m = PACOTE.matcher(texto);
				if (!m.find() || !m.group(1).startsWith("BCraftOSproject1")) {
					continue; // não é um arquivo do launcher
				}
				fontes.put(nome, conteudo);
			}
		}
		return fontes;
	}

	// ------------------------------------------------------------------
	// Arquivos locais
	// ------------------------------------------------------------------

	/** Acha os .java do launcher: soltos na pasta ou dentro de src/. */
	private static Map<String, Path> indexarLocais() throws IOException {
		Map<String, Path> mapa = new LinkedHashMap<>();
		try (Stream<Path> raiz = Files.list(RAIZ)) {
			for (Path p : (Iterable<Path>) raiz.filter(Files::isRegularFile)::iterator) {
				adicionarSeJava(mapa, p);
			}
		}
		Path src = RAIZ.resolve("src");
		if (Files.isDirectory(src)) {
			try (Stream<Path> arvore = Files.walk(src)) {
				for (Path p : (Iterable<Path>) arvore.filter(Files::isRegularFile)::iterator) {
					adicionarSeJava(mapa, p);
				}
			}
		}
		return mapa;
	}

	private static void adicionarSeJava(Map<String, Path> mapa, Path p) {
		String nome = p.getFileName().toString();
		if (nome.endsWith(".java") && !IGNORADOS.contains(nome)) {
			mapa.putIfAbsent(nome, p);
		}
	}

	private static Path destinoNovo(String nome, byte[] conteudo, boolean usaSrc) {
		if (!usaSrc) {
			return RAIZ.resolve(nome);
		}
		Matcher m = PACOTE.matcher(new String(conteudo, StandardCharsets.UTF_8));
		String pacote = m.find() ? m.group(1) : "";
		return RAIZ.resolve("src").resolve(pacote.replace('.', '/')).resolve(nome);
	}

	// ------------------------------------------------------------------
	// Estado, backups e utilitários
	// ------------------------------------------------------------------

	private static Properties lerEstado() throws IOException {
		Properties p = new Properties();
		if (Files.exists(ARQ_ESTADO)) {
			try (InputStream in = Files.newInputStream(ARQ_ESTADO)) {
				p.load(in);
			}
		}
		return p;
	}

	private static void salvarEstado(Properties p) throws IOException {
		try (java.io.OutputStream out = Files.newOutputStream(ARQ_ESTADO)) {
			p.store(out, "BCraftOS Atualizador: hash de cada arquivo na ultima sincronizacao. Pode apagar.");
		}
	}

	private static void podarBackups() throws IOException {
		if (!Files.isDirectory(PASTA_BACKUPS)) {
			return;
		}
		List<Path> pastas = new ArrayList<>();
		try (Stream<Path> lista = Files.list(PASTA_BACKUPS)) {
			lista.filter(Files::isDirectory).sorted(Comparator.comparing(Path::toString)).forEach(pastas::add);
		}
		for (int i = 0; i < pastas.size() - BACKUPS_GUARDADOS; i++) {
			try (Stream<Path> arvore = Files.walk(pastas.get(i))) {
				arvore.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}

	private static List<String> concat(List<String> a, List<String> b) {
		List<String> r = new ArrayList<>(a);
		r.addAll(b);
		return r;
	}

	private static String sha256(byte[] dados) throws Exception {
		StringBuilder hex = new StringBuilder();
		for (byte b : MessageDigest.getInstance("SHA-256").digest(dados)) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}
}
