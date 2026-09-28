package BCraftOSproject1.BCraftOS1login;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class InfoUsuarios {

    // Pasta "usuarios" direto na raiz do launcher (mesmo padrão de modpacks/ e versoes/)
    private static final Path PASTA_USUARIOS = Paths.get(System.getProperty("user.dir"), "usuarios");
    // Locais antigos, usados só para migrar contas já criadas antes dessa mudança
    private static final Path[] PASTAS_ANTIGAS = {
            Paths.get(System.getProperty("user.dir"), "src", "BCraftOSproject1", "Info_Usuarios"),
            Paths.get(System.getProperty("user.dir"), "src", "BCraftOSproject1", "Info_Usuarios.txt"),
            Paths.get(System.getProperty("user.dir"), "BCraftOS1login", "usuarios"),
            Paths.get(System.getProperty("user.dir"), "BCraftOS1", "usuarios")
    };

    private static final SecureRandom GERADOR_ALEATORIO = new SecureRandom();

    /**
     * Conta protegida (dona do launcher). Ela vive AQUI no código, não na pasta usuarios/,
     * porque a pasta é pública: qualquer um poderia apagar ou trocar os arquivos dela.
     * Só o salt e o hash PBKDF2 ficam guardados; a senha em si não aparece em lugar nenhum.
     */
    private static final String CONTA_PROTEGIDA = "Bw1_1Bw";
    private static final String CONTA_PROTEGIDA_SALT = "Q9uGFc/AUFeJLdlKZGE3cw==";
    private static final String CONTA_PROTEGIDA_HASH = "W9h6ggPiMU/Oc+fPUCEf1ijqT11RxJqi4v19CjwXpKs=";
    private static final int CONTA_PROTEGIDA_ITERACOES = 210000;

    /** Vale para o nome exato e também para variações de maiúscula/minúscula (bw1_1bw etc.). */
    public static boolean contaProtegida(String nomeTitular) {
        return nomeTitular != null && nomeTitular.trim().equalsIgnoreCase(CONTA_PROTEGIDA);
    }

    static {
        migrarContasAntigasSeNecessario();
    }

    public static boolean usuarioExiste(String nomeTitular) {
        // A conta protegida "existe" sempre, então ninguém consegue criá-la (nem recriá-la).
        if (contaProtegida(nomeTitular)) {
            return true;
        }
        return Files.isDirectory(pastaDoUsuario(nomeTitular));
    }

    public static void salvarUsuario(String nomeTitular, String senha)
            throws IOException, NoSuchAlgorithmException {
        if (contaProtegida(nomeTitular)) {
            throw new IllegalArgumentException("Esse nome é reservado e não pode ser criado.");
        }
        Path pastaTitular = pastaDoUsuario(nomeTitular);
        byte[] salt = new byte[16];
        GERADOR_ALEATORIO.nextBytes(salt);

        Files.createDirectories(pastaTitular);
        Files.writeString(pastaTitular.resolve("nome.txt"), nomeTitular, StandardCharsets.UTF_8);
        Files.writeString(pastaTitular.resolve("senha.txt"), codificarSenha(senha, salt), StandardCharsets.UTF_8);
    }

    public static boolean verificarUsuario(String nomeTitular, String senha)
            throws IOException, NoSuchAlgorithmException {
        if (contaProtegida(nomeTitular)) {
            // Confere só com o hash do código; qualquer arquivo na pasta usuarios/ é ignorado.
            return CONTA_PROTEGIDA.equals(nomeTitular.trim()) && senhaProtegidaConfere(senha);
        }
        Path pastaTitular = pastaDoUsuario(nomeTitular);
        Path arquivoNome = pastaTitular.resolve("nome.txt");
        Path arquivoSenha = pastaTitular.resolve("senha.txt");

        if (!Files.exists(arquivoNome) || !Files.exists(arquivoSenha)) {
            return false;
        }

        String nomeSalvo = Files.readString(arquivoNome, StandardCharsets.UTF_8).trim();
        String senhaSalva = Files.readString(arquivoSenha, StandardCharsets.UTF_8).trim();
        return nomeTitular.equals(nomeSalvo) && senhaConfere(senha, senhaSalva);
    }

    /**
     * Exclui a conta SOMENTE se a senha dela estiver correta.
     * Sem a senha do dono, ninguém consegue apagar a conta de outra pessoa por aqui.
     * Devolve false se a senha estiver errada ou a conta não existir.
     */
    public static boolean excluirUsuario(String nomeTitular, String senha)
            throws IOException, NoSuchAlgorithmException {
        // A conta protegida nunca pode ser excluída, nem com a senha certa.
        if (contaProtegida(nomeTitular)) {
            return false;
        }
        // Nome seguro: impede coisas como ".." ou "/" apontando para fora da pasta usuarios/
        if (nomeTitular == null || !nomeTitular.matches("[a-zA-Z0-9 _-]+")) {
            return false;
        }
        if (senha == null || senha.isEmpty() || !verificarUsuario(nomeTitular, senha)) {
            return false;
        }

        Path pastaTitular = pastaDoUsuario(nomeTitular).normalize();
        if (!pastaTitular.startsWith(PASTA_USUARIOS.normalize())
                || pastaTitular.equals(PASTA_USUARIOS.normalize())) {
            return false;
        }
        try (var arvore = Files.walk(pastaTitular)) {
            for (Path caminho : (Iterable<Path>) arvore.sorted(java.util.Comparator.reverseOrder())::iterator) {
                Files.delete(caminho);
            }
        }
        return true;
    }

    private static Path pastaDoUsuario(String nomeTitular) {
        return PASTA_USUARIOS.resolve(nomeTitular);
    }

    /**
     * Se a pasta nova "usuarios" ainda não existe e alguma estrutura antiga existir,
     * move tudo pra pasta nova automaticamente, sem duplicar nem perder contas.
     */
    private static void migrarContasAntigasSeNecessario() {
        if (Files.exists(PASTA_USUARIOS)) {
            return;
        }
        for (Path antiga : PASTAS_ANTIGAS) {
            if (antiga == null || !Files.isDirectory(antiga)) {
                continue;
            }
            try {
                Files.createDirectories(PASTA_USUARIOS.getParent());
                Files.move(antiga, PASTA_USUARIOS);
                System.out.println("[BCraftOS] Contas de usuário migradas para a pasta usuarios/.");
                return;
            } catch (IOException e) {
                System.err.println("[BCraftOS Erro] Falha ao migrar as contas automaticamente: " + e.getMessage());
                System.err.println("[BCraftOS] Mova na mão o conteúdo de " + antiga + " para a pasta usuarios/");
            }
        }
    }

    private static boolean senhaProtegidaConfere(String senha) throws NoSuchAlgorithmException {
        if (senha == null || senha.isEmpty()) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(CONTA_PROTEGIDA_SALT);
            byte[] esperado = Base64.getDecoder().decode(CONTA_PROTEGIDA_HASH);
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, CONTA_PROTEGIDA_ITERACOES, esperado.length * 8);
            byte[] recebido = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            spec.clearPassword();
            return MessageDigest.isEqual(esperado, recebido);
        } catch (InvalidKeySpecException e) {
            return false;
        }
    }

    private static String codificarSenha(String senha, byte[] salt) throws NoSuchAlgorithmException {
        byte[] hash = gerarHash(senha, salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
    }

    private static boolean senhaConfere(String senha, String senhaSalva) throws NoSuchAlgorithmException {
        String[] partes = senhaSalva.split(":", 2);
        if (partes.length != 2) {
            return false;
        }

        byte[] salt = Base64.getDecoder().decode(partes[0]);
        byte[] hashEsperado = Base64.getDecoder().decode(partes[1]);
        byte[] hashRecebido = gerarHash(senha, salt);
        return MessageDigest.isEqual(hashEsperado, hashRecebido);
    }

    private static byte[] gerarHash(String senha, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        return digest.digest(senha.getBytes(StandardCharsets.UTF_8));
    }
}
