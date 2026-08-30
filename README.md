# AtendeAuto

Atendimento automático de chamadas de números desconhecidos no Samsung Galaxy A36,
acionando o recurso nativo **Chamada por Texto** (Bixby Text Call) do One UI.

**Status: funcionando.** Testado no aparelho: chamada de número fora dos contatos é
atendida sozinha em modo Chamada por Texto, com a saudação padrão do Samsung.

## Como funciona

Dois componentes independentes no mesmo APK:

- **`AtendeAutoScreeningService`** (`CallScreeningService`, Android 10+) detecta o número
  da chamada recebida — sem precisar de `READ_PHONE_STATE` nem `READ_CALL_LOG` — e checa
  se está nos Contatos ou na allowlist do app. Se for desconhecido, apenas sinaliza a
  intenção (nunca bloqueia/rejeita/silencia a chamada).

- **`AtendeAutoAccessibilityService`** observa a tela de chamada do Samsung
  (`com.samsung.android.incallui`) e, se houver um número desconhecido sinalizado,
  executa os dois toques necessários para atender em modo texto:
  1. Botão flutuante "Chamada por texto" (`ai_call_floating_button_container`)
  2. Confirmação de atendimento (identificada pela `contentDescription`, pois não tem id)

Esses IDs/descrições foram descobertos empiricamente neste aparelho com o
`DumpAccessibilityService` (ver abaixo) e podem mudar em atualizações do One UI.

## Build

```bash
source env.sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Toolchain fora do repositório, em `~/Android`: SDK em `~/Android/Sdk`,
JDK 17 (Temurin) em `~/Android/jdk`.

## Configuração no aparelho (uma vez)

Abra o app **AtendeAuto** e, na tela principal:

1. **Abrir configurações de Acessibilidade** → ativar o serviço.
   Se estiver esmaecido (Android 13+ bloqueia acessibilidade de APKs sideloaded):
   `Informações do app → ⋮ → Permitir configurações restritas`.
2. **Tornar-se app de Triagem de Chamadas** → conceder (substitui qualquer outro app de
   triagem/antispam que você use, já que o papel é exclusivo).
3. **Permitir acesso aos Contatos** → conceder.
4. Adicionar números extras à allowlist, se quiser tratar como "conhecidos" sem estarem
   salvos na agenda.

## Depuração

```bash
adb logcat -s AtendeAuto
```

Se uma atualização do One UI quebrar a automação (IDs/descrições mudam), reative
temporariamente o `DumpAccessibilityService` no `AndroidManifest.xml` no lugar do
`AtendeAutoAccessibilityService`, reinstale, e repita uma chamada de teste para
redescobrir os identificadores atuais.

## Limitações conhecidas

- Papel de Triagem de Chamadas é exclusivo de um app por vez no aparelho.
- Depende de IDs internos do One UI — pode quebrar em atualizações do sistema.
- Requer permissão de Acessibilidade concedida manualmente (Android bloqueia concessão
  automática para apps sideloaded).
