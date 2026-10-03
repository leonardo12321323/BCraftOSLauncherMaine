package BCraftOSproject1.BCraftOS1;



import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Baixa da internet o MDK da versão escolhida e descompacta dentro de versoes/.
 * Nada precisa ser colocado na mão: o launcher cria a pasta e baixa o necessário.
 *
 * Fontes oficiais:
 *  - NeoForge: MDKs oficiais em github.com/NeoForgeMDKs
 *  - Forge:    pacote oficial de fontes em maven.minecraftforge.net
 *  - Fabric:   template oficial em github.com/FabricMC/fabric-example-mod
 */
public class GerenciadorDownloads {

	/** Recebe o andamento do download para a tela mostrar a barra de progresso. */
	public interface Progresso {
		void atualizar(String etapa, int porcentagem);
	}

	private static final String MDK_NEOFORGE = "https://github.com/NeoForgeMDKs/MDK-";
	/** O pacote certo do Forge é o "-mdk.zip": ele já traz o gradlew na raiz do arquivo. */
	private static final String MDK_FORGE = "https://maven.minecraftforge.net/net/minecraftforge/forge/";
	private static final String MDK_FABRIC_INICIO = "https://github.com/FabricMC/fabric-example-mod/archive/refs/heads/";
	private static final String MDK_FABRIC_FIM = ".zip";
	/** O exemplo oficial do Fabric começa na 1.14.4; abaixo disso eles não publicam kit. */
	private static final String FABRIC_MINIMO = "1.14.4";

	/**
	 * Garante que a versão escolhida exista em disco. Se já existir, não baixa nada.
	 *
	 * @return a pasta da versão, pronta para uso
	 */
	public static File garantirVersao(String loader, CatalogoVersoes.ItemVersao versao, Progresso progresso)
			throws Exception {
		File pastaVersao = GerenciadorVersoes.obterPastaDaVersao(loader, versao.codigo);

		if (GerenciadorVersoes.obterPastaMDK(pastaVersao) != null) {
			progresso.atualizar("Versão já instalada.", 100);
			return pastaVersao;
		}

		garantirEspaco(pastaVersao, 300);
		pastaVersao.mkdirs();
		File pastaTemporaria = new File(pastaVersao, ".baixando");
		apagarPasta(pastaTemporaria);
		pastaTemporaria.mkdirs();

		String endereco = montarEndereco(loader, versao);
		File arquivoBaixado = new File(pastaTemporaria, "pacote.zip");

		progresso.atualizar("Baixando " + versao.rotulo + "...", 5);
		baixar(endereco, arquivoBaixado, progresso);

		progresso.atualizar("Descompactando...", 80);
		descompactar(arquivoBaixado, pastaTemporaria);
		apagarPasta(arquivoBaixado);

		File raizExtraida = acharPastaComGradlew(pastaTemporaria);
		if (raizExtraida == null) {
			throw new IOException("O pacote baixado não contém o gradlew. Verifique sua conexão e tente de novo.");
		}

		File destinoFinal = new File(pastaVersao, GerenciadorVersoes.nomePastaMDK(loader, versao.codigo));
		apagarPasta(destinoFinal);
		if (!raizExtraida.renameTo(destinoFinal)) {
			copiarPasta(raizExtraida, destinoFinal);
			apagarPasta(raizExtraida);
		}
		apagarPasta(pastaTemporaria);

		liberarExecucao(destinoFinal);

		progresso.atualizar("Pronto.", 100);
		return pastaVersao;
	}

	/**
	 * O gradlew vem de dentro de um ZIP e no Linux/Mac ele chega sem permissão de execução.
	 * Isso é a causa mais comum de "Permission denied" na hora de iniciar. Resolvemos aqui,
	 * no momento do download, para o problema nunca chegar no botão de iniciar.
	 */
	private static void liberarExecucao(File pastaVersao) {
		File[] scripts = {new File(pastaVersao, "gradlew"), new File(pastaVersao, "gradlew.bat")};
		for (File script : scripts) {
			if (!script.isFile()) {
				continue;
			}
			script.setReadable(true, false);
			script.setExecutable(true, false);
			// Reforço pelo sistema de arquivos, para o caso do setExecutable não pegar.
			try {
				java.util.Set<java.nio.file.attribute.PosixFilePermission> permissoes =
						java.nio.file.Files.getPosixFilePermissions(script.toPath());
				permissoes.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
				permissoes.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
				permissoes.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
				java.nio.file.Files.setPosixFilePermissions(script.toPath(), permissoes);
				System.out.println("[BCraftOS] Permissão de execução liberada para " + script.getName());
			} catch (UnsupportedOperationException | IOException e) {
				// Windows não tem permissão POSIX: nada a fazer, e nada quebra.
			}
		}
	}

	private static String montarEndereco(String loader, CatalogoVersoes.ItemVersao versao) throws IOException {
		switch (loader) {
			case CatalogoVersoes.NEOFORGE:
				return MDK_NEOFORGE + versao.versaoMc + "-ModDevGradle/archive/refs/heads/main.zip";
			case CatalogoVersoes.FORGE:
				// Conferido em várias versões: o "-mdk.zip" existe da 1.8.9 até a 1.21.
				// O "-src.zip" que eu usava antes não existe nesse servidor e causava erro no download.
				return MDK_FORGE + versao.codigo + "/forge-" + versao.codigo + "-mdk.zip";
			case CatalogoVersoes.FABRIC:
				// O exemplo oficial do Fabric não tem branch "main": cada versão tem a sua.
				// Ex.: 1.21.1, 1.21, 1.20.6... Por isso o endereço muda com a versão escolhida.
				if (CatalogoVersoes.comparar(versao.versaoMc, FABRIC_MINIMO) < 0) {
					throw new IOException("O Fabric só tem kit para Minecraft " + FABRIC_MINIMO + " ou mais novo "
							+ "(esse é o exemplo oficial que eles publicam). Para " + versao.versaoMc
							+ ", use o Forge ou o NeoForge.");
				}
				return MDK_FABRIC_INICIO + versao.versaoMc + MDK_FABRIC_FIM;
			default:
				throw new IOException("Loader desconhecido: " + loader + ". Use Forge, Fabric ou NeoForge.");
		}
	}

	private static void baixar(String endereco, File destino, Progresso progresso) throws Exception {
		baixarArquivo(endereco, destino, progresso, 5, 75, "Baixando");
	}

	/** Quantas vezes tenta de novo quando a internet cai no meio do download. */
	private static final int TENTATIVAS = 4;

	/**
	 * Baixa um arquivo grande sem estourar a memória: lê em blocos pequenos e grava direto no disco.
	 * Se a conexão cair, tenta de novo e RETOMA de onde parou (arquivo .part), em vez de recomeçar.
	 * Funciona bem até em PCs com 2 GB de RAM, porque nada fica guardado na memória.
	 *
	 * @param pctInicio e pctFim a faixa da barra de progresso que este download ocupa
	 */
	public static void baixarArquivo(String endereco, File destino, Progresso progresso, int pctInicio,
			int pctFim, String verbo) throws Exception {
		File pasta = destino.getAbsoluteFile().getParentFile();
		pasta.mkdirs();
		File parcial = new File(pasta, destino.getName() + ".part");
		Exception ultimoErro = null;

		for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
			try {
				baixarUmaVez(endereco, parcial, progresso, pctInicio, pctFim, verbo);
				java.nio.file.Files.move(parcial.toPath(), destino.toPath(),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				return;
			} catch (IllegalStateException definitivo) {
				// 404 e similares: tentar de novo não adianta.
				throw new IOException(definitivo.getMessage());
			} catch (IOException falhou) {
				ultimoErro = falhou;
				if (tentativa < TENTATIVAS) {
					progresso.atualizar("A conexão falhou. Tentando de novo (" + (tentativa + 1) + "/"
							+ TENTATIVAS + ")...", pctInicio);
					Thread.sleep(1500L * tentativa);
				}
			}
		}
		throw new IOException("Não consegui baixar " + endereco + " depois de " + TENTATIVAS
				+ " tentativas. Confira a internet e tente de novo. Motivo: "
				+ (ultimoErro == null ? "desconhecido" : ultimoErro.getMessage()));
	}

	private static void baixarUmaVez(String endereco, File parcial, Progresso progresso, int pctInicio,
			int pctFim, String verbo) throws Exception {
		long jaTem = parcial.isFile() ? parcial.length() : 0;
		HttpURLConnection conexao = CatalogoVersoes.abrir(endereco);
		if (jaTem > 0) {
			conexao.setRequestProperty("Range", "bytes=" + jaTem + "-");
		}
		int codigo = conexao.getResponseCode();
		if (codigo == 416) { // o arquivo parcial já estava completo
			conexao.disconnect();
			return;
		}
		if (codigo != HttpURLConnection.HTTP_OK && codigo != HttpURLConnection.HTTP_PARTIAL) {
			conexao.disconnect();
			String msg = "O servidor respondeu " + codigo + " ao baixar " + endereco;
			if (codigo >= 400 && codigo < 500 && codigo != 408 && codigo != 429) {
				throw new IllegalStateException(msg);
			}
			throw new IOException(msg);
		}

		boolean retomou = codigo == HttpURLConnection.HTTP_PARTIAL;
		long inicio = retomou ? jaTem : 0;
		long restante = conexao.getContentLengthLong();
		long total = restante > 0 ? restante + inicio : -1;

		try (InputStream entrada = conexao.getInputStream();
				FileOutputStream saida = new FileOutputStream(parcial, retomou)) {
			byte[] buffer = new byte[32768];
			long baixado = inicio;
			int lido;
			int ultimoPercentual = -1;
			while ((lido = entrada.read(buffer)) != -1) {
				saida.write(buffer, 0, lido);
				baixado += lido;
				if (total > 0) {
					int percentual = pctInicio + (int) ((pctFim - pctInicio) * baixado / total);
					if (percentual != ultimoPercentual) {
						ultimoPercentual = percentual;
						progresso.atualizar(verbo + "... " + (baixado / 1024 / 1024) + " MB de "
								+ (total / 1024 / 1024) + " MB", percentual);
					}
				}
			}
			if (total > 0 && baixado < total) {
				throw new IOException("O download parou no meio (" + baixado + " de " + total + " bytes).");
			}
		} finally {
			conexao.disconnect();
		}
	}

	/** Confere se há espaço livre no disco antes de baixar, para não falhar no meio. */
	public static void garantirEspaco(File pasta, long megabytes) throws IOException {
		File existente = pasta.getAbsoluteFile();
		while (existente != null && !existente.exists()) {
			existente = existente.getParentFile();
		}
		if (existente == null) {
			return;
		}
		long livre = existente.getUsableSpace() / 1024 / 1024;
		if (livre > 0 && livre < megabytes) {
			throw new IOException("Pouco espaço em disco: restam " + livre + " MB e preciso de pelo menos "
					+ megabytes + " MB livres em " + existente + ". Libere espaço e tente de novo.");
		}
	}

	private static void descompactar(File arquivoZip, File destino) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(new java.io.FileInputStream(arquivoZip))) {
			ZipEntry entrada;
			while ((entrada = zip.getNextEntry()) != null) {
				File arquivo = new File(destino, entrada.getName());
				if (!arquivo.getCanonicalPath().startsWith(destino.getCanonicalPath())) {
					throw new IOException("Pacote suspeito: tentou escrever fora da pasta de destino.");
				}
				if (entrada.isDirectory()) {
					arquivo.mkdirs();
					continue;
				}
				arquivo.getParentFile().mkdirs();
				try (FileOutputStream saida = new FileOutputStream(arquivo)) {
					byte[] buffer = new byte[16384];
					int lido;
					while ((lido = zip.read(buffer)) != -1) {
						saida.write(buffer, 0, lido);
					}
				}
			}
		}
	}

	/** Acha a pasta que contém o gradlew, ignorando a pasta de nível extra que vem no ZIP. */
	private static File acharPastaComGradlew(File raiz) {
		if (new File(raiz, "gradlew").exists()) {
			return raiz;
		}
		File[] subpastas = raiz.listFiles(File::isDirectory);
		if (subpastas != null) {
			for (File subpasta : subpastas) {
				if (new File(subpasta, "gradlew").exists()) {
					return subpasta;
				}
			}
		}
		return null;
	}

	private static void apagarPasta(File pasta) {
		if (!pasta.exists()) {
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

	private static void copiarPasta(File origem, File destino) throws IOException {
		destino.mkdirs();
		File[] filhos = origem.listFiles();
		if (filhos == null) {
			return;
		}
		List<File> pendentes = new ArrayList<>();
		for (File filho : filhos) {
			if (filho.isDirectory()) {
				copiarPasta(filho, new File(destino, filho.getName()));
			} else {
				java.nio.file.Files.copy(filho.toPath(), new File(destino, filho.getName()).toPath(),
						java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			}
			pendentes.add(filho);
		}
		for (File filho : pendentes) {
			apagarPasta(filho);
		}
	}
}
