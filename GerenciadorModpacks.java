package BCraftOSproject1.BCraftOS1;



import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/**
 * Cuida da pasta "modpacks" DENTRO da pasta de uma versão específica
 * (ex: versoes/1.21.1-NeoForge/modpacks). Cada versão tem sua própria lista de modpacks.
 */
public class GerenciadorModpacks {

	private static final String NOME_PASTA_MODPACKS = "modpacks";
	private static final String MODPACK_PADRAO = "Sem-Mods";

	public static void configurarDiretoriosIniciais(File pastaVersao) {
		File pastaModpacks = obterPastaModpacks(pastaVersao);

		if (!pastaModpacks.exists()) {
			pastaModpacks.mkdirs();
		}

		File[] existentes = pastaModpacks.listFiles(File::isDirectory);
		if (existentes == null || existentes.length == 0) {
			File pastaPadrao = new File(pastaModpacks, MODPACK_PADRAO);
			if (pastaPadrao.mkdirs()) {
				System.out.println("[BCraftOS] Modpack padrão criado automaticamente: " + MODPACK_PADRAO);
			}
		}
	}

	public static File obterPastaModpacks(File pastaVersao) {
		return new File(pastaVersao, NOME_PASTA_MODPACKS);
	}

	public static String[] listarModpacksDisponiveis(File pastaVersao) {
		File pastaModpacks = obterPastaModpacks(pastaVersao);
		File[] subpastas = pastaModpacks.listFiles(File::isDirectory);

		if (subpastas == null || subpastas.length == 0) {
			return new String[]{"Nenhum modpack encontrado"};
		}

		String[] nomes = new String[subpastas.length];
		for (int i = 0; i < subpastas.length; i++) {
			nomes[i] = subpastas[i].getName();
		}

		Arrays.sort(nomes);
		return nomes;
	}

	public static void aplicarModpack(File pastaVersao, File pastaMDK, String nomeModpack) throws IOException {
		File pastaOrigem = new File(obterPastaModpacks(pastaVersao), nomeModpack);
		File pastaDestinoMods = new File(pastaMDK, "run/mods");

		System.out.println("[BCraftOS] Preparando a transição de arquivos para: " + nomeModpack);

		if (pastaDestinoMods.exists()) {
			File[] arquivosAntigos = pastaDestinoMods.listFiles();
			if (arquivosAntigos != null) {
				for (File f : arquivosAntigos) {
					f.delete();
				}
			}
		} else {
			pastaDestinoMods.mkdirs();
		}

		File[] novosMods = pastaOrigem.listFiles((dir, name) -> name.endsWith(".jar"));
		if (novosMods != null && novosMods.length > 0) {
			for (File mod : novosMods) {
				Files.copy(mod.toPath(), new File(pastaDestinoMods, mod.getName()).toPath(),
						StandardCopyOption.REPLACE_EXISTING);
			}
			System.out.println("[BCraftOS] " + novosMods.length + " mods injetados com sucesso.");
		} else {
			System.out.println("[BCraftOS Aviso] Nenhum arquivo .jar foi encontrado na pasta deste modpack (modo vanilla).");
		}
	}
}
