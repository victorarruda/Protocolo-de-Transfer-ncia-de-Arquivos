import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Stream;

public class Conexao implements Runnable {

    private Socket cli;
    private DataInputStream is;
    private DataOutputStream os;
    private Path currentDir;

    public Conexao(Socket cli) {
        this.cli = cli;
        this.currentDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    @Override
    public void run() {
        try {
            is = new DataInputStream(cli.getInputStream());
            os = new DataOutputStream(cli.getOutputStream());

            while (true) {
                String line;
                try {
                    line = is.readUTF();
                } catch (EOFException e) {
                    break; // Cliente desconectou normalmente
                }
                
                if (line.trim().isEmpty()) continue;
                System.out.println("[" + cli.getRemoteSocketAddress() + "] Comando recebido: " + line);
                String[] parts = line.split(" ");
                String cmd = parts[0].toUpperCase();

                try {
                    switch (cmd) {
                        case "PWD":
                            tratarPwd();
                            break;
                        case "CWD":
                            tratarCwd(parts);
                            break;
                        case "LIST":
                            tratarList();
                            break;
                        case "MKDIR":
                            tratarMkdir(parts);
                            break;
                        case "RM":
                            tratarRm(parts);
                            break;
                        case "RENAME":
                            tratarRename(parts);
                            break;
                        case "UPLOAD":
                            tratarUpload(parts, is);
                            break;
                        case "DOWNLOAD":
                            tratarDownload(parts);
                            break;
                        case "QUIT":
                            enviarResposta("200 OK", "Desconectado");
                            return; // Encerra o loop e a thread
                        default:
                            enviarResposta("400 Bad Request", "Comando desconhecido: " + cmd);
                    }
                } catch (Exception e) {
                    enviarResposta("500 Internal Server Error", "Erro processando comando: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("Erro na conexao com " + cli.getInetAddress() + ": " + e.getMessage());
        } finally {
            try {
                cli.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Conexao encerrada com " + cli.getRemoteSocketAddress());
        }
    }

    private void enviarResposta(String status, String msg) throws IOException {
        os.writeUTF(status + "\r\n" + msg);
        os.flush();
    }

    private void tratarPwd() throws IOException {
        enviarResposta("200 OK", currentDir.toString());
    }

    private void tratarCwd(String[] parts) throws IOException {
        if (parts.length < 2) {
            enviarResposta("400 Bad Request", "Falta o nome do diretorio");
            return;
        }
        Path target = currentDir.resolve(parts[1]).normalize();
        if (Files.isDirectory(target)) {
            currentDir = target;
            enviarResposta("200 OK", "Diretorio atualizado para " + currentDir.toString());
        } else {
            enviarResposta("404 Not Found", "Diretorio nao existe");
        }
    }

    private void tratarList() throws Exception {
        StringBuilder sb = new StringBuilder();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    sb.append(String.format("[DIR]  %s\n", entry.getFileName().toString()));
                } else {
                    sb.append(String.format("[FILE] %s - SHA256: %s\n", entry.getFileName().toString(), calcularChecksum(entry)));
                }
            }
        }
        if (sb.length() == 0) sb.append("Diretorio vazio.");
        enviarResposta("200 OK", sb.toString());
    }

    private String calcularChecksum(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void tratarMkdir(String[] parts) throws IOException {
        if (parts.length < 2) {
            enviarResposta("400 Bad Request", "Falta o nome do diretorio");
            return;
        }
        Path target = currentDir.resolve(parts[1]);
        if (!Files.exists(target)) {
            Files.createDirectory(target);
            enviarResposta("200 OK", "Diretorio " + parts[1] + " criado.");
        } else {
            enviarResposta("400 Bad Request", "Diretorio ja existe.");
        }
    }

    private void tratarRm(String[] parts) throws IOException {
        if (parts.length < 2) {
            enviarResposta("400 Bad Request", "Falta o nome do arquivo/diretorio");
            return;
        }
        Path target = currentDir.resolve(parts[1]);
        if (Files.exists(target)) {
            if (Files.isDirectory(target)) {
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            } else {
                Files.delete(target);
            }
            enviarResposta("200 OK", "Removido com sucesso.");
        } else {
            enviarResposta("404 Not Found", "Nao encontrado.");
        }
    }

    private void tratarRename(String[] parts) throws IOException {
        if (parts.length < 3) {
            enviarResposta("400 Bad Request", "Faltam argumentos. Uso: RENAME <velho> <novo>");
            return;
        }
        Path oldPath = currentDir.resolve(parts[1]);
        Path newPath = currentDir.resolve(parts[2]);
        if (Files.exists(oldPath)) {
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            enviarResposta("200 OK", "Renomeado com sucesso.");
        } else {
            enviarResposta("404 Not Found", "Nao encontrado.");
        }
    }

    private void tratarUpload(String[] parts, DataInputStream is) throws IOException {
        if (parts.length < 3) {
            enviarResposta("400 Bad Request", "Uso: UPLOAD <nome> <tamanho>");
            return;
        }
        String fileName = parts[1];
        long fileSize;
        try {
            fileSize = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            enviarResposta("400 Bad Request", "Tamanho de arquivo invalido.");
            return;
        }

        Path target = currentDir.resolve(fileName);
        try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
            byte[] buffer = new byte[8192];
            long bytesReadTotal = 0;
            while (bytesReadTotal < fileSize) {
                int bytesToRead = (int) Math.min(buffer.length, fileSize - bytesReadTotal);
                int read = is.read(buffer, 0, bytesToRead);
                if (read == -1) break;
                fos.write(buffer, 0, read);
                bytesReadTotal += read;
            }
        }
        enviarResposta("200 OK", "Upload concluido com sucesso.");
    }

    private void tratarDownload(String[] parts) throws IOException {
        if (parts.length < 2) {
            enviarResposta("400 Bad Request", "Uso: DOWNLOAD <nome>");
            return;
        }
        Path target = currentDir.resolve(parts[1]);
        if (!Files.exists(target) || Files.isDirectory(target)) {
            enviarResposta("404 Not Found", "Arquivo nao encontrado ou eh diretorio.");
            return;
        }
        
        long size = Files.size(target);
        // Primeiro avisamos que deu certo e mandamos o tamanho do arquivo
        enviarResposta("200 OK", String.valueOf(size));
        
        // Depois enviamos os bytes crus
        try (FileInputStream fis = new FileInputStream(target.toFile())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                os.write(buffer, 0, read);
            }
            os.flush();
        }
    }
}
