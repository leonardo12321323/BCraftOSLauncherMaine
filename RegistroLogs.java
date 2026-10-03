package BCraftOSproject1.BCraftOS1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Guarda tudo o que o launcher escreve no console em arquivos dentro da pasta logs/.
 *
 *   logs/launcher-2026-10-03_07-01-44.log   <- uma execução do launcher (o console inteiro)
 *   logs/compilacao.log                      <- escrito pelos executores (erros de compilação)
 *
 * O console continua mostrando tudo como antes: o log é uma cópia. Assim, com a janela
 * fechada ou escondida, dá para abrir o arquivo e ver exatamente o que aconteceu.
 * Ficam guardados os últimos 15 logs; os mais antigos são apagados sozinhos.
 */
public final class RegistroLogs {

	private static final int LOGS_GUARDADOS = 15;
	private static boolean iniciado;
	private static File arquivoAtual;

	private RegistroLogs() {
	}

	public static File pasta() {
		return new File(System.getProperty("user.dir"), "logs");
	}

	/** O log desta execução (ou null se não foi possível criar). */
	public static File arquivoAtual() {
		return arquivoAtual;
	}

	/** Liga a cópia do console para o arquivo. Pode chamar várias vezes: só age na primeira. */
	public static synchronized void iniciar() {
		if (iniciado) {
			return;
		}
		iniciado = true;
		try {
			File pasta = pasta();
			if (!pasta.isDirectory() && !pasta.mkdirs()) {
				return;
			}
			limparAntigos(pasta);
			String carimbo = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
			arquivoAtual = new File(pasta, "launcher-" + carimbo + ".log");
			OutputStream arquivo = new FileOutputStream(arquivoAtual, true);

			PrintStream saidaOriginal = System.out;
			PrintStream erroOriginal = System.err;
			System.setOut(new PrintStream(new Copia(saidaOriginal, arquivo), true, StandardCharsets.UTF_8));
			System.setErr(new PrintStream(new Copia(erroOriginal, arquivo), true, StandardCharsets.UTF_8));
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					arquivo.flush();
					arquivo.close();
				} catch (IOException ignorado) {
					// fechando mesmo
				}
			}));
			System.out.println("[BCraftOS] Log desta execução: " + arquivoAtual.getAbsolutePath());
			System.out.println("[BCraftOS] DICA: não clique dentro desta janela preta. No Windows isso PAUSA tudo.");
			System.out.println("[BCraftOS]       Se parecer travado, aperte Enter nesta janela (ou veja o log acima).");
		} catch (Exception e) {
			// Sem log não é motivo para o launcher deixar de abrir.
			System.err.println("[BCraftOS Aviso] Não consegui criar o arquivo de log: " + e.getMessage());
		}
	}

	/**
	 * Lê a saída de um processo (o Gradle ou o jogo) e manda para o console e para o log,
	 * linha por linha. Roda numa thread própria e termina quando o processo fecha a saída.
	 */
	public static Thread acompanhar(Process processo, String nome) {
		Thread t = new Thread(() -> {
			try (InputStream in = processo.getInputStream()) {
				byte[] buffer = new byte[8192];
				int lido;
				while ((lido = in.read(buffer)) != -1) {
					System.out.write(buffer, 0, lido);
					System.out.flush();
				}
			} catch (IOException e) {
				// o processo terminou: fim normal da leitura
			}
		}, "log-" + nome);
		t.setDaemon(true);
		t.start();
		return t;
	}

	private static void limparAntigos(File pasta) {
		File[] logs = pasta.listFiles((d, n) -> n.startsWith("launcher-") && n.endsWith(".log"));
		if (logs == null || logs.length < LOGS_GUARDADOS) {
			return;
		}
		Arrays.sort(logs, Comparator.comparingLong(File::lastModified));
		for (int i = 0; i < logs.length - (LOGS_GUARDADOS - 1); i++) {
			logs[i].delete();
		}
	}

	/** Escreve nos dois lugares ao mesmo tempo. */
	private static final class Copia extends OutputStream {
		private final OutputStream principal;
		private final OutputStream copia;

		Copia(OutputStream principal, OutputStream copia) {
			this.principal = principal;
			this.copia = copia;
		}

		@Override
		public void write(int b) throws IOException {
			principal.write(b);
			copia.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			principal.write(b, off, len);
			copia.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			principal.flush();
			copia.flush();
		}
	}
}
