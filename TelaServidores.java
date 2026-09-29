package BCraftOSproject1.BCraftOS1;



import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.Border;

/**
 * O "modo servidor" do menu: a expansão do layout do BCraftOS1.
 *
 * Segue o mesmo padrão da tela de clientes:
 *   1) escolhe o tipo (Paper, Purpur, Spigot, Vanilla, Forge, NeoForge, Fabric, Velocity);
 *   2) escolhe a versão — a lista vem da internet, pelo mesmo catálogo do cliente;
 *   3) dá um nome e cria: o launcher baixa e instala tudo sozinho.
 *
 * Embaixo ficam "Meus servidores", só os da conta logada, com os botões de iniciar,
 * abrir a pasta e excluir. Ao iniciar, abre o console do servidor.
 */
public class TelaServidores {

	private static final Color COR_FUNDO = new Color(18, 18, 18);
	private static final Color COR_PAINEL = new Color(26, 26, 26);
	private static final Color COR_DESTAQUE = new Color(170, 90, 240);
	private static final Color COR_BORDA = new Color(70, 70, 70);
	private static final Color COR_TEXTO = new Color(230, 230, 230);
	private static final Color COR_TEXTO_FRACO = new Color(150, 150, 150);

	private static final Font FONTE_ROTULO = new Font("Arial", Font.BOLD, 12);
	private static final Font FONTE_CAMPO = new Font("Arial", Font.PLAIN, 14);
	private static final int LARGURA = 420;

	private static final String[] OPCOES_RAM = {"512M", "1G", "2G", "3G", "4G", "6G", "8G"};

	private final JFrame janela;
	private final JPanel painel;
	private final JComponent componente;

	private JComboBox<String> seletorTipos;
	private JComboBox<CatalogoVersoes.ItemVersao> seletorVersoes;
	private JComboBox<String> seletorRam;
	private JComboBox<GerenciadorServidores.Servidor> seletorServidores;
	private JTextField campoNome;
	private JTextField campoPorta;
	private JCheckBox checkModoOnline;
	private JCheckBox checkEula;
	private JLabel descricaoTipo;
	private JLabel statusVersao;
	private JLabel statusCriacao;
	private JLabel statusServidor;
	private JProgressBar barraProgresso;
	private JButton botaoCriar;
	private JButton botaoIniciar;
	private JButton botaoPasta;
	private JButton botaoExcluir;

	private CatalogoVersoes.ItemVersao versaoSelecionada;
	private boolean criando;
	private boolean primeiraVez = true;
	/** Cada pedido de lista ganha um número; resposta de pedido antigo é descartada. */
	private long pedidoAtual;

	public TelaServidores(JFrame janela) {
		this.janela = janela;
		this.painel = montarPainel();
		this.componente = envolverEmRolagem(painel);
	}

	/** O componente que entra no menu do BCraftOS1. */
	public JComponent getPainel() {
		return componente;
	}

	/**
	 * Em telas baixas o painel inteiro não cabe: a rolagem limita a altura ao que a tela tem.
	 * Em telas normais o painel aparece inteiro, sem barra nenhuma.
	 */
	private static JComponent envolverEmRolagem(JPanel conteudo) {
		JScrollPane rolagem = new JScrollPane(conteudo,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
			@Override
			public Dimension getPreferredSize() {
				Dimension d = super.getPreferredSize();
				try {
					int alturaTela = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
							.getMaximumWindowBounds().height;
					// Sobra espaço para as abas, o rodapé e a barra de título da janela.
					d.height = Math.min(d.height, Math.max(420, alturaTela - 200));
				} catch (java.awt.HeadlessException ignorado) {
					// sem tela não há limite a respeitar
				}
				return d;
			}
		};
		rolagem.setBorder(BorderFactory.createEmptyBorder());
		rolagem.setOpaque(false);
		rolagem.getViewport().setOpaque(false);
		rolagem.getVerticalScrollBar().setUnitIncrement(16);
		return rolagem;
	}

	/** Chamado toda vez que a pessoa abre o modo servidor. A internet só é usada na primeira. */
	public void aoMostrar() {
		if (primeiraVez) {
			primeiraVez = false;
			ajustarPadroesDoTipo();
			carregarVersoes();
		}
		atualizarServidores(null);
	}

	// ------------------------------------------------------------------
	// Layout
	// ------------------------------------------------------------------

	private JPanel montarPainel() {
		JPanel p = new JPanel(new GridBagLayout());
		p.setBackground(COR_PAINEL);
		p.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_BORDA, 1),
				BorderFactory.createEmptyBorder(22, 26, 22, 26)));

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		int linha = 0;

		JLabel subtitulo = new JLabel("Escolha o tipo e a versão. O launcher baixa e instala tudo sozinho.");
		subtitulo.setFont(new Font("Arial", Font.PLAIN, 12));
		subtitulo.setForeground(COR_TEXTO_FRACO);
		linha = adicionar(p, c, linha, subtitulo, 16);

		// --- Tipo ---
		linha = adicionar(p, c, linha, rotulo("Tipo de servidor:"), 6);
		seletorTipos = new JComboBox<>(CatalogoServidores.TIPOS);
		estilizarSeletor(seletorTipos);
		linha = adicionar(p, c, linha, seletorTipos, 6);

		descricaoTipo = new JLabel(" ");
		descricaoTipo.setFont(new Font("Arial", Font.PLAIN, 10));
		descricaoTipo.setForeground(COR_TEXTO_FRACO);
		linha = adicionar(p, c, linha, descricaoTipo, 14);

		// --- Versão ---
		linha = adicionar(p, c, linha, rotulo("Versão:"), 6);
		seletorVersoes = new JComboBox<>();
		estilizarSeletor(seletorVersoes);
		linha = adicionar(p, c, linha, seletorVersoes, 6);

		statusVersao = new JLabel("Abra esta aba para carregar as versões.");
		statusVersao.setFont(new Font("Arial", Font.PLAIN, 11));
		statusVersao.setForeground(COR_TEXTO_FRACO);
		linha = adicionar(p, c, linha, statusVersao, 14);

		// --- Nome ---
		linha = adicionar(p, c, linha, rotulo("Nome do servidor:"), 6);
		campoNome = new JTextField();
		estilizarCampo(campoNome);
		linha = adicionar(p, c, linha, campoNome, 14);

		// --- RAM e porta lado a lado ---
		JPanel linhaRamPorta = new JPanel(new GridLayout(1, 2, 12, 0));
		linhaRamPorta.setOpaque(false);
		JPanel colunaRam = new JPanel(new GridLayout(2, 1, 0, 6));
		colunaRam.setOpaque(false);
		colunaRam.add(rotulo("Memória (RAM):"));
		seletorRam = new JComboBox<>(OPCOES_RAM);
		estilizarSeletor(seletorRam);
		seletorRam.setPreferredSize(new Dimension(LARGURA / 2 - 6, 40));
		colunaRam.add(seletorRam);
		JPanel colunaPorta = new JPanel(new GridLayout(2, 1, 0, 6));
		colunaPorta.setOpaque(false);
		colunaPorta.add(rotulo("Porta:"));
		campoPorta = new JTextField("25565");
		estilizarCampo(campoPorta);
		campoPorta.setPreferredSize(new Dimension(LARGURA / 2 - 6, 40));
		colunaPorta.add(campoPorta);
		linhaRamPorta.add(colunaRam);
		linhaRamPorta.add(colunaPorta);
		linha = adicionar(p, c, linha, linhaRamPorta, 12);

		// --- Opções ---
		checkModoOnline = new JCheckBox("Modo online (só contas originais da Microsoft)");
		estilizarCheck(checkModoOnline);
		checkModoOnline.setToolTipText("Desligado: quem joga sem conta original também entra, como no seu launcher. "
				+ "Em troca, qualquer pessoa pode usar o nick de outra.");
		linha = adicionar(p, c, linha, checkModoOnline, 4);

		checkEula = new JCheckBox("Aceito o EULA do Minecraft (aka.ms/MinecraftEULA)");
		estilizarCheck(checkEula);
		linha = adicionar(p, c, linha, checkEula, 12);

		// --- Andamento e botão de criar ---
		barraProgresso = new JProgressBar(0, 100);
		barraProgresso.setPreferredSize(new Dimension(LARGURA, 6));
		barraProgresso.setBackground(COR_FUNDO);
		barraProgresso.setForeground(COR_DESTAQUE);
		barraProgresso.setBorderPainted(false);
		barraProgresso.setVisible(false);
		linha = adicionar(p, c, linha, barraProgresso, 6);

		statusCriacao = new JLabel(" ");
		statusCriacao.setFont(new Font("Arial", Font.PLAIN, 11));
		statusCriacao.setForeground(COR_TEXTO_FRACO);
		linha = adicionar(p, c, linha, statusCriacao, 8);

		botaoCriar = new JButton("CRIAR SERVIDOR");
		estilizarBotao(botaoCriar, true);
		botaoCriar.setEnabled(false);
		botaoCriar.addActionListener(e -> criarServidor());
		linha = adicionar(p, c, linha, botaoCriar, 18);

		// --- Meus servidores ---
		JSeparator separador = new JSeparator();
		separador.setForeground(COR_BORDA);
		linha = adicionar(p, c, linha, separador, 16);

		linha = adicionar(p, c, linha, rotulo("Meus servidores:"), 6);
		seletorServidores = new JComboBox<>();
		estilizarSeletor(seletorServidores);
		linha = adicionar(p, c, linha, seletorServidores, 6);

		statusServidor = new JLabel(" ");
		statusServidor.setFont(new Font("Arial", Font.PLAIN, 11));
		statusServidor.setForeground(COR_TEXTO_FRACO);
		linha = adicionar(p, c, linha, statusServidor, 12);

		botaoIniciar = new JButton("INICIAR SERVIDOR");
		estilizarBotao(botaoIniciar, true);
		botaoIniciar.setEnabled(false);
		botaoIniciar.addActionListener(e -> iniciarServidor());
		linha = adicionar(p, c, linha, botaoIniciar, 8);

		JPanel linhaBotoes = new JPanel(new GridLayout(1, 2, 8, 0));
		linhaBotoes.setOpaque(false);
		botaoPasta = new JButton("Abrir pasta");
		estilizarBotao(botaoPasta, false);
		botaoPasta.setPreferredSize(new Dimension(LARGURA / 2 - 4, 40));
		botaoPasta.setEnabled(false);
		botaoPasta.addActionListener(e -> abrirPasta());
		botaoExcluir = new JButton("Excluir servidor");
		estilizarBotao(botaoExcluir, false);
		botaoExcluir.setPreferredSize(new Dimension(LARGURA / 2 - 4, 40));
		botaoExcluir.setEnabled(false);
		botaoExcluir.addActionListener(e -> excluirServidor());
		linhaBotoes.add(botaoPasta);
		linhaBotoes.add(botaoExcluir);
		adicionar(p, c, linha, linhaBotoes, 0);

		seletorTipos.addActionListener(e -> {
			ajustarPadroesDoTipo();
			carregarVersoes();
		});
		seletorVersoes.addActionListener(e -> atualizarVersaoSelecionada());
		seletorServidores.addActionListener(e -> atualizarBotoesDoServidor());

		return p;
	}

	private int adicionar(JPanel p, GridBagConstraints c, int linha, java.awt.Component componente, int espacoAbaixo) {
		c.gridy = linha;
		c.insets = new Insets(0, 0, espacoAbaixo, 0);
		p.add(componente, c);
		return linha + 1;
	}

	private JLabel rotulo(String texto) {
		JLabel rotulo = new JLabel(texto);
		rotulo.setFont(FONTE_ROTULO);
		rotulo.setForeground(Color.WHITE);
		return rotulo;
	}

	private void estilizarSeletor(JComboBox<?> seletor) {
		seletor.setFont(FONTE_CAMPO);
		seletor.setBackground(COR_FUNDO);
		seletor.setForeground(Color.WHITE);
		seletor.setPreferredSize(new Dimension(LARGURA, 40));
		Border borda = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_BORDA, 1),
				BorderFactory.createEmptyBorder(0, 8, 0, 8));
		seletor.setBorder(borda);
	}

	private void estilizarCampo(JTextField campo) {
		campo.setFont(FONTE_CAMPO);
		campo.setOpaque(true);
		campo.setBackground(COR_FUNDO);
		campo.setForeground(COR_TEXTO);
		campo.setCaretColor(COR_DESTAQUE);
		campo.setSelectionColor(COR_DESTAQUE);
		campo.setSelectedTextColor(Color.WHITE);
		campo.setPreferredSize(new Dimension(LARGURA, 40));
		Border normal = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_BORDA, 1),
				BorderFactory.createEmptyBorder(0, 12, 0, 12));
		Border foco = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_DESTAQUE, 2),
				BorderFactory.createEmptyBorder(0, 11, 0, 11));
		campo.setBorder(normal);
		campo.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusGained(java.awt.event.FocusEvent e) {
				campo.setBorder(foco);
			}

			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				campo.setBorder(normal);
			}
		});
	}

	private void estilizarCheck(JCheckBox check) {
		check.setFont(new Font("Arial", Font.PLAIN, 12));
		check.setForeground(Color.WHITE);
		check.setOpaque(false);
		check.setFocusPainted(false);
	}

	private void estilizarBotao(JButton botao, boolean principal) {
		botao.setFont(new Font("Arial", Font.BOLD, 14));
		botao.setFocusPainted(false);
		botao.setPreferredSize(new Dimension(LARGURA, 44));
		botao.setCursor(new Cursor(Cursor.HAND_CURSOR));
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
	// Tipo e versão
	// ------------------------------------------------------------------

	/** Cada tipo tem uma RAM e uma porta que fazem sentido de início; a pessoa pode mudar depois. */
	private void ajustarPadroesDoTipo() {
		String tipo = (String) seletorTipos.getSelectedItem();
		CatalogoServidores.Categoria categoria = CatalogoServidores.categoria(tipo);

		descricaoTipo.setText("<html><body style='width:400px'>" + CatalogoServidores.descricao(tipo)
				+ "</body></html>");

		boolean proxy = categoria == CatalogoServidores.Categoria.PROXY;
		campoPorta.setText(proxy ? "25577" : "25565");
		seletorRam.setSelectedItem(proxy ? "512M" : categoria == CatalogoServidores.Categoria.MODS ? "4G" : "2G");

		// O proxy não tem EULA nem modo online: a configuração dele fica no velocity.toml.
		checkEula.setEnabled(!proxy);
		checkModoOnline.setEnabled(!proxy);
		if (proxy) {
			checkEula.setSelected(false);
			checkModoOnline.setSelected(false);
		}
	}

	private void carregarVersoes() {
		String tipo = (String) seletorTipos.getSelectedItem();
		long meuPedido = ++pedidoAtual;

		seletorVersoes.setModel(new DefaultComboBoxModel<>());
		versaoSelecionada = null;
		botaoCriar.setEnabled(false);
		statusVersao.setText("Carregando versões de " + tipo + "...");

		new SwingWorker<List<CatalogoVersoes.ItemVersao>, Void>() {
			@Override
			protected List<CatalogoVersoes.ItemVersao> doInBackground() throws Exception {
				return CatalogoServidores.listarVersoes(tipo);
			}

			@Override
			protected void done() {
				if (meuPedido != pedidoAtual) {
					return; // a pessoa já trocou de tipo: essa resposta não vale mais
				}
				try {
					List<CatalogoVersoes.ItemVersao> itens = get();
					if (itens.isEmpty()) {
						statusVersao.setText("Nenhuma versão encontrada para " + tipo + ".");
						return;
					}
					DefaultComboBoxModel<CatalogoVersoes.ItemVersao> modelo = new DefaultComboBoxModel<>();
					for (CatalogoVersoes.ItemVersao item : itens) {
						modelo.addElement(item);
					}
					seletorVersoes.setModel(modelo);
					atualizarVersaoSelecionada();
				} catch (Exception erro) {
					statusVersao.setText("Não consegui falar com a internet. Verifique sua conexão.");
					System.err.println("[BCraftOS Erro] Falha ao listar versões de servidor: " + erro.getMessage());
				}
			}
		}.execute();
	}

	private void atualizarVersaoSelecionada() {
		Object escolhido = seletorVersoes.getSelectedItem();
		if (!(escolhido instanceof CatalogoVersoes.ItemVersao item)) {
			versaoSelecionada = null;
			botaoCriar.setEnabled(false);
			return;
		}
		versaoSelecionada = item;
		botaoCriar.setEnabled(!criando);

		String tipo = (String) seletorTipos.getSelectedItem();
		String aviso = CatalogoVersoes.avisoDoLoader(tipo, item.versaoMc);
		if (aviso != null) {
			statusVersao.setText(aviso);
		} else if (CatalogoServidores.ehProxy(tipo)) {
			statusVersao.setText("Java necessário: 21 (ou 17).");
		} else if (CatalogoServidores.SPIGOT.equals(tipo)) {
			statusVersao.setText("Java necessário: " + MinecraftLauncher.javaNecessarioPara(item.versaoMc)
					+ ". Vai compilar na hora: leva alguns minutos.");
		} else {
			statusVersao.setText("Java necessário: " + MinecraftLauncher.javaNecessarioPara(item.versaoMc) + ".");
		}
	}

	// ------------------------------------------------------------------
	// Criar
	// ------------------------------------------------------------------

	private void criarServidor() {
		if (criando || versaoSelecionada == null) {
			return;
		}
		String tipo = (String) seletorTipos.getSelectedItem();
		CatalogoVersoes.ItemVersao item = versaoSelecionada;
		String nome = campoNome.getText();

		GerenciadorServidores.Opcoes opcoes = new GerenciadorServidores.Opcoes();
		try {
			opcoes.porta = Integer.parseInt(campoPorta.getText().trim());
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(janela, "A porta precisa ser um número (o padrão é 25565).",
					"Porta inválida", JOptionPane.WARNING_MESSAGE);
			return;
		}
		opcoes.ram = (String) seletorRam.getSelectedItem();
		opcoes.modoOnline = checkModoOnline.isSelected();
		opcoes.eulaAceito = checkEula.isSelected();

		criando = true;
		botaoCriar.setEnabled(false);
		barraProgresso.setValue(0);
		barraProgresso.setVisible(true);
		statusCriacao.setText("Começando...");

		new SwingWorker<GerenciadorServidores.Servidor, Object[]>() {
			@Override
			protected GerenciadorServidores.Servidor doInBackground() throws Exception {
				return GerenciadorServidores.criar(tipo, item, nome, opcoes,
						(etapa, porcentagem) -> publish(new Object[]{etapa, porcentagem}));
			}

			@Override
			protected void process(List<Object[]> avisos) {
				Object[] ultimo = avisos.get(avisos.size() - 1);
				statusCriacao.setText((String) ultimo[0]);
				barraProgresso.setValue((Integer) ultimo[1]);
			}

			@Override
			protected void done() {
				criando = false;
				try {
					GerenciadorServidores.Servidor criado = get();
					barraProgresso.setValue(100);
					statusCriacao.setText("Servidor criado: " + criado.nome);
					campoNome.setText("");
					atualizarServidores(criado);
					JOptionPane.showMessageDialog(janela, mensagemDeSucesso(criado), "Servidor criado",
							JOptionPane.INFORMATION_MESSAGE);
				} catch (Exception erro) {
					Throwable causa = erro.getCause() == null ? erro : erro.getCause();
					barraProgresso.setVisible(false);
					String detalhe = causa.getMessage();
					if (detalhe == null || detalhe.isBlank()) {
						detalhe = causa.getClass().getSimpleName();
					}
					statusCriacao.setText("Não foi possível criar. Veja a mensagem.");
					boolean aviso = causa instanceof IllegalStateException;
					JOptionPane.showMessageDialog(janela,
							aviso ? detalhe : "Não consegui criar o servidor:\n\n" + detalhe
									+ "\n\nConfira sua internet e tente de novo.",
							aviso ? "Não foi possível criar" : "Erro ao criar o servidor",
							aviso ? JOptionPane.WARNING_MESSAGE : JOptionPane.ERROR_MESSAGE);
				}
				atualizarVersaoSelecionada();
			}
		}.execute();
	}

	private String mensagemDeSucesso(GerenciadorServidores.Servidor servidor) {
		String extra = CatalogoServidores.pastaExtra(servidor.tipo);
		String base = "Servidor \"" + servidor.nome + "\" criado.\n\n";
		if (CatalogoServidores.ehProxy(servidor.tipo)) {
			return base + "O Velocity cria o velocity.toml na primeira vez que liga. "
					+ "Depois configure os servidores dele nesse arquivo.";
		}
		if (extra == null) {
			return base + "É só clicar em INICIAR SERVIDOR.";
		}
		return base + "Coloque os " + extra + " (.jar) na pasta \"" + extra + "\" do servidor "
				+ "(botão Abrir pasta) e clique em INICIAR SERVIDOR.";
	}

	// ------------------------------------------------------------------
	// Meus servidores
	// ------------------------------------------------------------------

	private void atualizarServidores(GerenciadorServidores.Servidor selecionar) {
		DefaultComboBoxModel<GerenciadorServidores.Servidor> modelo = new DefaultComboBoxModel<>();
		GerenciadorServidores.Servidor alvo = null;
		for (GerenciadorServidores.Servidor s : GerenciadorServidores.listar()) {
			modelo.addElement(s);
			if (selecionar != null && s.pasta.equals(selecionar.pasta)) {
				alvo = s;
			}
		}
		seletorServidores.setModel(modelo);
		if (alvo != null) {
			seletorServidores.setSelectedItem(alvo);
		}
		atualizarBotoesDoServidor();
	}

	private GerenciadorServidores.Servidor servidorEscolhido() {
		Object escolhido = seletorServidores.getSelectedItem();
		return escolhido instanceof GerenciadorServidores.Servidor s ? s : null;
	}

	private void atualizarBotoesDoServidor() {
		GerenciadorServidores.Servidor s = servidorEscolhido();
		if (s == null) {
			statusServidor.setText("Nenhum servidor criado ainda nesta conta.");
			botaoIniciar.setText("INICIAR SERVIDOR");
			botaoIniciar.setEnabled(false);
			botaoPasta.setEnabled(false);
			botaoExcluir.setEnabled(false);
			return;
		}
		boolean ligado = ExecutorServidor.estaRodando(s);
		botaoIniciar.setText(ligado ? "SERVIDOR LIGADO" : "INICIAR SERVIDOR");
		botaoIniciar.setEnabled(!ligado);
		botaoPasta.setEnabled(true);
		botaoExcluir.setEnabled(!ligado);

		String extra = CatalogoServidores.pastaExtra(s.tipo);
		if (extra != null) {
			statusServidor.setText("Os " + extra + " vão em: " + caminhoCurto(new File(s.pasta, extra)));
		} else if (CatalogoServidores.ehProxy(s.tipo)) {
			statusServidor.setText("Proxy: a configuração fica no velocity.toml, dentro da pasta.");
		} else {
			statusServidor.setText("Servidor puro, sem mods nem plugins.");
		}
	}

	/** Caminho a partir da pasta do launcher, para a pessoa saber onde mexer. */
	private String caminhoCurto(File arquivo) {
		File raiz = new File(System.getProperty("user.dir"));
		try {
			return raiz.toPath().relativize(arquivo.toPath()).toString();
		} catch (IllegalArgumentException e) {
			return arquivo.getAbsolutePath();
		}
	}

	private void iniciarServidor() {
		GerenciadorServidores.Servidor s = servidorEscolhido();
		if (s == null) {
			return;
		}
		try {
			ConsoleServidor.abrir(s, () -> atualizarBotoesDoServidor());
			atualizarBotoesDoServidor();
		} catch (IllegalStateException motivo) {
			// Aqui chega o motivo real (Java que falta, porta ocupada...), em vez de um erro seco.
			JOptionPane.showMessageDialog(janela, motivo.getMessage(), "Não foi possível iniciar",
					JOptionPane.WARNING_MESSAGE);
		}
	}

	private void abrirPasta() {
		GerenciadorServidores.Servidor s = servidorEscolhido();
		if (s == null) {
			return;
		}
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				Desktop.getDesktop().open(s.pasta);
				return;
			}
		} catch (IOException | UnsupportedOperationException e) {
			// tenta o jeito do Linux logo abaixo
		}
		try {
			new ProcessBuilder("xdg-open", s.pasta.getAbsolutePath()).start();
		} catch (IOException e) {
			JOptionPane.showMessageDialog(janela, "Não consegui abrir a pasta sozinho. Ela fica em:\n"
					+ s.pasta.getAbsolutePath(), "Abrir pasta", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void excluirServidor() {
		GerenciadorServidores.Servidor s = servidorEscolhido();
		if (s == null) {
			return;
		}
		if (ExecutorServidor.estaRodando(s)) {
			JOptionPane.showMessageDialog(janela, "Desligue o servidor antes de excluir.",
					"Excluir servidor", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		int resposta = JOptionPane.showConfirmDialog(janela,
				"Isso apaga o servidor \"" + s.nome + "\" e o MUNDO dele, com plugins/mods e tudo mais.\n"
						+ "Não tem volta. Excluir mesmo?",
				"Excluir servidor", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (resposta != JOptionPane.YES_OPTION) {
			return;
		}
		botaoExcluir.setEnabled(false);
		new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				GerenciadorServidores.excluir(s);
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
				} catch (Exception erro) {
					Throwable causa = erro.getCause() == null ? erro : erro.getCause();
					JOptionPane.showMessageDialog(janela, "Não consegui excluir: " + causa.getMessage(),
							"Erro", JOptionPane.ERROR_MESSAGE);
				}
				atualizarServidores(null);
			}
		}.execute();
	}
}
