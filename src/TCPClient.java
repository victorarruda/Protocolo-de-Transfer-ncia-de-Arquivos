import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class TCPClient {

    public static void main(String[] args) {
        String host = "localhost";
        int port = 6777;

        try (Socket s = new Socket(host, port);
             DataInputStream is = new DataInputStream(s.getInputStream());
             DataOutputStream os = new DataOutputStream(s.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("=============================================");
            System.out.println("   CONECTADO AO SERVIDOR FTP: " + host + ":" + port);
            System.out.println("=============================================");
            System.out.println("\n--- Comandos Disponiveis ---");
            System.out.println("  PWD                    - Mostra o diretorio atual");
            System.out.println("  CWD <dir>              - Muda o diretorio atual");
            System.out.println("  LIST                   - Lista arquivos e pastas (com checksum SHA-256)");
            System.out.println("  MKDIR <dir>            - Cria um novo diretorio");
            System.out.println("  RM <nome>              - Remove um arquivo ou pasta recursivamente");
            System.out.println("  RENAME <velho> <novo>  - Renomeia um arquivo ou pasta");
            System.out.println("  UPLOAD <arquivo>       - Envia um arquivo do seu PC para o servidor");
            System.out.println("  DOWNLOAD <arquivo>     - Baixa um arquivo do servidor para o seu PC");
            System.out.println("  QUIT                   - Encerra a conexao");
            System.out.println("----------------------------\n");

            while (true) {
                System.out.print("ftp> ");
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                String[] parts = input.split(" ");
                String cmd = parts[0].toUpperCase();

                if (cmd.equals("UPLOAD")) {
                    if (parts.length < 2) {
                        System.out.println("[ERRO] Uso: UPLOAD <arquivo_local>");
                        continue;
                    }
                    Path localFile = Paths.get(parts[1]);
                    if (!Files.exists(localFile) || Files.isDirectory(localFile)) {
                        System.out.println("[ERRO] Arquivo local nao encontrado ou e um diretorio.");
                        continue;
                    }
                    long size = Files.size(localFile);
                    // Envia comando UPLOAD com o tamanho embutido
                    os.writeUTF("UPLOAD " + localFile.getFileName() + " " + size);
                    os.flush();

                    // Envia os bytes binários do arquivo
                    try (FileInputStream fis = new FileInputStream(localFile.toFile())) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                        os.flush();
                    }
                    // Lê a confirmação do servidor (ex "200 OK")
                    String response = is.readUTF();
                    imprimirRespostaFormatada(response);

                } else if (cmd.equals("DOWNLOAD")) {
                    if (parts.length < 2) {
                        System.out.println("[ERRO] Uso: DOWNLOAD <arquivo_remoto>");
                        continue;
                    }
                    os.writeUTF("DOWNLOAD " + parts[1]);
                    os.flush();
                    
                    String response = is.readUTF();
                    if (response.startsWith("200 OK")) {
                        String[] lines = response.split("\r\n");
                        long size = Long.parseLong(lines[1]);
                        System.out.println("[INFO] Iniciando download de " + size + " bytes...");
                        
                        Path localFile = Paths.get("downloaded_" + parts[1]);
                        try (FileOutputStream fos = new FileOutputStream(localFile.toFile())) {
                            byte[] buffer = new byte[8192];
                            long bytesReadTotal = 0;
                            while (bytesReadTotal < size) {
                                int bytesToRead = (int) Math.min(buffer.length, (int)(size - bytesReadTotal));
                                int read = is.read(buffer, 0, bytesToRead);
                                if (read == -1) break;
                                fos.write(buffer, 0, read);
                                bytesReadTotal += read;
                            }
                        }
                        System.out.println("[SUCESSO] Download concluido! Arquivo salvo como: " + localFile.toAbsolutePath().toString());
                    } else {
                        imprimirRespostaFormatada(response); // Exibe mensagem de erro (ex 404 Not Found)
                    }

                } else if (cmd.equals("QUIT")) {
                    os.writeUTF("QUIT");
                    imprimirRespostaFormatada(is.readUTF());
                    break;
                } else {
                    // Outros comandos: PWD, CWD, LIST, MKDIR, RM, RENAME
                    os.writeUTF(input);
                    os.flush();
                    String response = is.readUTF();
                    imprimirRespostaFormatada(response);
                }
            }
        } catch (Exception e) {
            System.out.println("[ERRO FATAL] Conexao perdida: " + e.getMessage());
        }
    }

    private static void imprimirRespostaFormatada(String rawResponse) {
        String[] lines = rawResponse.split("\r\n");
        if (lines.length == 0) return;
        
        String header = lines[0];
        if (header.startsWith("200 OK")) {
            System.out.println("[SUCESSO] Operacao concluida.");
            for (int i = 1; i < lines.length; i++) {
                System.out.println("   " + lines[i]);
            }
        } else if (header.startsWith("400") || header.startsWith("404") || header.startsWith("500")) {
            System.out.println("[ERRO] " + header);
            for (int i = 1; i < lines.length; i++) {
                System.out.println("   " + lines[i]);
            }
        } else {
            System.out.println(rawResponse);
        }
    }
}
