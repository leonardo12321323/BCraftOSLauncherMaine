import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * INSTALADOR do BCraftOS.
 *
 * É este arquivo que baixa tudo do seu GitHub. Ele:
 *
 *   1. baixa o projeto inteiro do repositório (arquivo .zip automático do GitHub);
 *   2. procura todos os .java que existem lá dentro, em qualquer pasta;
 *   3. cria as pastas certas (BCraftOS1/, BCraftOS1login/, versoes/, usuarios/);
 *   4. direciona cada .java para a pasta do seu pacote;
 *   5. gera os executores: EXECUTAR.bat (Windows) e executar.sh (Linux/Mac);
 *   6. só termina quando tudo está no lugar — e nunca apaga nada seu.
 *
 * NOTA HONESTA: o pacote `package` escrito dentro de cada .java é que manda na pasta.
 * Se um arquivo diz "package BCraftOSproject1.BCraftOS1;" ele vai para
 * src/BCraftOSproject1/BCraftOS1/. Como os arquivos do seu repositório são os mesmos
 * que você baixa aqui, eles vão cair exatamente nos lugares certos.
 *
 * COMO USAR:
 *   javac -encoding UTF-8 InstaladorBCraftOS.java
 *   java InstaladorBCraftOS
 *
 * Para trocar o repositório, passe o endereço na linha de comando:
 *   java InstaladorBCraftOS https://github.com/seu-usuario/seu-repo
 */
public class InstaladorBCraftOS {

	/** O repositório que o instalador baixa quando ninguém passa outro. */
	private static final String REPOSITORIO_PADRAO =
			"https://github.com/leonardo12321323/BCraftOSLauncherMaine";

	/** Pastas que o launcher usa em tempo de execução. */
	private static final String[] PASTAS_DO_LAUNCHER = {
			"versoes", "versoes/Forge", "versoes/Fabric", "versoes/NeoForge",
			"servidores", "usuarios", "java"
	};

	/** Arquivos .java que compõem o launcher e o pacote de cada um. */
	private static final String[][] ARQUIVOS_CONHECIDOS = {
			{"BCraftOS1.java", "BCraftOSproject1.BCraftOS1"},
			{"CatalogoVersoes.java", "BCraftOSproject1.BCraftOS1"},
			{"GerenciadorDownloads.java", "BCraftOSproject1.BCraftOS1"},
			{"GerenciadorVersoes.java", "BCraftOSproject1.BCraftOS1"},
			{"GerenciadorModpacks.java", "BCraftOSproject1.BCraftOS1"},
			{"MinecraftLauncher.java", "BCraftOSproject1.BCraftOS1"},
			{"ModoNormal.java", "BCraftOSproject1.BCraftOS1"},
			{"MiniJson.java", "BCraftOSproject1.BCraftOS1"},
			{"OrganizadorPastas.java", "BCraftOSproject1.BCraftOS1"},
			{"CatalogoServidores.java", "BCraftOSproject1.BCraftOS1"},
			{"GerenciadorServidores.java", "BCraftOSproject1.BCraftOS1"},
			{"ExecutorServidor.java", "BCraftOSproject1.BCraftOS1"},
			{"ConsoleServidor.java", "BCraftOSproject1.BCraftOS1"},
			{"TelaServidores.java", "BCraftOSproject1.BCraftOS1"},
			{"PerfilMemoria.java", "BCraftOSproject1.BCraftOS1"},
			{"InstaladorJava.java", "BCraftOSproject1.BCraftOS1"},
			{"RegistroLogs.java", "BCraftOSproject1.BCraftOS1"},
			{"BCraftOS1login.java", "BCraftOSproject1.BCraftOS1login"},
			{"InfoUsuarios.java", "BCraftOSproject1.BCraftOS1login"}
	};

	/** Classes que fazem parte do launcher. Usado para saber o que é do projeto. */
	private static final List<String> CLASSES_DO_LAUNCHER = List.of(
			"BCraftOS1", "CatalogoVersoes", "GerenciadorDownloads", "GerenciadorVersoes",
			"GerenciadorModpacks", "MinecraftLauncher", "ModoNormal", "MiniJson", "OrganizadorPastas",
			"CatalogoServidores", "GerenciadorServidores", "ExecutorServidor", "ConsoleServidor",
			"TelaServidores", "PerfilMemoria", "InstaladorJava", "RegistroLogs", "BCraftOS1login", "InfoUsuarios");

	/** Nomes que NUNCA são sobrescritos, porque são de outro sistema ou de teste. */
	private static final List<String> NAO_INSTALAR = List.of(
			"SkinViewer3D", "GerenciadorSkins", "Teste", "Test", "Exemplo", "InstaladorBCraftOS");

	private static int arquivosBaixados;
	private static int arquivosIgnorados;
	private static int arquivosRenomeados;
	private static final List<String> avisos = new ArrayList<>();

	public static void main(String[] args) {
		String repositorio = args.length > 0 ? args[0].trim() : REPOSITORIO_PADRAO;
		if (repositorio.endsWith(".git")) {
			repositorio = repositorio.substring(0, repositorio.length() - 4);
		}
		while (repositorio.endsWith("/")) {
			repositorio = repositorio.substring(0, repositorio.length() - 1);
		}

		File raiz = new File(System.getProperty("user.dir"));
		System.out.println("=================================================");
		System.out.println("        BCraftOS - Instalador automatico");
		System.out.println("=================================================");
		System.out.println("Repositorio : " + repositorio);
		System.out.println("Instalando em: " + raiz.getAbsolutePath());
		System.out.println();

		try {
			String enderecoZip = montarEnderecoZip(repositorio);
			System.out.println("[1/5] Baixando o projeto do GitHub...");
			byte[] pacote = baixarBytes(enderecoZip);
			System.out.println("      Recebido: " + (pacote.length / 1024) + " KB");

			System.out.println("[2/5] Lendo os arquivos .java do projeto...");
			Map<String, byte[]> fontes = lerFontes(pacote);
			if (fontes.isEmpty()) {
				System.out.println();
				System.out.println("Nenhum arquivo .java foi encontrado no repositorio.");
				System.out.println("Confira se o repositorio e publico e se os arquivos foram enviados.");
				return;
			}
			System.out.println("      Encontrados: " + fontes.size() + " arquivo(s) .java");

			System.out.println("[3/5] Organizando as pastas do launcher...");
			criarPastas(raiz);

			System.out.println("[4/5] Colocando cada arquivo no lugar certo...");
			distribuir(raiz, fontes);

			System.out.println("[5/5] Gerando os executores...");
			escreverExecutores(raiz);

			escreverGuia(raiz);
			resumo(raiz, repositorio);
		} catch (Exception erro) {
			System.out.println();
			System.out.println("NAO CONSEGUI TERMINAR: " + erro.getMessage());
			System.out.println();
			System.out.println("O que fazer:");
			System.out.println(" - Confira se o repositorio e publico:");
			System.out.println("   " + repositorio);
			System.out.println(" - Confira sua internet.");
			System.out.println(" - Se o repositorio tiver outro nome, rode assim:");
			System.out.println("   java InstaladorBCraftOS https://github.com/seu-usuario/seu-repo");
		}
	}

	// ------------------------------------------------------------------
	// Download
	// ------------------------------------------------------------------

	private static String montarEnderecoZip(String repositorio) {
		// O GitHub NÃO aceita o ".git" no meio do caminho do arquivo .zip:
		// ".../repo.git/archive/..." responde 404. Por isso ele sai daqui.
		String base = repositorio;
		if (base.endsWith(".git")) {
			base = base.substring(0, base.length() - 4);
		}
		return base + "/archive/refs/heads/main.zip";
	}

	/**
	 * Baixa o pacote. Se a internet redirecionar (o GitHub redireciona), segue o caminho.
	 * Se o repositorio usar "master" em vez de "main", tenta as duas opcoes.
	 */
	private static byte[] baixarBytes(String endereco) throws Exception {
		try {
			return baixarBytesDireto(endereco);
		} catch (IOException primeira) {
			if (endereco.endsWith("/main.zip")) {
				String alternativa = endereco.substring(0, endereco.length() - "/main.zip".length()) + "/master.zip";
				System.out.println("      Branch 'main' nao respondeu. Tentando 'master'...");
				return baixarBytesDireto(alternativa);
			}
			throw primeira;
		}
	}

	private static byte[] baixarBytesDireto(String endereco) throws Exception {
		String atual = endereco;
		for (int salto = 0; salto < 5; salto++) {
			HttpURLConnection conexao = abrir(atual);
			int codigo = conexao.getResponseCode();

			if (codigo == HttpURLConnection.HTTP_MOVED_PERM || codigo == HttpURLConnection.HTTP_MOVED_TEMP
					|| codigo == 307 || codigo == 308) {
				String destino = conexao.getHeaderField("Location");
				conexao.disconnect();
				if (destino == null || destino.isBlank()) {
					throw new IOException("O GitHub respondeu " + codigo + " sem dizer para onde ir.");
				}
				atual = destino;
				continue;
			}
			if (codigo == HttpURLConnection.HTTP_NOT_FOUND) {
				conexao.disconnect();
				throw new IOException("O GitHub respondeu 404: repositorio nao encontrado ou privado.");
			}
			if (codigo != HttpURLConnection.HTTP_OK) {
				conexao.disconnect();
				throw new IOException("O GitHub respondeu " + codigo + " ao baixar o projeto.");
			}

			try (InputStream entrada = new BufferedInputStream(conexao.getInputStream());
					ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[16384];
				int lido;
				while ((lido = entrada.read(buffer)) != -1) {
					saida.write(buffer, 0, lido);
				}
				return saida.toByteArray();
			} finally {
				conexao.disconnect();
			}
		}
		throw new IOException("Muitos redirecionamentos seguidos. Confira o endereco do repositorio.");
	}

	private static HttpURLConnection abrir(String endereco) throws Exception {
		URL url = new URI(endereco).toURL();
		HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
		conexao.setConnectTimeout(30000);
		conexao.setReadTimeout(60000);
		conexao.setRequestProperty("User-Agent", "BCraftOS-Instalador");
		conexao.setInstanceFollowRedirects(false);
		return conexao;
	}

	/** Lê o ZIP e devolve só os .java, com o nome do arquivo (sem repetir caminho). */
	private static Map<String, byte[]> lerFontes(byte[] pacote) throws IOException {
		Map<String, byte[]> fontes = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(pacote))) {
			ZipEntry entrada;
			while ((entrada = zip.getNextEntry()) != null) {
				if (entrada.isDirectory()) {
					continue;
				}
				String nome = entrada.getName();
				int barra = nome.lastIndexOf('/');
				String simples = barra < 0 ? nome : nome.substring(barra + 1);
				if (!simples.toLowerCase(Locale.ROOT).endsWith(".java")) {
					continue;
				}
				if (simples.contains("/")) {
					continue;
				}
				fontes.put(simples, lerTudo(zip));
			}
		}
		return fontes;
	}

	private static byte[] lerTudo(InputStream entrada) throws IOException {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		byte[] buffer = new byte[16384];
		int lido;
		while ((lido = entrada.read(buffer)) != -1) {
			saida.write(buffer, 0, lido);
		}
		return saida.toByteArray();
	}

	// ------------------------------------------------------------------
	// Pastas e distribuição dos arquivos
	// ------------------------------------------------------------------

	private static void criarPastas(File raiz) {
		String[] pastas = {
				"src", "src/BCraftOSproject1", "src/BCraftOSproject1/BCraftOS1",
				"src/BCraftOSproject1/BCraftOS1login"
		};
		for (String pasta : pastas) {
			File destino = new File(raiz, pasta);
			if (!destino.isDirectory() && destino.mkdirs()) {
				System.out.println("      Criada: " + pasta + "/");
			}
		}
		for (String pasta : PASTAS_DO_LAUNCHER) {
			File destino = new File(raiz, pasta);
			if (!destino.isDirectory() && destino.mkdirs()) {
				System.out.println("      Criada: " + pasta + "/");
			}
		}
	}

	private static void distribuir(File raiz, Map<String, byte[]> fontes) throws IOException {
		Map<String, String> porNome = new LinkedHashMap<>();
		for (String[] conhecido : ARQUIVOS_CONHECIDOS) {
			porNome.put(conhecido[0], conhecido[1]);
		}

		List<String> jaColocados = new ArrayList<>();

		// 1) Os arquivos conhecidos primeiro: vão sempre para a pasta certa.
		for (Map.Entry<String, String> item : porNome.entrySet()) {
			String arquivo = item.getKey();
			byte[] conteudo = null;
			String nomeReal = arquivo;

			for (Map.Entry<String, byte[]> fonte : fontes.entrySet()) {
				if (fonte.getKey().equalsIgnoreCase(arquivo)
						|| limparNome(fonte.getKey()).equalsIgnoreCase(arquivo)) {
					conteudo = fonte.getValue();
					nomeReal = limparNome(fonte.getKey());
					break;
				}
			}
			if (conteudo == null) {
				avisos.add("Nao achei " + arquivo + " no repositorio: ele nao foi instalado.");
				arquivosIgnorados++;
				continue;
			}

			String pacoteDeclarado = lerPacote(conteudo);
			File pasta = pacoteDeclarado != null
					? pastaDoPacote(raiz, pacoteDeclarado)
					: new File(new File(raiz, "src/BCraftOSproject1"), ultimoTrecho(item.getValue()));

			File destino = new File(pasta, arquivo);
			gravar(destino, conteudo);
			if (!nomeReal.equals(fonteReal(arquivo))) {
				arquivosRenomeados++;
			}
			jaColocados.add(arquivo);
			System.out.println("      " + nomeReal + "  ->  " + caminhoCurto(raiz, destino));
		}

		// 2) Qualquer outro .java que o usuário tenha criado vai para a pasta certa também.
		for (Map.Entry<String, byte[]> fonte : fontes.entrySet()) {
			String limpo = limparNome(fonte.getKey());
			if (jaColocados.contains(limpo) || jaColocados.contains(fonte.getKey())) {
				continue;
			}
			String semExtensao = limpo.substring(0, limpo.length() - ".java".length());
			if (NAO_INSTALAR.contains(semExtensao) || CLASSES_DO_LAUNCHER.contains(semExtensao)) {
				continue;
			}

			String pacoteDeclarado = lerPacote(fonte.getValue());
			File pasta;
			if (pacoteDeclarado != null) {
				pasta = pastaDoPacote(raiz, pacoteDeclarado);
			} else {
				// Sem "package" no arquivo: usa a pasta onde ele estava no repositório.
				pasta = new File(raiz, "src/BCraftOSproject1");
				avisos.add(limpo + " nao tem a linha 'package'. Coloquei em src/BCraftOSproject1, "
						+ "mas ele pode precisar de ajuste.");
			}
			File destino = new File(pasta, limpo);
			gravar(destino, fonte.getValue());
			if (!limpo.equals(fonte.getKey())) {
				arquivosRenomeados++;
			}
			arquivosBaixados++;
			System.out.println("      " + fonte.getKey() + "  ->  " + caminhoCurto(raiz, destino));
		}

		arquivosBaixados += jaColocados.size();

		// 3) Sobras: se a pessoa ainda tem os arquivos antigos de skin, avisa (sem apagar nada).
		File pastaAntigaSkins = new File(raiz, "src/BCraftOSproject1/BCraftOS1/SkinViewer3D.java");
		if (pastaAntigaSkins.isFile()) {
			avisos.add("SkinViewer3D.java ainda existe na pasta. Ele nao e mais usado e pode ser apagado.");
		}
	}

	/** Lê a linha "package ..." do arquivo, que diz em qual pasta ele deve ficar. */
	private static String lerPacote(byte[] conteudo) {
		String texto = new String(conteudo, StandardCharsets.UTF_8);
		for (String linha : texto.split("\\R")) {
			String semEspaco = linha.trim();
			if (semEspaco.startsWith("package ")) {
				String pacote = semEspaco.substring("package ".length()).trim();
				if (pacote.endsWith(";")) {
					pacote = pacote.substring(0, pacote.length() - 1);
				}
				return pacote.trim();
			}
			if (semEspaco.startsWith("import ") || semEspaco.startsWith("public ")
					|| semEspaco.startsWith("class ")) {
				break;
			}
		}
		return null;
	}

	private static File pastaDoPacote(File raiz, String pacote) {
		return new File(new File(raiz, "src"), pacote.replace('.', File.separatorChar));
	}

	private static String ultimoTrecho(String pacote) {
		int ponto = pacote.lastIndexOf('.');
		return ponto < 0 ? pacote : pacote.substring(ponto + 1);
	}

	private static String caminhoCurto(File raiz, File arquivo) {
		String caminhoRaiz = raiz.getAbsolutePath();
		String caminhoArquivo = arquivo.getAbsolutePath();
		if (caminhoArquivo.startsWith(caminhoRaiz)) {
			return caminhoArquivo.substring(caminhoRaiz.length() + 1);
		}
		return caminhoArquivo;
	}

	private static String fonteReal(String arquivo) {
		return arquivo;
	}

	/** "BCraftOS1(1).java" e "BCraftOS1 - Copia.java" viram "BCraftOS1.java". */
	private static String limparNome(String nome) {
		if (!nome.toLowerCase(Locale.ROOT).endsWith(".java")) {
			return nome;
		}
		String base = nome.substring(0, nome.length() - ".java".length());
		String limpa = base;

		if (limpa.endsWith(" - Copia")) {
			limpa = limpa.substring(0, limpa.length() - " - Copia".length());
		}
		while (limpa.endsWith(")")) {
			int abre = limpa.lastIndexOf('(');
			if (abre <= 0) {
				break;
			}
			String dentro = limpa.substring(abre + 1, limpa.length() - 1).trim();
			boolean numerico = !dentro.isEmpty() && dentro.chars().allMatch(Character::isDigit);
			boolean copia = dentro.equalsIgnoreCase("copia") || dentro.equalsIgnoreCase("copy");
			if (!numerico && !copia) {
				break;
			}
			limpa = limpa.substring(0, abre).trim();
		}
		if (limpa.equals(base) || limpa.isEmpty()) {
			return nome;
		}
		return limpa + ".java";
	}

	private static void gravar(File destino, byte[] conteudo) throws IOException {
		File pasta = destino.getParentFile();
		if (pasta != null && !pasta.isDirectory()) {
			pasta.mkdirs();
		}
		if (destino.isFile()) {
			byte[] antigo = Files.readAllBytes(destino.toPath());
			if (java.util.Arrays.equals(antigo, conteudo)) {
				return; // igual: não faz nada
			}
		}
		try (FileOutputStream saida = new FileOutputStream(destino)) {
			saida.write(conteudo);
		}
	}

	// ------------------------------------------------------------------
	// Executores
	// ------------------------------------------------------------------

	private static void escreverExecutores(File raiz) throws IOException {
		escreverBat(raiz);
		escreverSh(raiz);
	}

	/**
	 * EXECUTAR.bat — usado no Windows.
	 * Procura o Java, compila o launcher e abre. Se faltar Java, explica e para com calma.
	 */
	private static void escreverBat(File raiz) throws IOException {
		String bruto = String.join("\r\n",
				"@echo off",
				"setlocal enabledelayedexpansion",
				"chcp 65001 >nul 2>&1",
				"title BCraftOS Launcher",
				"cd /d \"%~dp0\"",
				"",
				"echo =================================================",
				"echo            BCraftOS - Abrindo o launcher",
				"echo =================================================",
				"echo.",
				"",
				"where javac >nul 2>&1",
				"if errorlevel 1 goto SEM_JAVA",
				"where java >nul 2>&1",
				"if errorlevel 1 goto SEM_JAVA",
				"",
				"echo [1/3] Compilando os arquivos...",
				"if not exist \"saida\" mkdir \"saida\"",
				"set FONTES=",
				"for /r \"src\" %%f in (*.java) do set FONTES=!FONTES! \"%%f\"",
				"if \"!FONTES!\"==\"\" goto SEM_FONTES",
				"",
				"javac -encoding UTF-8 -d \"saida\" !FONTES!",
				"if errorlevel 1 goto ERRO_COMPILAR",
				"echo       Compilado sem erros.",
				"echo.",
				"",
				"echo [2/3] Preparando as pastas...",
				"if not exist \"versoes\" mkdir \"versoes\"",
				"if not exist \"usuarios\" mkdir \"usuarios\"",
				"echo       Pastas prontas.",
				"echo.",
				"",
				"echo [3/3] Abrindo o BCraftOS...",
				"echo.",
				"java -cp \"saida\" BCraftOSproject1.BCraftOS1login.BCraftOS1login",
				"if errorlevel 1 goto ERRO_ABRIR",
				"",
				"echo.",
				"echo Launcher fechado. Ate a proxima!",
				"pause",
				"exit /b 0",
				"",
				":SEM_JAVA",
				"echo.",
				"echo NAO ACHEI O JAVA neste computador.",
				"echo.",
				"echo O launcher precisa do Java Development Kit (JDK) 17 ou mais novo.",
				"echo Baixe de graca aqui: https://adoptium.net/temurin/releases/",
				"echo Na instalacao, marque a opcao \"Add to PATH\".",
				"echo Depois de instalar, feche esta janela e abra de novo.",
				"pause",
				"exit /b 1",
				"",
				":SEM_FONTES",
				"echo.",
				"echo NAO ACHEI os arquivos .java na pasta src.",
				"echo.",
				"echo Se eles nao estiverem ai, rode primeiro o instalador:",
				"echo    java InstaladorBCraftOS",
				"pause",
				"exit /b 1",
				"",
				":ERRO_COMPILAR",
				"echo.",
				"echo DEU ERRO AO COMPILAR. As mensagens estao logo acima.",
				"echo.",
				"echo O motivo mais comum e um arquivo repetido do tipo BCraftOS1(1).java.",
				"echo Nesse caso apague o arquivo com o (1) no nome e rode de novo.",
				"pause",
				"exit /b 1",
				"",
				":ERRO_ABRIR",
				"echo.",
				"echo O launcher fechou com erro. As mensagens estao logo acima.",
				"pause",
				"exit /b 1",
				"");
		gravar(new File(raiz, "EXECUTAR.bat"), bruto.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * executar.sh — usado no Linux e no Mac.
	 * Procura o Java, libera a permissão, compila e abre.
	 */
	private static void escreverSh(File raiz) throws IOException {
		String bruto = String.join("\n",
				"#!/usr/bin/env bash",
				"# BCraftOS - abre o launcher no Linux e no Mac.",
				"# Se der erro de permissao, rode uma vez:  chmod +x executar.sh",
				"",
				"cd \"$(cd \"$(dirname \"$0\")\" && pwd)\" || exit 1",
				"",
				"echo \"=================================================\"",
				"echo \"           BCraftOS - Abrindo o launcher\"",
				"echo \"=================================================\"",
				"echo",
				"",
				"if ! command -v javac >/dev/null 2>&1; then",
				"  echo \"NAO ACHEI O COMPILADOR JAVA (javac) neste computador.\"",
				"  echo",
				"  echo \"Instale o JDK 17 ou mais novo:\"",
				"  echo \"  Ubuntu/Debian : sudo apt install openjdk-17-jdk\"",
				"  echo \"  Fedora        : sudo dnf install java-17-openjdk-devel\"",
				"  echo \"  Arch          : sudo pacman -S jdk17-openjdk\"",
				"  echo \"  Mac           : brew install openjdk@17\"",
				"  exit 1",
				"fi",
				"",
				"echo \"[1/3] Compilando os arquivos...\"",
				"mkdir -p saida",
				"mapfile -t ARQUIVOS < <(find src -name '*.java' 2>/dev/null)",
				"if [ \"${#ARQUIVOS[@]}\" -eq 0 ]; then",
				"  echo",
				"  echo \"NAO ACHEI os arquivos .java na pasta src.\"",
				"  echo \"Rode primeiro o instalador:  java InstaladorBCraftOS\"",
				"  exit 1",
				"fi",
				"",
				"javac -encoding UTF-8 -d saida \"${ARQUIVOS[@]}\"",
				"if [ $? -ne 0 ]; then",
				"  echo",
				"  echo \"DEU ERRO AO COMPILAR. As mensagens estao logo acima.\"",
				"  echo \"Motivo mais comum: arquivo repetido do tipo BCraftOS1(1).java. Apague e tente de novo.\"",
				"  exit 1",
				"fi",
				"echo \"      Compilado sem erros.\"",
				"echo",
				"",
				"echo \"[2/3] Preparando as pastas...\"",
				"mkdir -p versoes usuarios",
				"echo \"      Pastas prontas.\"",
				"echo",
				"",
				"echo \"[3/3] Abrindo o BCraftOS...\"",
				"echo",
				"java -cp saida BCraftOSproject1.BCraftOS1login.BCraftOS1login",
				"",
				"echo",
				"echo \"Launcher fechado. Ate a proxima!\"",
				"");
		File script = new File(raiz, "executar.sh");
		gravar(script, bruto.getBytes(StandardCharsets.UTF_8));
		script.setReadable(true, false);
		script.setExecutable(true, false);
		try {
			java.util.Set<java.nio.file.attribute.PosixFilePermission> permissoes =
					Files.getPosixFilePermissions(script.toPath());
			permissoes.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
			permissoes.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
			permissoes.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
			Files.setPosixFilePermissions(script.toPath(), permissoes);
		} catch (UnsupportedOperationException | IOException e) {
			// Windows nao tem permissao POSIX: nada a fazer.
		}
	}

	// ------------------------------------------------------------------
	// Guia e resumo
	// ------------------------------------------------------------------

	private static void escreverGuia(File raiz) throws IOException {
		String texto = String.join(System.lineSeparator(),
				"BCraftOS - COMO USAR",
				"",
				"ABRIR O LAUNCHER",
				"  Windows ....... clique duas vezes em EXECUTAR.bat",
				"  Linux / Mac ... clique duas vezes em executar.sh (ou rode ./executar.sh)",
				"",
				"O executor compila os arquivos e abre o launcher. Nao precisa fazer mais nada.",
				"",
				"PASTAS (todas criadas automaticamente)",
				"  src/BCraftOSproject1/BCraftOS1/        arquivos do launcher",
				"  src/BCraftOSproject1/BCraftOS1login/   tela de login",
				"  saida/                                 compilado (pode apagar quando quiser)",
				"  versoes/<Loader>/<versao>/             versoes da conta Bw1_1Bw (a conta dona)",
				"  versoes/contas/<Conta>/<Loader>/<versao>/   versoes das outras contas (cada uma so ve as suas)",
				"  <pasta da versao>/modpacks/            seus mods (.jar)",
				"  cache-normal/                          arquivos do Minecraft/Forge do Modo normal (compartilhado)",
				"  servidores/<Tipo>/<versao>/<Nome>/     servidores da conta Bw1_1Bw (mundo, plugins ou mods)",
				"  servidores/contas/<Conta>/...          servidores das outras contas (cada uma so ve os seus)",
				"  usuarios/                              suas contas",
				"",
				"MODO NORMAL (mods de jogo e ghost clients)",
				"  No menu, marque \"Modo normal\" (so aparece com Forge ate a 1.12.2).",
				"  A primeira vez baixa o Minecraft e o Forge e precisa de internet.",
				"",
				"CRIAR SERVIDOR",
				"  No menu, clique na aba \"Servidor\". Escolha o tipo (Paper, Purpur, Spigot, Vanilla,",
				"  Forge, NeoForge, Fabric ou Velocity) e a versao, de um nome e clique em CRIAR SERVIDOR.",
				"  E preciso marcar que aceita o EULA do Minecraft. O launcher baixa e instala tudo sozinho.",
				"  Plugins vao na pasta plugins/ do servidor e mods na pasta mods/ (botao Abrir pasta).",
				"  O Spigot compila na hora: precisa do git no Linux/Mac e leva alguns minutos.",
				"",
				"SE O WINDOWS RECLAMAR DO ARQUIVO .bat",
				"  Clique com o botao direito em EXECUTAR.bat, Propriedades,",
				"  e marque \"Desbloquear\". Depois abra de novo.",
				"",
				"SE DER ERRO DE JAVA",
				"  Instale o JDK 17 ou mais novo em https://adoptium.net/temurin/releases/",
				"  e marque \"Add to PATH\" na instalacao.",
				"",
				"PRECISA BAIXAR O PROJETO DE NOVO?",
				"  java InstaladorBCraftOS");
		gravar(new File(raiz, "COMO-USAR.txt"), texto.getBytes(StandardCharsets.UTF_8));
	}

	private static void resumo(File raiz, String repositorio) {
		System.out.println();
		System.out.println("=================================================");
		System.out.println("                 TUDO PRONTO!");
		System.out.println("=================================================");
		System.out.println("Arquivos instalados .....: " + arquivosBaixados);
		System.out.println("Renomeados (nome repetido): " + arquivosRenomeados);
		System.out.println("Nao encontrados .........: " + arquivosIgnorados);
		System.out.println();
		System.out.println("Para abrir o launcher:");
		System.out.println("  Windows ...... clique duas vezes em EXECUTAR.bat");
		System.out.println("  Linux / Mac .. clique duas vezes em executar.sh");
		System.out.println();
		System.out.println("Pasta do projeto: " + raiz.getAbsolutePath());
		if (!avisos.isEmpty()) {
			System.out.println();
			System.out.println("Avisos:");
			for (String aviso : avisos) {
				System.out.println(" - " + aviso);
			}
		}
		System.out.println();
		System.out.println("Repositorio usado: " + repositorio);
	}
}
