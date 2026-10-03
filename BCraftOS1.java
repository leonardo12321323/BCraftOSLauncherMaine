package BCraftOSproject1.BCraftOS1;



import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
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
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.Border;

/**
 * Tela principal do launcher.
 *
 * Agora as versões vêm por catálogo, em duas etapas:
 *   1) escolhe o loader (Forge, Fabric, NeoForge);
 *   2) escolhe a versão daquele loader — a lista vem da internet, só com a versão mais
 *      estável de cada uma (sem encher a tela de builds);
 *      se a versão ainda não estiver no PC, um botão baixa e instala ela sozinho.
 *
 * Depois disso o modpack é escolhido e o cliente inicia, como já era antes.
 * O sistema de skins continua removido.
 *
 * No topo há duas abas: "Jogar" (a tela acima) e "Servidor" (TelaServidores), que expande o
 * menu para criar servidores de Minecraft. A janela cresce ou encolhe conforme a aba.
 */
public class BCraftOS1 {

	private static final Color COR_FUNDO = new Color(18, 18, 18);
	private static final Color COR_PAINEL = new Color(26, 26, 26);
	private static final Color COR_DESTAQUE = new Color(170, 90, 240);
	private static final Color COR_BORDA = new Color(70, 70, 70);
	private static final Color COR_TEXTO = new Color(230, 230, 230);
	private static final Color COR_TEXTO_FRACO = new Color(150, 150, 150);

	private static final Font FONTE_ROTULO = new Font("Arial", Font.BOLD, 12);
	private static final Font FONTE_CAMPO = new Font("Arial", Font.PLAIN, 14);

	private static final int LARGURA = 420;

	private static String jogadorAutenticadoID = "Player";

	private static JFrame janela;
	private static JComboBox<String> seletorLoaders;
	private static JComboBox<CatalogoVersoes.ItemVersao> seletorVersoes;
	private static JComboBox<String> seletorModpacks;

	private static JLabel statusVersao;
	private static JLabel statusModpack;
	private static JProgressBar barraProgresso;
	private static JButton botaoBaixar;
	private static JButton botaoJogar;
	private static JCheckBox checkModoNormal;

	private static CatalogoVersoes.ItemVersao versaoSelecionada;
	private static boolean baixando;

	// Abas do topo: "Jogar" (cliente) e "Servidor"
	private static CardLayout cartoes;
	private static JPanel areaCartoes;
	private static JButton abaJogar;
	private static JButton abaServidor;
	private static TelaServidores telaServidores;

	public static void menuPrincipal() {
		// Monta a estrutura de pastas e arruma nomes de arquivo duplicados antes de tudo.
		OrganizadorPastas.organizar();
		GerenciadorVersoes.configurarDiretoriosIniciais();
		OrganizadorPastas.mostrarResumo();

		janela = new JFrame("BCraftOS - Catálogo de Versões");
		janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		janela.setResizable(false);

		JPanel painelPrincipal = new JPanel(new BorderLayout());
		painelPrincipal.setBackground(COR_FUNDO);
		painelPrincipal.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
		// Dois cartões na mesma janela: o de jogar (de sempre) e o de criar servidores.
		cartoes = new CardLayout() {
			/** Mede só o cartão visível, para a janela crescer no modo servidor e voltar ao trocar. */
			@Override
			public Dimension preferredLayoutSize(Container pai) {
				synchronized (pai.getTreeLock()) {
					Insets margens = pai.getInsets();
					for (Component filho : pai.getComponents()) {
						if (filho.isVisible()) {
							Dimension d = filho.getPreferredSize();
							return new Dimension(d.width + margens.left + margens.right,
									d.height + margens.top + margens.bottom);
						}
					}
					return super.preferredLayoutSize(pai);
				}
			}
		};
		areaCartoes = new JPanel(cartoes);
		areaCartoes.setOpaque(false);
		areaCartoes.add(montarPainel(), "cliente");
		telaServidores = new TelaServidores(janela);
		areaCartoes.add(telaServidores.getPainel(), "servidor");

		painelPrincipal.add(montarAbas(), BorderLayout.NORTH);
		painelPrincipal.add(areaCartoes, BorderLayout.CENTER);

		JLabel rodape = new JLabel("Conta ativa: " + jogadorAutenticadoID);
		rodape.setForeground(COR_TEXTO_FRACO);
		rodape.setFont(new Font("Arial", Font.PLAIN, 11));
		rodape.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));

		JButton botaoExcluirConta = new JButton("Excluir minha conta");
		botaoExcluirConta.setFont(new Font("Arial", Font.PLAIN, 11));
		botaoExcluirConta.setForeground(COR_TEXTO_FRACO);
		botaoExcluirConta.setFocusPainted(false);
		botaoExcluirConta.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		botaoExcluirConta.addActionListener(e -> excluirContaAtiva());

		JPanel linhaRodape = new JPanel(new BorderLayout());
		linhaRodape.setOpaque(false);
		linhaRodape.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
		rodape.setBorder(BorderFactory.createEmptyBorder());
		linhaRodape.add(rodape, BorderLayout.WEST);
		linhaRodape.add(botaoExcluirConta, BorderLayout.EAST);
		painelPrincipal.add(linhaRodape, BorderLayout.SOUTH);

		janela.add(painelPrincipal);
		janela.pack();
		janela.setLocationRelativeTo(null);
		janela.setVisible(true);

		carregarVersoesDoLoader();
	}

	/** As duas abas do topo: Jogar e Servidor. */
	private static JPanel montarAbas() {
		abaJogar = new JButton("Jogar");
		abaServidor = new JButton("Servidor");
		abaJogar.addActionListener(e -> mostrarModo(false));
		abaServidor.addActionListener(e -> mostrarModo(true));

		JPanel abas = new JPanel(new GridLayout(1, 2, 8, 0));
		abas.setOpaque(false);
		abas.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
		abas.add(abaJogar);
		abas.add(abaServidor);

		estilizarAba(abaJogar, true);
		estilizarAba(abaServidor, false);
		return abas;
	}

	private static void estilizarAba(JButton aba, boolean ativa) {
		aba.setFont(new Font("Arial", Font.BOLD, 13));
		aba.setFocusPainted(false);
		aba.setCursor(new Cursor(Cursor.HAND_CURSOR));
		aba.setPreferredSize(new Dimension(100, 38));
		if (ativa) {
			aba.setBackground(COR_DESTAQUE);
			aba.setForeground(Color.BLACK);
			aba.setBorder(BorderFactory.createLineBorder(COR_DESTAQUE, 1));
		} else {
			aba.setBackground(COR_PAINEL);
			aba.setForeground(COR_TEXTO);
			aba.setBorder(BorderFactory.createLineBorder(COR_BORDA, 1));
		}
	}

	/** Troca entre jogar e criar servidores; a janela se ajusta ao tamanho do cartão novo. */
	private static void mostrarModo(boolean servidor) {
		cartoes.show(areaCartoes, servidor ? "servidor" : "cliente");
		estilizarAba(abaJogar, !servidor);
		estilizarAba(abaServidor, servidor);
		janela.setTitle(servidor ? "BCraftOS - Servidores" : "BCraftOS - Catálogo de Versões");
		if (servidor) {
			telaServidores.aoMostrar();
		}
		janela.pack();
		janela.setLocationRelativeTo(null);
	}

	private static JPanel montarPainel() {
		JPanel painel = new JPanel(new GridBagLayout());
		painel.setBackground(COR_PAINEL);
		painel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_BORDA, 1),
				BorderFactory.createEmptyBorder(22, 26, 22, 26)));

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;

		JLabel titulo = new JLabel("BCraftOS Launcher");
		titulo.setFont(new Font("Arial", Font.BOLD, 24));
		titulo.setForeground(COR_DESTAQUE);
		c.gridy = 0;
		c.insets = new Insets(0, 0, 4, 0);
		painel.add(titulo, c);

		JLabel subtitulo = new JLabel("Escolha o loader, a versão e o modpack.");
		subtitulo.setFont(new Font("Arial", Font.PLAIN, 12));
		subtitulo.setForeground(COR_TEXTO_FRACO);
		c.gridy = 1;
		c.insets = new Insets(0, 0, 20, 0);
		painel.add(subtitulo, c);

		// --- Loader ---
		adicionarRotulo(painel, c, 2, "Loader:");
		seletorLoaders = new JComboBox<>(CatalogoVersoes.LOADERS);
		estilizarSeletor(seletorLoaders);
		c.gridy = 3;
		c.insets = new Insets(0, 0, 6, 0);
		painel.add(seletorLoaders, c);

		JLabel dicaSemMods = new JLabel(
				"Para jogar sem mods, escolha Forge ou NeoForge e deixe o modpack sem mod nenhum.");
		dicaSemMods.setFont(new Font("Arial", Font.PLAIN, 10));
		dicaSemMods.setForeground(COR_TEXTO_FRACO);
		c.gridy = 4;
		c.insets = new Insets(0, 0, 16, 0);
		painel.add(dicaSemMods, c);

		// --- Versão ---
		adicionarRotulo(painel, c, 5, "Versão:");
		seletorVersoes = new JComboBox<>();
		estilizarSeletor(seletorVersoes);
		c.gridy = 6;
		c.insets = new Insets(0, 0, 6, 0);
		painel.add(seletorVersoes, c);

		statusVersao = new JLabel("Carregando versões...");
		statusVersao.setFont(new Font("Arial", Font.PLAIN, 11));
		statusVersao.setForeground(COR_TEXTO_FRACO);
		c.gridy = 7;
		c.insets = new Insets(0, 0, 8, 0);
		painel.add(statusVersao, c);

		barraProgresso = new JProgressBar(0, 100);
		barraProgresso.setPreferredSize(new Dimension(LARGURA, 6));
		barraProgresso.setBackground(COR_FUNDO);
		barraProgresso.setForeground(COR_DESTAQUE);
		barraProgresso.setBorderPainted(false);
		barraProgresso.setVisible(false);
		c.gridy = 8;
		c.insets = new Insets(0, 0, 10, 0);
		painel.add(barraProgresso, c);

		botaoBaixar = new JButton("Baixar esta versão");
		estilizarBotao(botaoBaixar, false);
		botaoBaixar.addActionListener(e -> baixarVersaoSelecionada());
		c.gridy = 9;
		c.insets = new Insets(0, 0, 18, 0);
		painel.add(botaoBaixar, c);

		// --- Modpack ---
		adicionarRotulo(painel, c, 10, "Modpack:");
		seletorModpacks = new JComboBox<>();
		estilizarSeletor(seletorModpacks);
		c.gridy = 11;
		c.insets = new Insets(0, 0, 6, 0);
		painel.add(seletorModpacks, c);

		statusModpack = new JLabel(" ");
		statusModpack.setFont(new Font("Arial", Font.PLAIN, 11));
		statusModpack.setForeground(COR_TEXTO_FRACO);
		c.gridy = 12;
		c.insets = new Insets(0, 0, 20, 0);
		painel.add(statusModpack, c);

		checkModoNormal = new JCheckBox("Modo normal (para mods de jogo e ghost clients)");
		checkModoNormal.setFont(new Font("Arial", Font.PLAIN, 12));
		checkModoNormal.setForeground(Color.WHITE);
		checkModoNormal.setOpaque(false);
		checkModoNormal.setFocusPainted(false);
		checkModoNormal.setToolTipText("Abre o Minecraft + Forge do jeito normal (só Forge até a 1.12.2). "
				+ "O modo padrão, do Gradle, não aceita mods feitos para o jogo normal.");
		checkModoNormal.setEnabled(false);
		c.gridy = 13;
		c.insets = new Insets(0, 0, 14, 0);
		painel.add(checkModoNormal, c);

		botaoJogar = new JButton("INICIAR CLIENT");
		estilizarBotao(botaoJogar, true);
		botaoJogar.addActionListener(e -> iniciarJogo());
		c.gridy = 14;
		c.insets = new Insets(0, 0, 0, 0);
		painel.add(botaoJogar, c);

		seletorLoaders.addActionListener(e -> carregarVersoesDoLoader());
		seletorVersoes.addActionListener(e -> atualizarVersaoSelecionada());
		seletorModpacks.addActionListener(e -> atualizarStatusModpack());

		return painel;
	}

	private static void adicionarRotulo(JPanel painel, GridBagConstraints c, int linha, String texto) {
		JLabel rotulo = new JLabel(texto);
		rotulo.setFont(FONTE_ROTULO);
		rotulo.setForeground(Color.WHITE);
		c.gridy = linha;
		c.insets = new Insets(0, 0, 6, 0);
		painel.add(rotulo, c);
	}

	private static void estilizarSeletor(JComboBox<?> seletor) {
		seletor.setFont(FONTE_CAMPO);
		seletor.setBackground(COR_FUNDO);
		seletor.setForeground(Color.WHITE);
		seletor.setPreferredSize(new Dimension(LARGURA, 40));
		Border borda = BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_BORDA, 1),
				BorderFactory.createEmptyBorder(0, 8, 0, 8));
		seletor.setBorder(borda);
	}

	private static void estilizarBotao(JButton botao, boolean principal) {
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
	// Catálogo: loader -> versões vindas da internet
	// ------------------------------------------------------------------

	private static void carregarVersoesDoLoader() {
		String loader = (String) seletorLoaders.getSelectedItem();
		seletorVersoes.setModel(new DefaultComboBoxModel<>());
		seletorModpacks.setModel(new DefaultComboBoxModel<>());
		versaoSelecionada = null;
		botaoBaixar.setEnabled(false);
		botaoJogar.setEnabled(false);
		statusVersao.setText("Carregando versões de " + loader + "...");
		statusModpack.setText(" ");
		barraProgresso.setVisible(false);

		new SwingWorker<List<CatalogoVersoes.ItemVersao>, Void>() {
			@Override
			protected List<CatalogoVersoes.ItemVersao> doInBackground() throws Exception {
				return CatalogoVersoes.listarVersoes(loader);
			}

			@Override
			protected void done() {
				try {
					List<CatalogoVersoes.ItemVersao> itens = get();
					if (itens.isEmpty()) {
						statusVersao.setText("Nenhuma versão encontrada para " + loader + ".");
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
					System.err.println("[BCraftOS Erro] Falha ao listar versões: " + erro.getMessage());
				}
			}
		}.execute();
	}

	private static void atualizarVersaoSelecionada() {
		Object escolhido = seletorVersoes.getSelectedItem();
		if (!(escolhido instanceof CatalogoVersoes.ItemVersao item)) {
			versaoSelecionada = null;
			botaoBaixar.setEnabled(false);
			botaoJogar.setEnabled(false);
			statusModpack.setText(" ");
			return;
		}

		versaoSelecionada = item;
		String loader = (String) seletorLoaders.getSelectedItem();
		boolean instalada = estaInstalada(loader, item);

		botaoBaixar.setEnabled(!instalada && !baixando);
		botaoBaixar.setText(instalada ? "Versão já instalada" : "Baixar esta versão");

		String avisoLoader = CatalogoVersoes.avisoDoLoader(loader, item.versaoMc);
		String javaNecessario = MinecraftLauncher.javaNecessarioPara(item.versaoMc);

		if (instalada) {
			statusVersao.setText("Já instalada em disco. Java necessário: " + javaNecessario + ".");
		} else {
			statusVersao.setText("Ainda não está no PC — clique em \"Baixar esta versão\".");
		}
		if (avisoLoader != null) {
			statusVersao.setText(avisoLoader);
		}

		atualizarListaDeModpacks();
	}

	private static boolean estaInstalada(String loader, CatalogoVersoes.ItemVersao item) {
		File pastaVersao = GerenciadorVersoes.obterPastaDaVersao(loader, item.codigo);
		return GerenciadorVersoes.obterPastaMDK(pastaVersao) != null;
	}

	private static void baixarVersaoSelecionada() {
		if (versaoSelecionada == null || baixando) {
			return;
		}
		String loader = (String) seletorLoaders.getSelectedItem();
		CatalogoVersoes.ItemVersao item = versaoSelecionada;

		baixando = true;
		botaoBaixar.setEnabled(false);
		botaoJogar.setEnabled(false);
		barraProgresso.setValue(0);
		barraProgresso.setVisible(true);

		new SwingWorker<File, Object[]>() {
			@Override
			protected File doInBackground() throws Exception {
				GerenciadorDownloads.Progresso progresso =
						comConsole((etapa, porcentagem) -> publish(new Object[]{etapa, porcentagem}));
				File pasta = GerenciadorDownloads.garantirVersao(loader, item, progresso);
				// Já deixa o Java certo pronto, para o botão Jogar não ter que esperar depois.
				try {
					MinecraftLauncher.garantirJava(item.versaoMc, progresso);
				} catch (Exception semJava) {
					System.err.println("[BCraftOS Aviso] A versão baixou, mas o Java não: " + semJava.getMessage()
							+ " Vou tentar de novo quando você clicar em Jogar.");
				}
				return pasta;
			}

			@Override
			protected void process(List<Object[]> avisos) {
				Object[] ultimo = avisos.get(avisos.size() - 1);
				statusVersao.setText((String) ultimo[0]);
				barraProgresso.setValue((Integer) ultimo[1]);
			}

			@Override
			protected void done() {
				baixando = false;
				try {
					get();
					barraProgresso.setValue(100);
					atualizarVersaoSelecionada();
					atualizarListaDeModpacks();
				} catch (Exception erro) {
					Throwable causa = erro.getCause() == null ? erro : erro.getCause();
					barraProgresso.setVisible(false);
					String detalhe = causa.getMessage();
					if (detalhe == null || detalhe.isBlank()) {
						detalhe = causa.getClass().getSimpleName();
					}
					statusVersao.setText("Falha no download. Veja a mensagem abaixo.");
					JOptionPane.showMessageDialog(janela,
							"Não consegui baixar essa versão:\n\n" + detalhe
									+ "\n\nConfira sua internet e tente de novo.",
							"Erro no download", JOptionPane.ERROR_MESSAGE);
					botaoBaixar.setEnabled(true);
				}
			}
		}.execute();
	}

	// ------------------------------------------------------------------
	// Modpacks e início do jogo
	// ------------------------------------------------------------------

	private static void atualizarListaDeModpacks() {
		seletorModpacks.setModel(new DefaultComboBoxModel<>());

		if (versaoSelecionada == null) {
			return;
		}
		String loader = (String) seletorLoaders.getSelectedItem();
		File pastaVersao = GerenciadorVersoes.obterPastaDaVersao(loader, versaoSelecionada.codigo);

		if (GerenciadorVersoes.obterPastaMDK(pastaVersao) == null) {
			statusModpack.setText("Baixe a versão para poder escolher o modpack.");
			botaoJogar.setEnabled(false);
			return;
		}

		GerenciadorModpacks.configurarDiretoriosIniciais(pastaVersao);
		DefaultComboBoxModel<String> modelo = new DefaultComboBoxModel<>();
		for (String nome : GerenciadorModpacks.listarModpacksDisponiveis(pastaVersao)) {
			modelo.addElement(nome);
		}
		seletorModpacks.setModel(modelo);
		atualizarStatusModpack();
	}

	/** O modo normal só existe para o Forge até a 1.12.2; nas outras combinações fica desligado. */
	private static void atualizarModoNormal() {
		if (checkModoNormal == null) {
			return;
		}
		String loader = (String) seletorLoaders.getSelectedItem();
		boolean disponivel = versaoSelecionada != null
				&& ModoNormal.disponivelPara(loader, versaoSelecionada.versaoMc);
		checkModoNormal.setEnabled(disponivel);
		if (!disponivel) {
			checkModoNormal.setSelected(false);
		}
	}

	private static void atualizarStatusModpack() {
		atualizarModoNormal();
		Object escolhido = seletorModpacks.getSelectedItem();
		if (escolhido == null) {
			statusModpack.setText(" ");
			botaoJogar.setEnabled(false);
			return;
		}
		botaoJogar.setEnabled(!baixando);
		statusModpack.setText("Modpack selecionado: " + escolhido
				+ ". Coloque os .jar dentro da pasta modpacks dessa versão.");
	}

	private static void iniciarJogo() {
		if (versaoSelecionada == null) {
			return;
		}
		String loader = (String) seletorLoaders.getSelectedItem();
		String modpackSelecionado = (String) seletorModpacks.getSelectedItem();

		if (modpackSelecionado == null || modpackSelecionado.equals("Nenhum modpack encontrado")) {
			JOptionPane.showMessageDialog(janela,
					"Nenhum modpack encontrado para essa versão.",
					"Erro", JOptionPane.ERROR_MESSAGE);
			return;
		}

		File pastaVersao = GerenciadorVersoes.obterPastaDaVersao(loader, versaoSelecionada.codigo);
		File pastaMDK = GerenciadorVersoes.obterPastaMDK(pastaVersao);
		if (pastaMDK == null) {
			JOptionPane.showMessageDialog(janela,
					"Baixe a versão antes de jogar.",
					"Erro", JOptionPane.ERROR_MESSAGE);
			return;
		}

		// Falta o Java dessa versão? Baixa sozinho (Temurin) e continua daqui quando terminar.
		String javaNecessario = MinecraftLauncher.javaNecessarioPara(versaoSelecionada.versaoMc);
		if (MinecraftLauncher.procurarJava(javaNecessario) == null) {
			baixarJavaEDepoisJogar(versaoSelecionada.versaoMc, javaNecessario);
			return;
		}

		janela.setVisible(false);
		try {
			GerenciadorModpacks.aplicarModpack(pastaVersao, pastaMDK, modpackSelecionado);
			Runnable aoFinalizar = () -> SwingUtilities.invokeLater(BCraftOS1::menuPrincipal);
			if (checkModoNormal.isSelected() && ModoNormal.disponivelPara(loader, versaoSelecionada.versaoMc)) {
				ModoNormal.iniciar(pastaMDK, versaoSelecionada.versaoMc, versaoSelecionada.codigo,
						MinecraftLauncher.nickValido(jogadorAutenticadoID), aoFinalizar);
			} else {
				MinecraftLauncher.iniciar(pastaMDK, versaoSelecionada.versaoMc, jogadorAutenticadoID, aoFinalizar);
			}
		} catch (IllegalStateException diagnostico) {
			// Aqui chega o resultado da checagem: o motivo real, em vez de um erro seco.
			janela.setVisible(true);
			JOptionPane.showMessageDialog(janela, diagnostico.getMessage(),
					"Não foi possível iniciar", JOptionPane.WARNING_MESSAGE);
		} catch (IOException ex) {
			System.err.println("[BCraftOS Erro] Falha crítica ao preparar o modpack: " + ex.getMessage());
			JOptionPane.showMessageDialog(null,
					"Falha ao preparar o modpack: " + ex.getMessage(),
					"Erro", JOptionPane.ERROR_MESSAGE);
			janela.setVisible(true);
		}
	}

	/**
	 * Faz o andamento dos downloads aparecer TAMBÉM no console (e, por consequência, no log):
	 * uma linha a cada 10% e uma a cada etapa nova. Assim dá para ver que está trabalhando,
	 * e quando termina, sem depender só da barra da janela.
	 */
	private static GerenciadorDownloads.Progresso comConsole(GerenciadorDownloads.Progresso destino) {
		final int[] ultimoBalde = {-1};
		final String[] ultimaEtapa = {""};
		return (etapa, porcentagem) -> {
			int balde = porcentagem / 10;
			boolean download = etapa.startsWith("Baixando");
			if (balde != ultimoBalde[0] || (!download && !etapa.equals(ultimaEtapa[0]))) {
				System.out.println("[BCraftOS] " + etapa + " (" + porcentagem + "%)");
				ultimoBalde[0] = balde;
				ultimaEtapa[0] = etapa;
			}
			destino.atualizar(etapa, porcentagem);
		};
	}

	/** Baixa o JDK em segundo plano mostrando a barra; quando termina, clica em Jogar de novo. */
	private static void baixarJavaEDepoisJogar(String versaoMc, String javaNecessario) {
		if (baixando) {
			return;
		}
		baixando = true;
		botaoBaixar.setEnabled(false);
		botaoJogar.setEnabled(false);
		barraProgresso.setValue(0);
		barraProgresso.setVisible(true);
		statusVersao.setText("Esta versão precisa do Java " + javaNecessario + ". Baixando sozinho...");

		new SwingWorker<String, Object[]>() {
			@Override
			protected String doInBackground() throws Exception {
				return MinecraftLauncher.garantirJava(versaoMc,
						comConsole((etapa, porcentagem) -> publish(new Object[]{etapa, porcentagem})));
			}

			@Override
			protected void process(List<Object[]> avisos) {
				Object[] ultimo = avisos.get(avisos.size() - 1);
				statusVersao.setText((String) ultimo[0]);
				barraProgresso.setValue((Integer) ultimo[1]);
			}

			@Override
			protected void done() {
				baixando = false;
				try {
					get();
					barraProgresso.setVisible(false);
					atualizarVersaoSelecionada();
					iniciarJogo(); // agora o Java existe: segue o fluxo normal
				} catch (Exception erro) {
					Throwable causa = erro.getCause() == null ? erro : erro.getCause();
					barraProgresso.setVisible(false);
					atualizarVersaoSelecionada();
					JOptionPane.showMessageDialog(janela,
							"Não consegui baixar o Java " + javaNecessario + " sozinho:\n\n" + causa.getMessage()
									+ "\n\nConfira a internet e clique em Jogar de novo.",
							"Java não instalado", JOptionPane.ERROR_MESSAGE);
				}
			}
		}.execute();
	}

	/**
	 * Exclui a conta que está logada. Só o dono consegue: é preciso digitar a senha dela,
	 * e o launcher nunca pede o nome de outra conta, então não dá para apagar a de terceiros.
	 */
	private static void excluirContaAtiva() {
		if (BCraftOSproject1.BCraftOS1login.InfoUsuarios.contaProtegida(jogadorAutenticadoID)) {
			JOptionPane.showMessageDialog(janela, "Esta conta é protegida e não pode ser excluída.",
					"Excluir conta", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		javax.swing.JPasswordField campoSenha = new javax.swing.JPasswordField(18);
		Object[] conteudo = {
				"Isso apaga a conta \"" + jogadorAutenticadoID + "\" e não tem volta.",
				"Digite a senha dela para confirmar:", campoSenha };
		int resposta = JOptionPane.showConfirmDialog(janela, conteudo, "Excluir conta",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (resposta != JOptionPane.OK_OPTION) {
			return;
		}

		String senha = new String(campoSenha.getPassword());
		try {
			boolean apagou = BCraftOSproject1.BCraftOS1login.InfoUsuarios.excluirUsuario(jogadorAutenticadoID, senha);
			if (!apagou) {
				JOptionPane.showMessageDialog(janela, "Senha incorreta. A conta não foi excluída.",
						"Excluir conta", JOptionPane.ERROR_MESSAGE);
				return;
			}
		} catch (IOException | java.security.NoSuchAlgorithmException ex) {
			JOptionPane.showMessageDialog(janela, "Não consegui excluir a conta: " + ex.getMessage(),
					"Erro", JOptionPane.ERROR_MESSAGE);
			return;
		}

		JOptionPane.showMessageDialog(janela, "Conta excluída.", "Excluir conta", JOptionPane.INFORMATION_MESSAGE);
		janela.dispose();
		SwingUtilities.invokeLater(() -> new BCraftOSproject1.BCraftOS1login.BCraftOS1login().setVisible(true));
	}

	public static void definirJogadorAtivo(String nomeTitular) {
		jogadorAutenticadoID = nomeTitular;
		GerenciadorVersoes.definirContaAtiva(nomeTitular);
	}

	public static void main(String[] args) {
		RegistroLogs.iniciar();
		SwingUtilities.invokeLater(BCraftOS1::menuPrincipal);
	}
}
