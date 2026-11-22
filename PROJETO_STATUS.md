# PROJETO_STATUS.md — HeardWord (Neural Audio Recognition)

**Curador / Criador:** `@ferzferax` ([GitHub](https://github.com/ferzferax))  
**Pacote Android:** `com.ferzferax.heardword` (`HeardWord`)  
**Repositório Público:** [ferzferax/HeardWord](https://github.com/ferzferax/HeardWord)  
**Última atualização:** 2026-11-22

---

## Status da Separação e Evolução

- [x] Projeto separado do módulo de Sonar (`AudioMonitor`) em projeto independente focado em reconhecimento neural de áudio
- [x] Permissões RF/Localização/Bluetooth do Sonar 100% removidas
- [x] Ícone vetorial adaptativo original criado (`ic_launcher_background.xml` + `ic_launcher_foreground.xml`)
- [x] Suporte bilíngue completo implementado (**Inglês padrão** + botão instantâneo `🌐 EN / PT` para Português BR incluindo todas as 521 classes do YAMNet)
- [x] Aba **Studio** equipada com **Export/Import JSON** + **Submit Sound Pack to GitHub** + **Sync Vault from GitHub**
- [x] Documentação GitHub padrão em inglês (`README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `LICENSE`, banner SVG, templates, `community_sound_vault.json`)
- [x] **`v0.2.0-alpha`:** Calibração acústica completa aplicada (`VOICE_RECOGNITION`, remoção de DC offset, eliminação do pré-ganho `2.5x`, duplo Noise Gate por `dBFS` + `Silence`, blocklist contextual de alucinações, suavização EMA e recorte de pico no Estúdio)
- [x] **`v0.2.0-alpha`:** Build Release assinado (`versionCode = 2`) com empacotamento compatível com Android 16+ e release publicada no GitHub
