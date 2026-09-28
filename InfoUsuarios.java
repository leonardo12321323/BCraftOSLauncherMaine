package BCraftOSproject1.BCraftOS1login;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

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

    static {
        migrarContasAntigasSeNecessario();
    }

    public static boolean usuarioExiste(String nomeTitular) {
        return Files.isDirectory(pastaDoUsuario(nomeTitular));
    }

    public static void salvarUsuario(String nomeTitular, String senha)
            throws IOException, NoSuchAlgorithmException {
        Path pastaTitular = pastaDoUsuario(nomeTitular);
        byte[] salt = new byte[16];
        GERADOR_ALEATORIO.nextBytes(salt);

        Files.createDirectories(pastaTitular);
        Files.writeString(pastaTitular.resolve("nome.txt"), nomeTitular, StandardCharsets.UTF_8);
        Files.writeString(pastaTitular.resolve("senha.txt"), codificarSenha(senha, salt), StandardCharsets.UTF_8);
    }

    public static boolean verificarUsuario(String nomeTitular, String senha)
            throws IOException, NoSuchAlgorithmException {
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
