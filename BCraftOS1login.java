package BCraftOSproject1.BCraftOS1login;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Tela de login/cadastro do BCraftOS.
 *
 * Correção das caixas de texto:
 * - fundo escuro com texto claro (o texto não some mais ao digitar);
 * - altura maior (44px) e largura fixa igual para as três caixas;
 * - label acima do campo, e não do lado, com espaçamento uniforme;
 * - cursor, seleção e foco visíveis (borda roxa quando o campo está ativo);
 * - o campo "Confirme a senha" só aparece quando a conta ainda não existe.
 *
 * O sistema de skins foi removido desta tela: não há mais SkinViewer3D,
 * botão "Trocar Skin" nem carregamento de skin salva.
 */
public class BCraftOS1login extends JFrame {

	private static final Color COR_FUNDO = new Color(18, 18, 18);
	private static final Color COR_PAINEL = new Color(26, 26, 26);
	private static final Color COR_DESTAQUE = new Color(170, 90, 240);
	private static final Color COR_BORDA = new Color(70, 70, 70);
	private static final Color COR_TEXTO = new Color(238, 238, 238);
	private static final Color COR_TEXTO_FRACO = new Color(150, 150, 150);

	private static final int LARGURA_CAMPO = 300;
	private static final int ALTURA_CAMPO = 44;
	private static final Font FONTE_CAMPO = new Font("Arial", Font.PLAIN, 15);
	private static final Font FONTE_ROTULO = new Font("Arial", Font.BOLD, 12);

	private final JTextField campoNome = new JTextField();
	private final JPasswordField campoSenha = new JPasswordField();
	private final JPasswordField campoConfirmacao = new JPasswordField();

	private final JLabel rotuloConfirmacao = new JLabel("Confirme a senha:");
	private final JButton botaoEntrar = new JButton("Entrar / Criar conta");
	private final JLabel legendaAcao = new JLabel(" ");

	private final Border bordaNormal = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(COR_BORDA, 1),
			BorderFactory.createEmptyBorder(0, 12, 0, 12));
	private final Border bordaFoco = BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(COR_DESTAQUE, 2),
			BorderFactory.createEmptyBorder(0, 11, 0, 11));

	public BCraftOS1login() {
		setTitle("BCraftOS - Login");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setResizable(false);

		JPanel painelPrincipal = new JPanel(new BorderLayout());
		painelPrincipal.setBackground(COR_FUNDO);
		painelPrincipal.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
		painelPrincipal.add(montarPainelFormulario(), BorderLayout.CENTER);

		add(painelPrincipal);
		pack();
		setLocationRelativeTo(null);

		atualizarModoFormulario();
		atualizarLegenda();
		SwingUtilities.invokeLater(campoNome::requestFocusInWindow);
	}

	private JPanel montarPainelFormulario() {
		JPanel painel = new JPanel(new GridBagLayout());
		painel.setBackground(COR_PAINEL);
		painel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(COR_BORDA, 1),
				BorderFactory.createEmptyBorder(22, 26, 22, 26)));

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;

		JLabel titulo = new JLabel("BCraftOS Launcher");
		titulo.setFont(new Font("Arial", Font.BOLD, 24));
		titulo.setForeground(COR_DESTAQUE);
		c.insets = new Insets(0, 0, 4, 0);
		painel.add(titulo, c);

		JLabel subtitulo = new JLabel("Entre com sua conta ou crie uma nova automaticamente.");
		subtitulo.setFont(new Font("Arial", Font.PLAIN, 12));
		subtitulo.setForeground(COR_TEXTO_FRACO);
		c.gridy = 1;
		c.insets = new Insets(0, 0, 18, 0);
		painel.add(subtitulo, c);

		adicionarCampo(painel, c, 2, "Nome do titular:", campoNome);
		adicionarCampo(painel, c, 4, "Senha:", campoSenha);

		rotuloConfirmacao.setFont(FONTE_ROTULO);
		rotuloConfirmacao.setForeground(COR_TEXTO);
		c.gridy = 6;
		c.insets = new Insets(0, 0, 6, 0);
		painel.add(rotuloConfirmacao, c);

		estilizarCampo(campoConfirmacao);
		c.gridy = 7;
		c.insets = new Insets(0, 0, 6, 0);
		painel.add(campoConfirmacao, c);

		legendaAcao.setFont(new Font("Arial", Font.PLAIN, 11));
		legendaAcao.setForeground(COR_TEXTO_FRACO);
		c.gridy = 8;
		c.insets = new Insets(2, 0, 14, 0);
		painel.add(legendaAcao, c);

		estilizarBotao(botaoEntrar);
		botaoEntrar.addActionListener(this::processarLogin);
		c.gridy = 9;
		c.insets = new Insets(0, 0, 0, 0);
		painel.add(botaoEntrar, c);
		getRootPane().setDefaultButton(botaoEntrar);

		campoNome.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				atualizarModoFormulario();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				atualizarModoFormulario();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				atualizarModoFormulario();
			}
		});

		return painel;
	}

	/** Coloca o rótulo acima do campo e aplica o espaçamento/estilo padrão. */
	private void adicionarCampo(JPanel painel, GridBagConstraints c, int linha,
			String texto, JTextField campo) {
		JLabel rotulo = new JLabel(texto);
		rotulo.setFont(FONTE_ROTULO);
		rotulo.setForeground(COR_TEXTO);

		c.gridy = linha;
		c.insets = new Insets(0, 0, 6, 0);
		painel.add(rotulo, c);

		estilizarCampo(campo);
		c.gridy = linha + 1;
		c.insets = new Insets(0, 0, 16, 0);
		painel.add(campo, c);
	}

	private void estilizarCampo(JTextField campo) {
		campo.setFont(FONTE_CAMPO);
		campo.setOpaque(true);
		campo.setBackground(COR_FUNDO);
		campo.setForeground(COR_TEXTO);
		campo.setCaretColor(COR_DESTAQUE);
		campo.setSelectionColor(COR_DESTAQUE);
		campo.setSelectedTextColor(Color.WHITE);
		campo.setBorder(bordaNormal);
		campo.setPreferredSize(new Dimension(LARGURA_CAMPO, ALTURA_CAMPO));
		campo.setMinimumSize(new Dimension(LARGURA_CAMPO, ALTURA_CAMPO));
		campo.addFocusListener(new java.awt.event.FocusAdapter() {
			@Override
			public void focusGained(java.awt.event.FocusEvent e) {
				campo.setBorder(bordaFoco);
			}

			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				campo.setBorder(bordaNormal);
			}
		});
	}

	private void estilizarBotao(JButton botao) {
		botao.setBackground(COR_DESTAQUE);
		botao.setForeground(Color.BLACK);
		botao.setFont(new Font("Arial", Font.BOLD, 14));
		botao.setFocusPainted(false);
		botao.setPreferredSize(new Dimension(LARGURA_CAMPO, 44));
		botao.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
	}

	/** Mostra o campo de confirmação só quando o nome ainda não tem conta. */
	private void atualizarModoFormulario() {
		String nome = campoNome.getText().trim();
		boolean precisaConfirmar = nome.isEmpty() || !InfoUsuarios.usuarioExiste(nome);

		campoConfirmacao.setVisible(precisaConfirmar);
		rotuloConfirmacao.setVisible(precisaConfirmar);
		if (!precisaConfirmar) {
			campoConfirmacao.setText("");
		}
		atualizarLegenda();
		revalidate();
		repaint();
	}

	private void atualizarLegenda() {
		String nome = campoNome.getText().trim();
		if (nome.isEmpty()) {
			legendaAcao.setText("Digite o nome do titular para continuar.");
		} else if (InfoUsuarios.usuarioExiste(nome)) {
			legendaAcao.setText("Conta encontrada: informe a senha para entrar.");
		} else {
			legendaAcao.setText("Conta nova: confirme a senha para criar.");
		}
	}

	private void processarLogin(ActionEvent event) {
		String nomeTitular = campoNome.getText().trim();
		String senha = new String(campoSenha.getPassword());

		try {
			validarNomeTitular(nomeTitular);
			if (InfoUsuarios.usuarioExiste(nomeTitular)) {
				entrar(nomeTitular, senha);
			} else {
				criarConta(nomeTitular, senha, new String(campoConfirmacao.getPassword()));
			}
		} catch (Exception exception) {
			JOptionPane.showMessageDialog(this, exception.getMessage(), "Erro",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private void criarConta(String nomeTitular, String senha, String confirmacao) throws Exception {
		if (senha.isEmpty() || !senha.equals(confirmacao)) {
			throw new IllegalArgumentException("As senhas estão vazias ou não coincidem.");
		}
		if (senha.length() < 4) {
			throw new IllegalArgumentException("A senha precisa ter pelo menos 4 caracteres.");
		}
		InfoUsuarios.salvarUsuario(nomeTitular, senha);
		mostrarSucesso("Conta criada e login realizado com sucesso.");
	}

	private void entrar(String nomeTitular, String senha) throws Exception {
		if (!InfoUsuarios.verificarUsuario(nomeTitular, senha)) {
			throw new IllegalArgumentException("Nome ou senha incorretos.");
		}
		mostrarSucesso("Login realizado com sucesso.");
	}

	private void mostrarSucesso(String mensagem) {
		JOptionPane.showMessageDialog(this, mensagem, "BCraftOS", JOptionPane.INFORMATION_MESSAGE);
		dispose();

		BCraftOSproject1.BCraftOS1.BCraftOS1.definirJogadorAtivo(campoNome.getText().trim());

		SwingUtilities.invokeLater(BCraftOSproject1.BCraftOS1.BCraftOS1::menuPrincipal);
	}

	private static void validarNomeTitular(String nomeTitular) {
		if (nomeTitular.isEmpty() || !nomeTitular.matches("[a-zA-Z0-9 _-]+")) {
			throw new IllegalArgumentException(
					"O nome deve conter apenas letras, números, espaços, '_' ou '-'.");
		}
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> new BCraftOS1login().setVisible(true));
	}
}
