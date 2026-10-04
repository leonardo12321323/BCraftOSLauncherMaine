package BCraftOSproject1.BCraftOS1;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Descobre quanta RAM o computador tem e decide quanto o Gradle e o jogo podem usar.
 *
 * Num PC com 2 GB, o Gradle (que por padrão pega memória à vontade) e o Minecraft
 * brigam pela RAM e o sistema inteiro trava. Aqui tudo sai da RAM real da máquina.
 * Para testar como se fosse outro PC:   java -Dbcraftos.ram.mb=2048 ...
 *
 * Faixas (RAM total): até 2,5 GB = ECONOMICO, até 4,5 GB = BASICO,
 * até 8,5 GB = NORMAL, acima disso = GENEROSO.
 */
public final class PerfilMemoria {

	public enum Faixa { ECONOMICO, BASICO, NORMAL, GENEROSO }

	public final long ramTotalMb;
	public final Faixa faixa;
	/** Memória do jogo (Minecraft), em MB. */
	public final int jogoMinMb;
	public final int jogoMaxMb;
	/** Memória do Gradle, em MB. A primeira execução pede mais (baixa e prepara tudo). */
	public final int gradleMb;
	public final int gradlePrimeiraVezMb;
	/** Tarefas simultâneas do Gradle. */
	public final int trabalhadores;
	/** Flags extras da JVM do jogo. */
	public final List<String> flagsJogo;

	private PerfilMemoria(long ram, Faixa faixa, int jogoMin, int jogoMax, int gradle, int gradlePrimeira,
			int trabalhadores, List<String> flags) {
		this.ramTotalMb = ram;
		this.faixa = faixa;
		this.jogoMinMb = jogoMin;
		this.jogoMaxMb = jogoMax;
		this.gradleMb = gradle;
		this.gradlePrimeiraVezMb = gradlePrimeira;
		this.trabalhadores = trabalhadores;
		this.flagsJogo = flags;
	}

	public boolean economico() {
		return faixa == Faixa.ECONOMICO;
	}

	/** RAM total em MB. Se o sistema não souber informar, assume 4 GB. */
	public static long ramTotalMb() {
		String forcado = System.getProperty("bcraftos.ram.mb");
		if (forcado != null) {
			try {
				long valor = Long.parseLong(forcado.trim());
				if (valor >= 256) {
					return valor;
				}
			} catch (NumberFormatException ignorado) {
				// valor inválido: usa a detecção normal
			}
		}
		try {
			java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
			if (os instanceof com.sun.management.OperatingSystemMXBean) {
				long bytes = ((com.sun.management.OperatingSystemMXBean) os).getTotalMemorySize();
				if (bytes > 0) {
					return bytes / 1024 / 1024;
				}
			}
		} catch (Throwable ignorado) {
			// JVM sem a extensão: cai no padrão
		}
		return 4096;
	}

	public static PerfilMemoria detectar() {
		return paraVersao(null);
	}

	/** Perfil ajustado à versão do Minecraft: as antigas rodam com bem menos memória. */
	public static PerfilMemoria paraVersao(String versaoMc) {
		long ram = ramTotalMb();
		int nucleos = Math.max(1, Runtime.getRuntime().availableProcessors());

		int base;
		if (versaoMc == null || CatalogoVersoes.comparar(versaoMc, "1.17") >= 0) {
			base = 3;
		} else if (CatalogoVersoes.comparar(versaoMc, "1.13") >= 0) {
			base = 2;
		} else {
			base = 1;
		}

		if (ram <= 2560) {
			int[] jogo = {0, 640, 896, 1024};
			List<String> flags = new ArrayList<>();
			flags.add("-XX:+UseSerialGC");
			flags.add("-XX:TieredStopAtLevel=1");
			flags.add("-XX:MinHeapFreeRatio=10");
			flags.add("-XX:MaxHeapFreeRatio=30");
			flags.add("-XX:-UsePerfData");
			flags.add("-Xss512k");
			return new PerfilMemoria(ram, Faixa.ECONOMICO, 256, jogo[base], 384, 768, 1, flags);
		}
		if (ram <= 4608) {
			int[] jogo = {0, 1024, 1536, 2048};
			return new PerfilMemoria(ram, Faixa.BASICO, 512, jogo[base], 512, 1024, 1,
					List.of("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100"));
		}
		if (ram <= 8704) {
			int[] jogo = {0, 1536, 2560, 3072};
			return new PerfilMemoria(ram, Faixa.NORMAL, 512, jogo[base], 768, 1280, Math.min(nucleos, 2),
					List.of("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100"));
		}
		int[] jogo = {0, 2048, 4096, 4096};
		return new PerfilMemoria(ram, Faixa.GENEROSO, 1024, jogo[base], 1024, 1536, Math.min(nucleos, 4),
				List.of("-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100"));
	}

	/** Opções da JVM do Gradle (GRADLE_OPTS e org.gradle.jvmargs). */
	public String opcoesGradle(boolean primeiraExecucao) {
		StringBuilder t = new StringBuilder();
		t.append("-Xmx").append(primeiraExecucao ? gradlePrimeiraVezMb : gradleMb).append('m');
		t.append(" -Dfile.encoding=UTF-8");
		if (economico()) {
			t.append(" -XX:+UseSerialGC -XX:MaxMetaspaceSize=256m");
		}
		return t.toString();
	}

	/** Quantos downloads rodam juntos (o Modo normal baixa centenas de arquivos pequenos). */
	public int downloadsSimultaneos() {
		switch (faixa) {
			case ECONOMICO:
				return 2;
			case BASICO:
				return 3;
			case NORMAL:
				return 4;
			default:
				return 8;
		}
	}

	/** Avisos para mostrar antes de jogar. Vazio quando o PC tem folga. */
	public List<String> avisos() {
		List<String> avisos = new ArrayList<>();
		if (economico()) {
			avisos.add("Seu computador tem pouca RAM (cerca de " + Math.round(ramTotalMb / 1024.0 * 10) / 10.0
					+ " GB). Ativei o modo econômico: o jogo usa até " + jogoMaxMb
					+ " MB e o Gradle roda sem daemon. Feche o navegador e outros programas antes de jogar.");
			avisos.add("Dica: no Windows aumente a memória virtual; no Linux crie um arquivo de swap de 2 GB. "
					+ "A primeira execução de cada versão é a mais pesada e fica bem mais estável com swap.");
		}
		return avisos;
	}

	// ------------------------------------------------------------------
	// Servidores (Paper, Forge, Fabric...): orçamento de RAM e JVM leve
	// ------------------------------------------------------------------

	/**
	 * Quanto da RAM do computador todos os servidores ligados juntos podem usar: 65% da RAM, no
	 * máximo 10 GB. Num PC de 15 GB isso dá cerca de 9,7 GB; o resto fica para o sistema e para o jogo.
	 */
	public static long orcamentoServidoresMb() {
		return Math.max(512, Math.min(10240, ramTotalMb() * 65 / 100));
	}

	/** RAM sugerida para um servidor novo, tirada do orçamento: mods pedem mais que plugins. */
	public static String ramPadraoServidor(boolean mods, boolean proxy) {
		if (proxy) {
			return "512M";
		}
		long orcamento = orcamentoServidoresMb();
		long mb = mods ? Math.max(1024, Math.min(6144, orcamento * 40 / 100))
				: Math.max(1024, Math.min(4096, orcamento * 30 / 100));
		mb = (mb + 255) / 512 * 512; // arredonda para múltiplos de 512 MB
		return mb % 1024 == 0 ? (mb / 1024) + "G" : mb + "M";
	}

	/** Converte "4G" ou "512M" em MB. Valor inválido vira 2048. */
	public static long paraMb(String ram) {
		if (ram == null || !ram.matches("[0-9]{1,5}[MmGg]")) {
			return 2048;
		}
		long n = Long.parseLong(ram.substring(0, ram.length() - 1));
		return Character.toUpperCase(ram.charAt(ram.length() - 1)) == 'G' ? n * 1024 : n;
	}

	/**
	 * Flags da JVM de um servidor: coletor de lixo G1 ajustado para pausas curtas (as flags
	 * conhecidas do Paper/Aikar, sem o AlwaysPreTouch, que reservaria toda a RAM já ao ligar).
	 * Em PC com pouca RAM usa o coletor serial, que é bem mais leve.
	 */
	public static List<String> flagsServidor(boolean proxy) {
		List<String> f = new ArrayList<>();
		if (economicoAgora()) {
			f.add("-XX:+UseSerialGC");
			return f;
		}
		f.add("-XX:+UseG1GC");
		f.add("-XX:+ParallelRefProcEnabled");
		f.add("-XX:MaxGCPauseMillis=200");
		f.add("-XX:+DisableExplicitGC");
		f.add("-XX:+PerfDisableSharedMem");
		if (!proxy) {
			f.add("-XX:+UnlockExperimentalVMOptions");
			f.add("-XX:G1NewSizePercent=30");
			f.add("-XX:G1MaxNewSizePercent=40");
			f.add("-XX:G1HeapRegionSize=8M");
			f.add("-XX:G1ReservePercent=20");
			f.add("-XX:G1HeapWastePercent=5");
			f.add("-XX:G1MixedGCCountTarget=4");
			f.add("-XX:InitiatingHeapOccupancyPercent=15");
			f.add("-XX:G1MixedGCLiveThresholdPercent=90");
			f.add("-XX:G1RSetUpdatingPauseTimePercent=5");
			f.add("-XX:SurvivorRatio=32");
			f.add("-XX:MaxTenuringThreshold=1");
		}
		return f;
	}

	private static boolean economicoAgora() {
		return ramTotalMb() <= 2560;
	}

	/** Limita a RAM pedida para um servidor (ex.: "2G") a ~55% da RAM do computador. */
	public static String limitarRam(String pedida) {
		if (pedida == null || !pedida.matches("[0-9]{1,5}[MmGg]")) {
			return pedida;
		}
		long pedidaMb = Long.parseLong(pedida.substring(0, pedida.length() - 1))
				* (Character.toUpperCase(pedida.charAt(pedida.length() - 1)) == 'G' ? 1024 : 1);
		long teto = Math.max(512, ramTotalMb() * 55 / 100);
		return pedidaMb <= teto ? pedida : teto + "M";
	}

	@Override
	public String toString() {
		return "PerfilMemoria{RAM=" + ramTotalMb + "MB, faixa=" + faixa + ", jogo=" + jogoMinMb + "-" + jogoMaxMb
				+ "MB, gradle=" + gradleMb + "/" + gradlePrimeiraVezMb + "MB}";
	}
}
