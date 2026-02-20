# Guia de Distribuição (Windows) — Assistente de Redes

Este guia resolve o erro do .cfg e garante que o app rode em qualquer máquina.

## ✅ Regra de ouro (Portable)
Quando você gera o app com `jpackage --type app-image`, **não existe “um EXE sozinho”**.
Você deve distribuir **a pasta inteira**:

`dist\AssistenteRedes\`

Ela precisa conter:
- `AssistenteRedes.exe`
- `AssistenteRedes.cfg`
- pasta `app\`
- pasta `runtime\` (ou `lib\`, dependendo do build)

> **Não renomeie o EXE.**  
Se renomear, o Windows procura `NOME_DO_EXE.cfg` e dá o erro “No such file or directory”.

---

## ✅ Opção A — Portable (recomendado para enviar por ZIP/Drive/WhatsApp)

### 1) Gerar o portable
1. Defina o JavaFX (uma vez):
   `setx JAVAFX_HOME C:\javafx-sdk-25.0.2`

2. Rode:
   `scripts\build-exe-portable.bat`

### 2) Gerar ZIP pronto para enviar
Rode:
`scripts\pack-portable-zip.bat`

Saída:
`release\AssistenteRedes-Portable-Windows.zip`

### 3) Como o usuário final executa
1) Extrair o ZIP  
2) Abrir a pasta (conteúdo do ZIP)  
3) Clicar em `AssistenteRedes.exe`

---

## ✅ Opção B — Instalador (Setup) com atalho no Desktop/Menu Iniciar

> Para gerar instalador com `jpackage --type exe` pode ser necessário o **WiX Toolset** no Windows.
> Se o comando falhar, instale o WiX e tente novamente.

1) Rode:
`scripts\build-installer-exe.bat`

Saída:
`release\(arquivo .exe do instalador)`

---

## 🧯 Solução rápida para o erro do .cfg
Se o usuário baixou e o exe virou:
`AssistenteRedes (1).exe`

Ele vai procurar:
`AssistenteRedes (1).cfg`

✅ Corrija assim:
- Renomeie o EXE para `AssistenteRedes.exe` **OU**
- Renomeie `AssistenteRedes.cfg` para o mesmo nome do EXE.

---

## ✅ Dicas para distribuir sem dor de cabeça
- Sempre enviar em **ZIP** (não mandar arquivos soltos).
- Evitar mandar pelo WhatsApp sem ZIP (ele renomeia e pode quebrar o cfg).
- Se o Windows bloquear (SmartScreen):
  - Botão direito → Propriedades → **Desbloquear**
