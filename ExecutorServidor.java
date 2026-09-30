package BCraftOSproject1.BCraftOS1;



import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Liga, comanda e desliga os servidores criados pelo GerenciadorServidores.
 *
 * Igual ao MinecraftLauncher, faz uma checagem antes de iniciar e devolve o MOTIVO real
 * quando não dá (Java que falta, porta ocupada, arquivo sumido) em vez de um erro seco.
 * A saída do servidor é entregue linha a linha para o console da tela.
 *
 * Se o launcher for fechado com servidores ligados, eles recebem "stop" e têm um tempo
 * para salvar o mundo antes de serem encerrados.
 */
public class ExecutorServidor {

	/** Quem quer acompanhar o servidor (o console da tela). */
	public interface Saida {
		void linha(String texto);

		void terminou(int codigoSaida);
	}

	private static class Processo {
		final Process processo;
		final BufferedWriter entrada;
		final boolean proxy;

		Processo(Process processo, boolean proxy) {
			this.processo = processo;
			this.proxy = proxy;
			this.entrada = new BufferedWriter(
					new OutputStreamWriter(processo.getOutputStream(), StandardCharsets.UTF_8));
		}
	}

	private static final Map<File, Processo> EM_EXECUCAO = new ConcurrentHashMap<>();
	private static boolean ganchoRegistrado;

	public static boolean estaRodando(GerenciadorServidores.Servidor servidor) {
		Processo p = EM_EXECUCAO.get(servidor.pasta.getAbsoluteFile());
		return p != null && p.processo.isAlive();
	}

	// ------------------------------------------------------------------
	// Java
	// ------------------------------------------------------------------

	/**
	 * Acha o executável do Java certo para o servidor. As versões do Minecraft seguem a mesma
	 * regra do cliente; o Velocity (proxy) aceita o Java 21 ou o 17.
	 *
	 * @throws IllegalStateException com o motivo real quando o Java não está instalado
	 */
	public static File executavelJava(String tipo, String versaoMc) {
		boolean proxy = CatalogoServidores.ehProxy(tipo);
		List<String> aceitas = proxy ? List.of("21", "17") : List.of(CatalogoServidores.javaNecessarioPara(versaoMc));

		for (String versao : aceitas) {
			String casa = MinecraftLauncher.procurarJava(versao);
			if (casa == null) {
				continue;
			}
			File unix = new File(casa, "bin/java");
			if (unix.isFile()) {
				return unix;
			}
			File windows = new File(casa, "bin/java.exe");
			if (windows.isFile()) {
				return windows;
			}
		}

		String precisa = proxy ? "21 (ou 17)" : aceitas.get(0);
		throw new IllegalStateException("Não encontrei o Java " + precisa + " instalado, que é o exigido "
				+ (proxy ? "pelo Velocity." : "pela versão " + versaoMc + " do Minecraft.")
				+ " Instale o Java " + precisa.split(" ")[0]
				+ " (Temurin/Adoptium é uma boa opção) e abra o launcher de novo.");
	}

	// ------------------------------------------------------------------
	// Iniciar
	// ------------------------------------------------------------------

	/**
	 * Liga o servidor. Lança IllegalStateException com o motivo quando não dá.
	 * A saída do servidor chega em {@code saida}, em outra thread.
	 */
	public static void iniciar(GerenciadorServidores.Servidor servidor, Saida saida) {
		if (estaRodando(servidor)) {
			throw new IllegalStateException("Esse servidor já está ligado.");
		}
		boolean proxy = CatalogoServidores.ehProxy(servidor.tipo);

		File java = executavelJava(servidor.tipo, servidor.versaoMc);

		String arquivoDeInicio = servidor.args() != null ? servidor.args() : servidor.jar();
		if (arquivoDeInicio == null || !new File(servidor.pasta, arquivoDeInicio).isFile()) {
			throw new IllegalStateException("Não achei os arquivos do servidor em " + servidor.pasta.getName()
					+ ". A instalação pode ter sido interrompida: exclua e crie o servidor de novo.");
		}

		if (!proxy) {
			int porta = portaReal(servidor);
			try (ServerSocket teste = new ServerSocket(porta)) {
				teste.setReuseAddress(true);
			} catch (IOException e) {
				throw new IllegalStateException("A porta " + porta + " já está em uso (talvez outro servidor "
						+ "ligado). Desligue o outro ou troque o server-port em server.properties.");
			}
		}

		List<String> comando = montarComando(servidor, java, proxy);
		System.out.println("[BCraftOS] Servidor: " + servidor.pasta.getAbsolutePath());
		System.out.println("[BCraftOS] Comando: " + String.join(" ", comando));

		ProcessBuilder pb = new ProcessBuilder(comando);
		pb.directory(servidor.pasta);
		pb.redirectErrorStream(true);

		Process processo;
		try {
			processo = pb.start();
		} catch (IOException e) {
			throw new IllegalStateException("Não consegui abrir o processo do servidor: " + e.getMessage());
		}

		File chave = servidor.pasta.getAbsoluteFile();
		EM_EXECUCAO.put(chave, new Processo(processo, proxy));
		registrarGancho();

		Thread leitor = new Thread(() -> {
			try (BufferedReader saidaDoServidor = new BufferedReader(
					new InputStreamReader(processo.getInputStream(), StandardCharsets.UTF_8))) {
				String linha;
				while ((linha = saidaDoServidor.readLine()) != null) {
					saida.linha(linha);
				}
			} catch (IOException e) {
				// O processo foi encerrado: o fim da leitura é esperado.
			}
			int codigo = -1;
			try {
				codigo = processo.waitFor();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			EM_EXECUCAO.remove(chave);
			saida.terminou(codigo);
		}, "servidor-" + servidor.nome);
		leitor.setDaemon(true);
		leitor.start();
	}

	private static List<String> montarComando(GerenciadorServidores.Servidor servidor, File java, boolean proxy) {
		List<String> comando = new ArrayList<>();
		comando.add(java.getAbsolutePath());
		comando.add("-Xmx" + ramValida(servidor.ram()));

		String args = servidor.args();
		if (args != null) {
			// Forge/NeoForge novos: o Windows usa o arquivo irmão win_args.txt.
			if (ehWindows()) {
				args = args.replace("unix_args.txt", "win_args.txt");
			}
			if (new File(servidor.pasta, "user_jvm_args.txt").isFile()) {
				comando.add("@user_jvm_args.txt");
			}
			comando.add("@" + args);
		} else {
			comando.add("-jar");
			comando.add(servidor.jar());
		}
		if (!proxy) {
			comando.add("nogui");
		}
		return comando;
	}

	/** Só aceita algo como 512M ou 4G, para o valor do arquivo de configuração nunca quebrar o comando. */
	private static String ramValida(String ram) {
		return ram != null && ram.matches("[0-9]{1,5}[MmGg]") ? ram : "2G";
	}

	/** A porta que vale é a do server.properties (a pessoa pode ter editado depois de criar). */
	public static int portaReal(GerenciadorServidores.Servidor servidor) {
		File propriedades = new File(servidor.pasta, "server.properties");
		if (propriedades.isFile()) {
			try {
				for (String linha : Files.readAllLines(propriedades.toPath(), StandardCharsets.UTF_8)) {
					if (linha.startsWith("server-port=")) {
						return Integer.parseInt(linha.substring("server-port=".length()).trim());
					}
				}
			} catch (IOException | NumberFormatException e) {
				// cai no valor guardado na criação
			}
		}
		return servidor.porta();
	}

	// ------------------------------------------------------------------
	// Comandos e parada
	// ------------------------------------------------------------------

	/** Digita um comando no console do servidor (sem a barra). */
	public static void enviarComando(GerenciadorServidores.Servidor servidor, String comando) {
		Processo p = EM_EXECUCAO.get(servidor.pasta.getAbsoluteFile());
		if (p == null || !p.processo.isAlive()) {
			return;
		}
		enviar(p, comando);
	}

	/** Pede para o servidor desligar salvando tudo. */
	public static void parar(GerenciadorServidores.Servidor servidor) {
		Processo p = EM_EXECUCAO.get(servidor.pasta.getAbsoluteFile());
		if (p != null && p.processo.isAlive()) {
			enviar(p, p.proxy ? "shutdown" : "stop");
		}
	}

	/** Encerra na marra. Pode perder o que não foi salvo: use só se o "parar" não funcionar. */
	public static void forcarParada(GerenciadorServidores.Servidor servidor) {
		Processo p = EM_EXECUCAO.get(servidor.pasta.getAbsoluteFile());
		if (p != null) {
			p.processo.destroyForcibly();
		}
	}

	private static void enviar(Processo p, String comando) {
		try {
			synchronized (p.entrada) {
				p.entrada.write(comando);
				p.entrada.newLine();
				p.entrada.flush();
			}
		} catch (IOException e) {
			System.err.println("[BCraftOS Aviso] Não consegui enviar o comando ao servidor: " + e.getMessage());
		}
	}

	/** Se o launcher fechar com servidor ligado, manda "stop" e espera um pouco para salvar o mundo. */
	private static synchronized void registrarGancho() {
		if (ganchoRegistrado) {
			return;
		}
		ganchoRegistrado = true;
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			List<Processo> ligados = new ArrayList<>(EM_EXECUCAO.values());
			for (Processo p : ligados) {
				if (p.processo.isAlive()) {
					enviar(p, p.proxy ? "shutdown" : "stop");
				}
			}
			long limite = System.currentTimeMillis() + 20000;
			for (Processo p : ligados) {
				try {
					long resta = Math.max(1, limite - System.currentTimeMillis());
					if (!p.processo.waitFor(resta, java.util.concurrent.TimeUnit.MILLISECONDS)) {
						p.processo.destroyForcibly();
					}
				} catch (InterruptedException e) {
					p.processo.destroyForcibly();
				}
			}
		}, "desligar-servidores"));
	}

	private static boolean ehWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
	}
}
