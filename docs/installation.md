# Instalar, escolher Home e restaurar

Instale somente em Android TV/Google TV11+; confirme o sistema e método de depuração oficial do seu aparelho. ADB precisa estar autorizado ou pareado. Exemplos usam `SERIAL` e `USUARIO` como marcadores: substitua pelos valores reais, nunca deixe o alvo implícito quando houver mais de um dispositivo.

```sh
adb devices
adb -s SERIAL shell am get-current-user
adb -s SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s SERIAL install -r lume-orbita-android-0.3.4.apk
adb -s SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Guarde o componente Home anterior antes de instalar. Alguns firmwares podem limpar sua preferência ao adicionar outro launcher. Atualizações precisam manter pacote e assinatura; não desinstale para forçar atualização incompatível.

Compare o Home antes e depois da instalação. Se a preferência desapareceu ou mudou inesperadamente, pare e restaure a escolha anterior pelo método oficial do aparelho; não aceite uma troca automática como autorização. Somente após confirmar uma Home funcional, abra o Lume para avaliação:

```sh
adb -s SERIAL shell am start -n br.com.lume.launcher/.LauncherActivity
```

## Tela inicial padrão

Teste o aplicativo como app primeiro. Se estiver confortável, use Ajustes do Lume > seleção de tela inicial, ou a configuração oficial de apps padrão do Android. Confirme a escolha na TV. Disponibilidade depende do OEM; a instalação por si só não deve ser tratada como garantia de Home.

Em um aparelho que ofereça o comando abaixo, após autorização do proprietário, o método avançado é:

```sh
adb -s SERIAL shell cmd package set-home-activity --user USUARIO br.com.lume.launcher/.LauncherActivity
```

Leia novamente o resolvedor e teste Home no controle, Voltar, configurações, HDMI, apps e standby separadamente. Mantenha o launcher anterior instalado durante a avaliação. Não são necessários root, acessibilidade, desativação de apps do sistema ou alteração de firmware.

## Recuperação

Para voltar, escolha o launcher anterior nas configurações oficiais. Se a tela do Lume falhar e ADB ainda estiver autorizado, abra o componente anterior registrado e escolha-o como Home pelo método disponível no seu aparelho. Confirme uma Home funcional antes de remover Lume.

Não desinstale componentes do sistema. Este repositório não distribui backups de launchers de terceiros nem scripts ligados a uma TV particular.

## Conteúdo compartilhado e privacidade

Continuar assistindo/Sugestões dependem dos registros Android publicados por outros apps e da permissão opcional READ_TV_LISTINGS. Autorize pela TV se desejar. Não há login de plataforma, servidor próprio ou telemetria. A biblioteca usa somente atividades habilitadas/exportadas com LAUNCHER/LEANBACK_LAUNCHER; serviços sem tela e apps desativados não são importados como atalhos.
