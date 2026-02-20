Assistente de Redes (Desktop JavaFX) — Versão Evoluída

O que tem de novo:
- DNS via Java (InetAddress) + reverse DNS
- Teste HTTP/HTTPS (status code, tempo, headers básicos)
- Leitura de certificado SSL (subject/issuer/validade/cipher)
- Interfaces de rede via Java (NetworkInterface) — sem depender de ipconfig
- Mantém ferramentas do SO: traceroute/rotas/arp/netstat (quando disponíveis)

Como rodar (Windows):
1) Instale JavaFX SDK (ex: C:\javafx-sdk-25.0.2) e defina:
   setx JAVAFX_HOME C:\javafx-sdk-25.0.2

2) Execute:
   scripts\run-windows.bat

Gerar executável portátil:
- scripts\build-exe-portable.bat
Saída: dist\AssistenteRedes\AssistenteRedes.exe

Documentação detalhada:
- docs\EXPLICACAO_CODIGO.md


Distribuição (Windows): veja docs\GUIA_DISTRIBUICAO_WINDOWS.md


Arquivos do app:
- Documentos\AssistenteRedes\history.jsonl
- Documentos\AssistenteRedes\history.csv
