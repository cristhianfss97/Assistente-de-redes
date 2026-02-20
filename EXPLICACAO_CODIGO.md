# Explicação do Projeto (versão evoluída)

## Estrutura
- `src/main/java/com/assistente/AssistenteRedesApp.java`  
  App principal: interface JavaFX + execução de testes + histórico.
- `src/main/java/com/assistente/TinyJson.java`  
  Mini JSON (string->string) para salvar histórico sem libs externas.
- `src/main/java/com/assistente/HistRow.java`  
  Modelo de linha para a tabela de histórico (TableView).
- `src/main/resources/styles.css`  
  Tema visual (dark) e saída com fundo branco/texto preto.
- `scripts/`  
  Scripts Windows para rodar, gerar JAR e empacotar EXE via `jpackage`.

---

## AssistenteRedesApp.java — o que foi feito

### 1) UI e layout
- Usa `BorderPane`:
  - Top: Header com botões **Copiar saída / Limpar / Exportar CSV**
  - Left: Sidebar (menu de ferramentas)
  - Center: `TabPane` + `TextArea` de saída
- `ScrollPane` envolve a sidebar e o centro para permitir rolagem em telas menores.

### 2) Execução sem travar a interface
- `ExecutorService pool` roda tarefas em background.
- `runInBg(...)` executa a tarefa e, em caso de erro, mostra stacktrace no Output.

### 3) Ferramentas (implementação)
- **Status**: pega hostname e IP local (heurística com DatagramSocket) e OS.
- **Ping**: chama o comando do SO (`ping`) pois ICMP puro em Java não é confiável cross-platform.
- **DNS Lookup**:
  - Modo Java: `InetAddress.getAllByName(...)`
  - Modo SO: `nslookup`
- **Reverse DNS**: `InetAddress.getByName(ip).getCanonicalHostName()`
- **Porta TCP**: Java puro com `Socket.connect(...)`
- **HTTP/HTTPS**: Java puro com `HttpURLConnection`
  - Retorna status code, tempo, IP do host e alguns headers.
- **SSL Cert**: Java puro com `SSLSocket`
  - Faz handshake e lê certificado X509 (subject/issuer/validade/cipher/protocolo).
- **Traceroute / Rotas / ARP / Netstat**: comandos do sistema (SO).
  - Antes de executar, o app verifica se o comando existe usando `where` (Windows).

### 4) Histórico e exportação
- Cada execução chama `log(action, target, result)`:
  - Salva em `history.jsonl` (1 JSON por linha).
- Aba Histórico:
  - filtra por ação, busca texto, limite de linhas
  - duplo clique abre resultado completo no Output
- Exportação CSV:
  - lê `history.jsonl` e gera `history.csv` no diretório atual

---

## TinyJson.java — por que existe
- Evita dependências extras (Gson/Jackson).
- Serve apenas para:
  - salvar o histórico (string->string)
  - ler o histórico

---

## HistRow.java — por que existe
- JavaFX TableView trabalha muito bem com `StringProperty`.
- Mantém a tabela reativa e simples.

---

## Como evoluir mais
Sugestões:
- Relatório PDF (em vez de CSV)
- Monitoramento contínuo (ping loop, gráfico)
- Teste de DNS por servidor específico (exigiria implementação de DNS client)
- Modo “Checklist” (executa sequência de testes com 1 clique)
