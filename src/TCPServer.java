import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {

    public static void main(String[] args) throws Exception {

        int port = 6777;
        ServerSocket ss = new ServerSocket(port);
        System.out.println("Servidor FTP Iniciado na porta " + port);
        System.out.println("Aguardando conexoes...");

        while (true) {
            Socket cli = ss.accept(); // bloqueia aguardando conexão
            System.out.println("Nova conexao de: " + cli.getRemoteSocketAddress());
            Conexao con = new Conexao(cli);
            new Thread(con).start(); // inicia atendimento concorrente
        }
    }
}
