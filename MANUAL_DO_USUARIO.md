# Manual do Usuário — Assistente de Redes

## Visão geral
O Assistente de Redes é um aplicativo desktop (Java + JavaFX) para **diagnóstico rápido** de conectividade, DNS, portas, HTTP/HTTPS e SSL — com histórico e exportação.

### Onde o app salva arquivos
- Histórico completo: **Documentos\AssistenteRedes\history.jsonl**
- Exportação: **Documentos\AssistenteRedes\history.csv**

---

## Como usar (passo a passo)
1. Abra o Assistente (duplo clique no EXE).
2. Escolha a ferramenta no menu lateral.
3. Preencha o alvo (host/IP/URL) e parâmetros (porta, timeout…).
4. Clique em **Executar**.
5. Veja o resultado no painel **Saída**.
6. A execução fica salva em **Histórico**.
7. Use **Exportar histórico (CSV)** para gerar um relatório.

---

## Ferramentas e exemplos

### 1) Status
**O que faz:** mostra hostname, IP local (heurística) e sistema operacional.  
**Use para:** confirmar rapidamente o “contexto” antes de diagnosticar.

**Exemplo:** após conectar na VPN, confira se o IP/rota esperada mudou.

---

### 2) Ping (ICMP)
**O que faz:** testa conectividade e mede latência/perda (usa ferramenta do Windows).  
**Como interpretar:**
- Responde sem perdas → ok
- Timeout → pode ser bloqueio ICMP, host offline ou rota quebrada
- Alta latência/perda → instabilidade

**Exemplos de alvo:**
- `8.8.8.8` (internet)
- `1.1.1.1` (internet)
- `servidor-interno` (rede/VPN)

---

### 3) DNS Lookup
**O que faz:** resolve domínio em IP(s).  
**Modos:**
- **Java (InetAddress)**: usa o DNS do sistema via Java
- **SO (nslookup)**: usa o nslookup do Windows

**Exemplos:**
- `google.com` → vários IPs
- `meusistema.com.br` → confirmar se aponta pro IP correto

**Problemas comuns:**
- IP errado → cache/propagação/zone
- Sem resposta → DNS corporativo bloqueando

---

### 4) Reverse DNS (PTR)
**O que faz:** dado um IP, retorna o nome associado (quando existe).  
**Exemplo:** `8.8.8.8` → geralmente `dns.google` (pode variar).

---

### 5) Porta TCP
**O que faz:** testa se **consegue abrir conexão TCP** com host:porta (Java puro).  
**Interpretação:**
- ✅ conectou → acessível
- ❌ recusada → porta fechada/serviço parado
- ⏱ timeout → firewall/rota/filtragem

**Exemplos:**
- `google.com:443`
- `servidor:3389` (RDP)
- `servidor:5432` (PostgreSQL)

---

### 6) HTTP/HTTPS
**O que faz:** faz requisição e mostra **status code**, tempo e headers básicos (Java puro).

**Exemplos:**
- `https://seusite.com`:
  - `200` OK → no ar
  - `301/302` → redirecionamento
  - `403` → bloqueio/permissão/WAF
  - `500` → erro no servidor

**Dica:** diferencia “porta 443 abre” de “aplicação responde”.

---

### 7) SSL Cert
**O que faz:** lê certificado SSL/TLS (Java puro): protocolo, cipher, subject, issuer e validade.

**Exemplos de uso:**
- verificar **expiração** (NotAfter)
- validar emissor/subject do certificado
- checar protocolo/cipher em produção

---

### 8) Traceroute
**O que faz:** mostra os saltos até o destino (usa ferramenta do Windows).  
**Use para:** descobrir onde a rota “quebra”.

---

### 9) Interfaces (Java)
**O que faz:** lista interfaces, IPs, MAC, MTU, flags (Java puro).  
**Use para:** identificar interface ativa, interface VPN, MAC para cadastro.

---

### 10) Rotas (SO)
**O que faz:** tabela de rotas do sistema (`route print`).  
**Use para:** investigar acesso via VPN/sub-redes.

---

### 11) ARP (SO)
**O que faz:** tabela ARP (`arp -a`).  
**Use para:** conflitos de IP, descobrir MAC na LAN.

---

### 12) Netstat (SO)
**O que faz:** conexões e portas (`netstat -ano`).  
**Modos:** LISTEN, ESTABLISHED ou tudo.

**Use para:** checar se um serviço está escutando e conexões ativas.

---

### 13) Histórico
**O que faz:** lista execuções; filtros por ação e busca.  
**Dica:** duplo clique abre o resultado completo na Saída.

---

### 14) Exportar histórico (CSV)
**O que faz:** gera **Documentos\AssistenteRedes\history.csv**.  
**Use para:** evidência para cliente/suporte.

---

## Checklist rápido de troubleshooting
1) Status
2) Ping
3) DNS
4) Porta TCP
5) HTTP/HTTPS
6) SSL
7) Traceroute
8) Rotas/ARP/Netstat (avançado)
