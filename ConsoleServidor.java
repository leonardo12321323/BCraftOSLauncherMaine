package BCraftOSproject1.BCraftOS1;



import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Janela com a saída do servidor e uma caixa para digitar comandos (op, whitelist, stop...).
 * Abre quando o servidor liga e fecha junto com ele. Se a pessoa fechar a janela com o
 * servidor ligado, o launcher pergunta e desliga o servidor salvando o mundo.
 */
public class ConsoleServidor extends JFrame {

	private static final Color COR_FUNDO = new Color(18, 18, 18);
	private static final Color COR_PAINEL = new Color(26, 26, 26);
	private static final Color COR_DESTAQUE = new Color(170, 90, 240);
	private static final Color COR_BORDA = new Color(70, 70, 70);
	private static final Color COR_TEXTO = new Color(215, 215, 215);
	private static final Color COR_TEXTO_FRACO = new Color(150, 150, 150);

	/** Passou disso, as linhas mais antigas saem para o console não pesar. */
	private static final int LINHAS_MAXIMAS = 3000;
	private static final int LINHAS_REMOVIDAS = 500;

	private final GerenciadorServidores.Servidor servidor;
	private final Runnable aoTerminar;
	private final JTextArea area = new JTextArea();
	private final JTextField campoComando = new JTextField();
	private final JButton botaoParar = new JButton("Desligar");
	private final JButton botaoForcar = new JButton("Forçar parada");
	private final JLabel estado = new JLabel("Ligando...");
	private boolean encerrado;
	private boolean fecharQuandoTerminar;

	private ConsoleServidor(GerenciadorServidores.Servidor servidor, Runnable aoTerminar) {
		super("Console - " + servidor.nome + " (" + servidor.tipo + " " + servidor.versaoMc + ")");
		this.servidor = servidor;
		this.aoTerminar = aoTerminar;
		montarJanela();
	}

	/**
	 * Liga o servidor e abre o console dele.
	 *
	 * @throws IllegalStateException com o motivo real quando o servidor não pode ligar
	 */
	public static void abrir(GerenciadorServidores.Servidor servidor, Runnable aoTerminar) {
		ConsoleServidor console = new ConsoleServidor(servidor, aoTerminar);
		try {
			ExecutorServidor.iniciar(servidor, new ExecutorServidor.Saida() {
				@Override
				public void linha(String texto) {
					console.adicionar(texto);
				}

				@Override
				public void terminou(int codigoSaida) {
					console.aoEncerrar(codigoSaida);
				}
			});
		} catch (IllegalStateException motivo) {
			console.dispose();
			throw motivo;
		}
		if (CatalogoServidores.ehProxy(servidor.tipo)) {
			console.adicionar("[BCraftOS] Proxy ligando. O endereço e a porta ficam no velocity.toml, "
					+ "que o Velocity cria na primeira vez.");
		} else {
			console.adicionar("[BCraftOS] Ligando " + servidor.nome + ". Para entrar: localhost:"
					+ ExecutorServidor.portaReal(servidor) + " (no mesmo computador).");
		}
		console.estado.setText("Ligado");
		console.setVisible(true);
		SwingUtilities.invokeLater(console.campoComando::requestFocusInWindow);
	}

	private void montarJanela() {
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				tentarFechar();
			}
		});

		area.setEditable(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(false);
		area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		area.setBackground(COR_FUNDO);
		area.setForeground(COR_TEXTO);
		area.setCaretColor(COR_DESTAQUE);
		area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

		JScrollPane rolagem = new JScrollPane(area);
		rolagem.setBorder(BorderFactory.createLineBorder(COR_BORDA, 1));
		rolagem.setPreferredSize(new Dimension(760, 420));

		estado.setForeground(COR_TEXTO_FRACO);
		estado.setFont(new Font("Arial", Font.PLAIN, 12));

		campoComando.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
		campoComando.setBackground(COR_FUNDO);
		campoComando.setForeground(COR_TEXTO);
		campoComando.setCaretColor(COR_DESTAQUE);
		campoComando.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_BORDA, 1),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		campoComando.setToolTipText("Digite um comando (sem a barra) e aperte Enter. Ex.: op SeuNick");
		campoComando.addActionListener(e -> enviarComando());

		estilizarBotao(botaoParar, true);
		botaoParar.addActionListener(e -> desligar());
		estilizarBotao(botaoForcar, false);
		botaoForcar.addActionListener(e -> forcar());

		JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		botoes.setOpaque(false);
		botoes.add(botaoForcar);
		botoes.add(botaoParar);

		JPanel barra = new JPanel(new BorderLayout(10, 0));
		barra.setOpaque(false);
		barra.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
		barra.add(campoComando, BorderLayout.CENTER);
		barra.add(botoes, BorderLayout.EAST);

		JPanel painel = new JPanel(new BorderLayout());
		painel.setBackground(COR_PAINEL);
		painel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
		painel.add(estado, BorderLayout.NORTH);
		painel.add(rolagem, BorderLayout.CENTER);
		painel.add(barra, BorderLayout.SOUTH);
		estado.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		setContentPane(painel);
		pack();
		setLocationRelativeTo(null);
	}

	private static void estilizarBotao(JButton botao, boolean principal) {
		botao.setFont(new Font("Arial", Font.BOLD, 12));
		botao.setFocusPainted(false);
		botao.setCursor(new Cursor(Cursor.HAND_CURSOR));
		botao.setPreferredSize(new Dimension(120, 34));
		if (principal) {
			botao.setBackground(COR_DESTAQUE);
			botao.setForeground(Color.BLACK);
		} else {
			botao.setBackground(COR_FUNDO);
			botao.setForeground(COR_TEXTO);
			botao.setBorder(BorderFactory.createLineBorder(COR_BORDA, 1));
		}
	}

	// ------------------------------------------------------------------
	// Saída e comandos
	// ------------------------------------------------------------------

	private void adicionar(String linha) {
		SwingUtilities.invokeLater(() -> {
			area.append(linha + "\n");
			if (area.getLineCount() > LINHAS_MAXIMAS) {
				try {
					area.replaceRange("", 0, area.getLineStartOffset(LINHAS_REMOVIDAS));
				} catch (javax.swing.text.BadLocationException ignorado) {
					// se a linha não existe mais, não há o que cortar
				}
			}
			area.setCaretPosition(area.getDocument().getLength());
		});
	}

	private void enviarComando() {
		String comando = campoComando.getText().trim();
		if (comando.isEmpty() || encerrado) {
			return;
		}
		campoComando.setText("");
		adicionar("> " + comando);
		ExecutorServidor.enviarComando(servidor, comando);
	}

	private void desligar() {
		if (encerrado) {
			return;
		}
		estado.setText("Desligando (salvando o mundo)...");
		botaoParar.setEnabled(false);
		ExecutorServidor.parar(servidor);
	}

	private void forcar() {
		if (encerrado) {
			return;
		}
		int resposta = JOptionPane.showConfirmDialog(this,
				"Forçar a parada pode perder o que não foi salvo no mundo.\nUse só se o servidor travou. Continuar?",
				"Forçar parada", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (resposta == JOptionPane.OK_OPTION) {
			ExecutorServidor.forcarParada(servidor);
		}
	}

	private void tentarFechar() {
		if (encerrado) {
			dispose();
			return;
		}
		int resposta = JOptionPane.showConfirmDialog(this,
				"O servidor ainda está ligado.\nDesligar agora (o mundo é salvo) e fechar esta janela?",
				"Servidor ligado", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
		if (resposta == JOptionPane.OK_OPTION) {
			fecharQuandoTerminar = true;
			desligar();
		}
	}

	private void aoEncerrar(int codigoSaida) {
		SwingUtilities.invokeLater(() -> {
			encerrado = true;
			estado.setText(codigoSaida == 0 ? "Servidor desligado." : "Servidor encerrado (código " + codigoSaida + ").");
			botaoParar.setEnabled(false);
			botaoForcar.setEnabled(false);
			campoComando.setEnabled(false);
			area.append("[BCraftOS] Servidor encerrado com código " + codigoSaida + ".\n");
			area.setCaretPosition(area.getDocument().getLength());
			if (aoTerminar != null) {
				aoTerminar.run();
			}
			if (fecharQuandoTerminar) {
				dispose();
			}
		});
	}
}
