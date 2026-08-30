# AtendeAuto

Atendimento automático de chamadas de números desconhecidos no Samsung Galaxy A36,
acionando o recurso nativo **Chamada por Texto** (Bixby Text Call) do One UI.

Estado atual: **Fase 1 — APK de diagnóstico**. O app ainda não age sobre chamadas;
ele apenas registra a árvore de acessibilidade da tela de chamada para descobrirmos
se o botão "Chamada por texto" é alcançável neste aparelho.

## Antes de tudo (Fase 0)

Confira no telefone, em `Telefone → ⋮ → Configurações`:

1. Existe **"Triagem de chamadas"** com "Triar chamadas automaticamente"?
   → Se sim, ative no nível Alto. **Este app é desnecessário.**
2. Existe **"Chamada por texto"**? O pacote de idioma pt-BR baixa?
   → Se não existir, o plano principal cai (ver plano B no plano de implementação).

## Build

```bash
source env.sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O toolchain fica fora do repositório, em `~/Android`:
SDK em `~/Android/Sdk`, JDK 17 (Temurin) em `~/Android/jdk`.

## Uso da build de diagnóstico

1. Instale e abra o app; toque em "Abrir configurações de Acessibilidade" e ative
   **AtendeAuto (diag)**.
   Se a opção estiver esmaecida (Android 13+ bloqueia serviços de acessibilidade de
   APKs sideloaded): `Informações do app → ⋮ → Permitir configurações restritas`.
2. No PC: `adb logcat -s AtendeAuto`
3. Ligue para o telefone de outro número e deixe tocar alguns segundos.
4. Procure no log um nó cujo `text`/`desc` seja "Chamada por texto", e anote o
   `viewIdResourceName` e se `clickable=true`.

Se o log mostrar `rootInActiveWindow NULO`, a tela de chamada é protegida com
`FLAG_SECURE` e a automação por acessibilidade não é viável.

Este APK não atende, rejeita nem modifica chamada alguma.
