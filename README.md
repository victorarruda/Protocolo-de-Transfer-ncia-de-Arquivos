# Servidor de Transferência de Arquivos TCP

Este repositório contém uma implementação de um servidor de transferência de arquivos em Java, utilizando uma arquitetura cliente-servidor com suporte a múltiplos clientes simultâneos.

## Funcionalidades

- **Navegação de Diretórios:** Comandos `PWD`, `CWD` e `LIST`.
- **Gerenciamento de Arquivos:** Criação de pastas (`MKDIR`), renomeação (`RENAME`) e exclusão (`RM`).
- **Transferência de Arquivos:** Upload e Download de binários via canal multiplexado.
- **Segurança:** Verificação de integridade de arquivos via checksum SHA-256.
- **Concorrência:** Modelo *Thread-per-Connection* para alta taxa de resposta.

## Tecnologias Utilizadas

- **Linguagem:** Java
- **Comunicação:** Sockets TCP (java.net)
- **I/O:** Java NIO.2 e Data Streams
- **Segurança:** MessageDigest (SHA-256)

## Como Executar

### 1. Compilação
Na raiz do projeto, execute os comandos abaixo para compilar as classes:
```bash
mkdir bin
javac -d bin src/*.java
```

### 2. Iniciar o Servidor
```bash
java -cp bin TCPServer
```

### 3. Iniciar o Cliente
Em um novo terminal, execute:
```bash
java -cp bin TCPClient
```

## Documentação
Para detalhes técnicos aprofundados sobre o protocolo e decisões de implementação, consulte o documento técnico.
