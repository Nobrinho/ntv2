---
name: ntv2-firetv-logs
description: Coleta e interpreta logs do NTV no Fire TV via adb (travas, perda de quadros, decoder, modo de tela, download). Use quando o usuário relatar trava/engasgo/erro de vídeo no Fire TV ou pedir para capturar log.
---

# Diagnóstico de player no Fire TV

Lição do projeto: relatos de "trava" foram **decoder/exibição** (MediaTek), não rede/Telegram. Capture o log **antes** de mexer em rede.

## Coleta (somente leitura, sempre permitido)

1. `adb devices`. Se vazio, peça o IP ao usuário e use `adb connect <ip>:5555`. Aparelhos conhecidos: AFTKM (Stick 4K 1ª ger.) e AFTMM "mantis"; os IPs mudam, confirme.
2. O buffer padrão (256 KB) enche com spam da Alexa; aumente temporariamente: `adb logcat -G 4M`.
3. Limpe e capture enquanto o usuário reproduz o problema:
   ```
   adb logcat -c
   adb logcat -s NtvPlayer NtvDownload NtvDiskWindow > <scratchpad>/firetv.log
   ```
   Rode em background, peça ao usuário para reproduzir, pare e leia o arquivo.
4. Às vezes mensagens do app não aparecem no logcat do Fire OS: confirme também pelo que o usuário vê na tela. Para falhas nativas, procure `media.codec`, `SIGSEGV`, `OMX.MTK`.

## Como ler

- `NtvPlayer`: resumo de quadros a cada 10 s, `decodificador de vídeo:`, `modo da tela:`.
- Quadros perdidos com saída 1080p em vídeo 4K: o modo de tela (DisplayModeChooser) deveria sair em 4K@23.976.
- `OMX.MTK.VIDEO.DECODER.AVC` congelando ou com DECODE ERROR em ponto fixo: o app usa decoder por software só em janelas (DecoderWindows). Software no filme inteiro não serve (perde ~60% dos quadros).
- `OMX.MTK.VIDEO.DECODER.VP9` derrubando `media.codec` no 1º quadro: o app desiste após 3 falhas e mostra "Vídeo não suportado neste aparelho" (esperado).

## Cuidados

- O release não remove `Log.i/w`; dá para diagnosticar sem trocar por debug.
- Testes instrumentados: use `am instrument`, **não** `connectedAndroidTest` (ele desinstala o app e apaga o login).
- Não instale/desinstale APK sem pedido do usuário (assinaturas debug e release diferem).
- Termine reportando: aparelho, decoder usado, taxa de quadros perdidos e a causa provável, citando linhas do log.
