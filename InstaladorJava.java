package BCraftOSproject1.BCraftOS1;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Instalador automático de JDK.
 *
 * Quando o Minecraft pede um Java que o computador não tem (8, 17, 21 ou 25), o launcher
 * baixa o Temurin (Eclipse Adoptium, gratuito) sozinho e guarda em  java/jdk-<versão>/
 * dentro da pasta do launcher. Nada é instalado no sistema: não precisa de administrador,
 * não mexe no PATH e não atrapalha outros Java que a pessoa tenha.
 *
 * Passos: descobre sistema e arquitetura -> consulta a API da Adoptium (link + checksum)
 * -> baixa com retomada -> confere o SHA-256 -> extrai (zip no Windows, tar.gz no resto)
 * -> só então move para a pasta final. Se algo falhar no meio, nada fica pela metade.
 */
public final class InstaladorJava {

	private InstaladorJava() {
	}

	private static final String MARCADOR = ".bcraftos-java-ok";
	private static final Object TRAVA = new Object();

	/** Pasta onde ficam os JDKs baixados pelo launcher. */
	public static File pastaRaiz() {
		return new File(System.getProperty("user.dir"), "java");
	}

	public static File pastaDoJdk(String versao) {
		return new File(pastaRaiz(), "jdk-" + versao);
	}

	/** Devolve o JAVA_HOME do JDK já baixado para essa versão, ou null se ainda não existe. */
	public static String casaInstalada(String versao) {
		File pasta = pastaDoJdk(versao);
		if (!new File(pasta, MARCADOR).isFile()) {
			return null;
		}
		return acharCasa(pasta);
	}

	/** No macOS o JDK fica em Contents/Home; nos outros, na própria pasta. */
	private static String acharCasa(File pasta) {
		File[] candidatas = {new File(pasta, "Contents/Home"), pasta};
		for (File c : candidatas) {
			if (new File(c, "bin/java").isFile() || new File(c, "bin/java.exe").isFile()) {
				return c.getAbsolutePath();
			}
		}
		return null;
	}

	/**
	 * Garante o JDK da versão pedida: usa o que já foi baixado ou baixa agora.
	 *
	 * @return o JAVA_HOME pronto para usar
	 */
	public static String garantir(String versao, GerenciadorDownloads.Progresso progresso) throws Exception {
		synchronized (TRAVA) {
			String pronto = casaInstalada(versao);
			if (pronto != null) {
				return pronto;
			}
			return instalar(versao, progresso);
		}
	}

	private static String instalar(String versao, GerenciadorDownloads.Progresso progresso) throws Exception {
		String sistema = sistemaAdoptium();
		String arquitetura = arquiteturaAdoptium(sistema, versao);
		boolean zip = "windows".equals(sistema);

		GerenciadorDownloads.garantirEspaco(pastaRaiz(), 900);

		File temporaria = new File(pastaRaiz(), ".baixando-" + versao);
		apagar(temporaria);
		temporaria.mkdirs();

		progresso.atualizar("Procurando o Java " + versao + " para " + sistema + "/" + arquitetura + "...", 2);
		String link = null;
		String checksum = null;
		try {
			String api = "https://api.adoptium.net/v3/assets/latest/" + versao + "/hotspot?architecture="
					+ arquitetura + "&image_type=jdk&os=" + sistema + "&vendor=eclipse";
			Object json = MiniJson.ler(CatalogoVersoesAcesso.texto(api));
			List<Object> lista = MiniJson.lista(json);
			for (Object item : lista) {
				Map<String, Object> binario = MiniJson.objeto(MiniJson.objeto(item).get("binary"));
				Map<String, Object> pacote = binario == null ? null : MiniJson.objeto(binario.get("package"));
				if (pacote != null && MiniJson.texto(pacote.get("link")) != null) {
					link = MiniJson.texto(pacote.get("link"));
					checksum = MiniJson.texto(pacote.get("checksum"));
					break;
				}
			}
		} catch (Exception ignorado) {
			// A API de consulta falhou: usamos o endereço direto abaixo (sem checksum).
		}
		if (link == null) {
			link = "https://api.adoptium.net/v3/binary/latest/" + versao + "/ga/" + sistema + "/" + arquitetura
					+ "/jdk/hotspot/normal/eclipse";
		}

		File pacote = new File(temporaria, zip ? "jdk.zip" : "jdk.tar.gz");
		GerenciadorDownloads.baixarArquivo(link, pacote, progresso, 5, 80, "Baixando o Java " + versao);

		if (checksum != null && !checksum.isEmpty()) {
			progresso.atualizar("Conferindo se o arquivo veio inteiro...", 81);
			String obtido = sha256(pacote);
			if (!obtido.equalsIgnoreCase(checksum)) {
				apagar(temporaria);
				throw new IOException("O Java baixado veio corrompido (checksum diferente). "
						+ "Tente de novo; se repetir, a internet pode estar instável.");
			}
		}

		progresso.atualizar("Extraindo o Java " + versao + "...", 85);
		File extraido = new File(temporaria, "extraido");
		extraido.mkdirs();
		if (zip) {
			extrairZip(pacote, extraido);
		} else {
			extrairTarGz(pacote, extraido);
		}
		apagar(pacote);

		File[] filhos = extraido.listFiles(File::isDirectory);
		File raiz = filhos != null && filhos.length == 1 ? filhos[0] : extraido;
		if (acharCasa(raiz) == null) {
			apagar(temporaria);
			throw new IOException("O pacote do Java não tem a pasta bin/java. Tente baixar de novo.");
		}

		File destino = pastaDoJdk(versao);
		apagar(destino);
		try {
			Files.move(raiz.toPath(), destino.toPath(), StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			Files.move(raiz.toPath(), destino.toPath());
		}
		apagar(temporaria);

		String casa = acharCasa(destino);
		liberarExecucao(new File(casa, "bin"));
		Files.write(new File(destino, MARCADOR).toPath(), "ok".getBytes(StandardCharsets.UTF_8));
		progresso.atualizar("Java " + versao + " pronto.", 100);
		return casa;
	}

	// ------------------------------------------------------------------
	// Sistema e arquitetura
	// ------------------------------------------------------------------

	private static String sistemaAdoptium() {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("win")) {
			return "windows";
		}
		if (os.contains("mac") || os.contains("darwin")) {
			return "mac";
		}
		if (new File("/etc/alpine-release").exists()) {
			return "alpine-linux";
		}
		return "linux";
	}

	private static String arquiteturaAdoptium(String sistema, String versao) throws IOException {
		String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
		if ("windows".equals(sistema)) {
			// Um Java de 32 bits em Windows de 64 bits ainda enxerga "x86": confere o sistema de verdade.
			String real = System.getenv("PROCESSOR_ARCHITEW6432");
			if (real == null) {
				real = System.getenv("PROCESSOR_ARCHITECTURE");
			}
			if (real != null) {
				arch = real.toLowerCase(Locale.ROOT);
			}
		}
		if (arch.contains("aarch64") || arch.contains("arm64")) {
			return "aarch64";
		}
		if (arch.contains("amd64") || arch.contains("x86_64") || arch.equals("x64")) {
			return "x64";
		}
		if (arch.equals("x86") || arch.equals("i386") || arch.equals("i686")) {
			if ("windows".equals(sistema) && "8".equals(versao)) {
				return "x32";
			}
			throw new IOException("Este computador é de 32 bits e o Java " + versao
					+ " não existe para essa arquitetura. Só o Java 8 (Minecraft até a 1.16.5) roda aqui.");
		}
		if (arch.contains("arm")) {
			return "arm";
		}
		throw new IOException("Não conheço a arquitetura \"" + arch + "\" para baixar o Java automaticamente.");
	}

	// ------------------------------------------------------------------
	// Extração
	// ------------------------------------------------------------------

	private static void extrairZip(File arquivo, File destino) throws IOException {
		String raiz = destino.getCanonicalPath() + File.separator;
		try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(arquivo)))) {
			ZipEntry entrada;
			byte[] buffer = new byte[32768];
			while ((entrada = zip.getNextEntry()) != null) {
				File alvo = new File(destino, entrada.getName());
				if (!alvo.getCanonicalPath().startsWith(raiz)) {
					throw new IOException("Pacote suspeito: tentou escrever fora da pasta de destino.");
				}
				if (entrada.isDirectory()) {
					alvo.mkdirs();
					continue;
				}
				alvo.getParentFile().mkdirs();
				try (FileOutputStream saida = new FileOutputStream(alvo)) {
					int lido;
					while ((lido = zip.read(buffer)) != -1) {
						saida.write(buffer, 0, lido);
					}
				}
			}
		}
	}

	/**
	 * Leitor de tar.gz próprio: o Java não traz um, e não dá para contar com o comando "tar"
	 * (assim funciona igual em qualquer sistema). Entende nomes longos (GNU e PAX), pastas,
	 * links simbólicos e a permissão de execução dos arquivos.
	 */
	private static void extrairTarGz(File arquivo, File destino) throws IOException {
		String raiz = destino.getCanonicalPath() + File.separator;
		try (InputStream entrada = new GZIPInputStream(new BufferedInputStream(new FileInputStream(arquivo), 65536),
				65536)) {
			byte[] cabecalho = new byte[512];
			String nomeLongo = null;
			String linkLongo = null;
			byte[] buffer = new byte[32768];

			while (lerBloco(entrada, cabecalho)) {
				if (blocoVazio(cabecalho)) {
					break;
				}
				String nome = texto(cabecalho, 0, 100);
				long tamanho = numero(cabecalho, 124, 12);
				int modo = (int) numero(cabecalho, 100, 8);
				char tipo = (char) cabecalho[156];
				String link = texto(cabecalho, 157, 100);
				String prefixo = texto(cabecalho, 345, 155);
				if (!prefixo.isEmpty() && "ustar".equals(texto(cabecalho, 257, 5))) {
					nome = prefixo + "/" + nome;
				}

				if (tipo == 'L' || tipo == 'K') { // nome longo do GNU tar
					String valor = new String(lerBytes(entrada, tamanho), StandardCharsets.UTF_8);
					valor = valor.replace("\0", "");
					if (tipo == 'L') {
						nomeLongo = valor;
					} else {
						linkLongo = valor;
					}
					continue;
				}
				if (tipo == 'x' || tipo == 'g') { // cabeçalho PAX
					String pax = new String(lerBytes(entrada, tamanho), StandardCharsets.UTF_8);
					if (tipo == 'x') {
						for (String linha : pax.split("\n")) {
							int espaco = linha.indexOf(' ');
							int igual = linha.indexOf('=');
							if (espaco > 0 && igual > espaco) {
								String chave = linha.substring(espaco + 1, igual);
								String valor = linha.substring(igual + 1);
								if ("path".equals(chave)) {
									nomeLongo = valor;
								} else if ("linkpath".equals(chave)) {
									linkLongo = valor;
								}
							}
						}
					}
					continue;
				}

				if (nomeLongo != null) {
					nome = nomeLongo;
					nomeLongo = null;
				}
				if (linkLongo != null) {
					link = linkLongo;
					linkLongo = null;
				}

				File alvo = new File(destino, nome);
				if (!alvo.getCanonicalPath().startsWith(raiz) && !alvo.getCanonicalFile().equals(destino.getCanonicalFile())) {
					throw new IOException("Pacote suspeito: tentou escrever fora da pasta de destino.");
				}

				if (tipo == '5') {
					alvo.mkdirs();
				} else if (tipo == '2') { // link simbólico
					alvo.getParentFile().mkdirs();
					try {
						Path caminho = alvo.toPath();
						Files.deleteIfExists(caminho);
						Files.createSymbolicLink(caminho, new File(link).toPath());
					} catch (UnsupportedOperationException | IOException e) {
						// Sem suporte a link (ex.: Windows sem permissão): pula, o JDK funciona sem eles.
					}
				} else if (tipo == '0' || tipo == '\0' || tipo == '7') {
					alvo.getParentFile().mkdirs();
					long restante = tamanho;
					try (FileOutputStream saida = new FileOutputStream(alvo)) {
						while (restante > 0) {
							int lido = entrada.read(buffer, 0, (int) Math.min(buffer.length, restante));
							if (lido < 0) {
								throw new IOException("O pacote do Java terminou antes da hora.");
							}
							saida.write(buffer, 0, lido);
							restante -= lido;
						}
					}
					if ((modo & 0100) != 0) {
						alvo.setExecutable(true, false);
					}
					pularPreenchimento(entrada, tamanho);
					continue;
				}
				// Para os outros tipos o conteúdo (se houver) é só pulado.
				pularBytes(entrada, tamanho);
				pularPreenchimento(entrada, tamanho);
			}
		}
	}

	private static boolean lerBloco(InputStream in, byte[] bloco) throws IOException {
		int total = 0;
		while (total < bloco.length) {
			int lido = in.read(bloco, total, bloco.length - total);
			if (lido < 0) {
				return total > 0 && total == bloco.length;
			}
			total += lido;
		}
		return true;
	}

	private static boolean blocoVazio(byte[] bloco) {
		for (byte b : bloco) {
			if (b != 0) {
				return false;
			}
		}
		return true;
	}

	private static String texto(byte[] b, int inicio, int tamanho) {
		int fim = inicio;
		while (fim < inicio + tamanho && b[fim] != 0) {
			fim++;
		}
		return new String(b, inicio, fim - inicio, StandardCharsets.UTF_8);
	}

	/** Números do tar: octal em texto, ou base 256 quando o primeiro bit está ligado. */
	private static long numero(byte[] b, int inicio, int tamanho) {
		if ((b[inicio] & 0x80) != 0) {
			long valor = b[inicio] & 0x7F;
			for (int i = 1; i < tamanho; i++) {
				valor = (valor << 8) | (b[inicio + i] & 0xFF);
			}
			return valor;
		}
		long valor = 0;
		for (int i = inicio; i < inicio + tamanho; i++) {
			int c = b[i];
			if (c == 0 || c == ' ') {
				if (valor == 0 && c == ' ') {
					continue;
				}
				break;
			}
			if (c < '0' || c > '7') {
				break;
			}
			valor = valor * 8 + (c - '0');
		}
		return valor;
	}

	private static byte[] lerBytes(InputStream in, long tamanho) throws IOException {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		byte[] buffer = new byte[4096];
		long restante = tamanho;
		while (restante > 0) {
			int lido = in.read(buffer, 0, (int) Math.min(buffer.length, restante));
			if (lido < 0) {
				throw new IOException("O pacote do Java terminou antes da hora.");
			}
			saida.write(buffer, 0, lido);
			restante -= lido;
		}
		pularPreenchimento(in, tamanho);
		return saida.toByteArray();
	}

	private static void pularBytes(InputStream in, long quantos) throws IOException {
		byte[] lixo = new byte[8192];
		long restante = quantos;
		while (restante > 0) {
			int lido = in.read(lixo, 0, (int) Math.min(lixo.length, restante));
			if (lido < 0) {
				throw new IOException("O pacote do Java terminou antes da hora.");
			}
			restante -= lido;
		}
	}

	/** Os dados do tar ocupam sempre múltiplos de 512 bytes. */
	private static void pularPreenchimento(InputStream in, long tamanho) throws IOException {
		long resto = tamanho % 512;
		if (resto != 0) {
			pularBytes(in, 512 - resto);
		}
	}

	// ------------------------------------------------------------------
	// Utilitários
	// ------------------------------------------------------------------

	private static String sha256(File arquivo) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		try (InputStream in = new BufferedInputStream(new FileInputStream(arquivo), 65536)) {
			byte[] buffer = new byte[65536];
			int lido;
			while ((lido = in.read(buffer)) != -1) {
				md.update(buffer, 0, lido);
			}
		}
		StringBuilder hex = new StringBuilder();
		for (byte b : md.digest()) {
			hex.append(String.format("%02x", b));
		}
		return hex.toString();
	}

	private static void liberarExecucao(File pastaBin) {
		File[] arquivos = pastaBin.listFiles();
		if (arquivos == null) {
			return;
		}
		for (File f : arquivos) {
			if (f.isFile()) {
				f.setExecutable(true, false);
			}
		}
	}

	private static void apagar(File alvo) {
		if (alvo == null || !alvo.exists()) {
			return;
		}
		if (Files.isSymbolicLink(alvo.toPath())) {
			alvo.delete();
			return;
		}
		File[] filhos = alvo.listFiles();
		if (filhos != null) {
			for (File filho : filhos) {
				apagar(filho);
			}
		}
		alvo.delete();
	}

	/** Pequena ajuda para ler texto de um endereço, usando a mesma conexão do catálogo. */
	private static final class CatalogoVersoesAcesso {
		static String texto(String endereco) throws Exception {
			java.net.HttpURLConnection c = CatalogoVersoes.abrir(endereco);
			try (InputStream in = c.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} finally {
				c.disconnect();
			}
		}
	}
}
