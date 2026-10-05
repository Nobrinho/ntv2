# NBR Play Brand Asset Kit

Arte oficial preservada. Comece pelo PDF em guidelines/NBR-Play-Manual.pdf.

## Arquivos de uso direto
- Android: icons/android; copiar adaptive/res ao projeto para ícone adaptativo. No AndroidManifest, usar android:icon="@mipmap/ic_launcher". Recursos legados por densidade incluídos.
- iOS: icons/ios, PNG opacos sem cantos. Bases raster; não é um pacote Icon Composer nem catálogo completo de todas as plataformas Apple.
- PWA: web/pwa. Ajustar start_url, scope e caminhos conforme implantação. Manifest incluído é um ponto de partida, não aplicação publicada.
- TV: icons/android-tv/banner-*; composição compartilhada nas pastas google-tv, fire-tv, smart-tv e tv-box. Estas são bases gráficas, não certificação de loja.
- Home, biblioteca, settings: backgrounds/*/minimal, gradient ou pattern sem marca.
- Login, onboarding: glow ou cinematic. Usar logo separada para layouts responsivos.
- Loading: minimal + logo estática; este kit não contém animação.
- Empty state e seleção de perfil: minimal; acrescente texto e ações no app, não no fundo.
- Splash: pasta splash. Web/desktop e TV em 16:9; mobile em 9:16; tablet em 4:3.
- Social: templates SVG com texto editável e PNG/WebP para preview/publicação. Fontes não incorporadas; converter em curvas no editor antes de enviar a terceiros, se necessário.
- YouTube: composição central para suportar os recortes usuais. Conferir sempre no preview da plataforma.

## Preservação e limitações
A logo original PNG é o único master oficial. As versões horizontal/vertical/compacta são canvases de aplicação, sem reorganizar as letras. A arte já é acromática. Não foram geradas silhuetas branca/preta, wordmark redesenhado ou símbolo novo porque isso conflita com a instrução de não alterar elementos. Não há vetor original: SVGs com logo incorporam o PNG; apenas fundos e formas são vetores puros. Favicons 16/32 e camadas adaptativas têm legibilidade limitada. A sombra da marca é preservada mesmo onde diretrizes de plataforma preferem simplificação. É necessário aprovar uma marca reduzida para resolver esses limites sem ambiguidade.

A fonte de interface proposta é Inter, com fallback Arial. Arquivos de fonte não incluídos. O manual usa DejaVu Sans. Templates sociais usam DejaVu Sans como fonte disponível; substituir por Inter no editor caso instalado.

## Templates e responsividade
Nos SVGs, manter `preserveAspectRatio` e proporção da imagem. Usar fundos sem logo com `background-size: cover`; posicionar a logo como camada separada. Fundos ultrawide possuem composição própria. Área segura sugerida para TV: 5% das bordas; para logo: 12% de sua largura visível. Essas margens são decisões do sistema visual, não normas universais.

## Tokens e integração
`design-system/tokens.json`: paletas dark/light, tipografia, espaçamento, raio, foco e movimento.
`tokens.css`: cores e foco. Aplicar `data-theme="dark"` ou `data-theme="light"` no elemento raiz.
`mui-theme.ts`: ponto de partida para a aplicação MUI, sem alterar a configuração existente. Instalar Inter pelo projeto se desejado. As cores semânticas devem sempre acompanhar rótulos ou ícones.

## Formatos
PNG para fidelidade/transparência; WebP para fundos com menor custo; SVG para composição/editabilidade. Não foram criados arquivos vetoriais falsos por tracing automático. Social covers e ícones específicos reutilizam a mesma marca intencionalmente.

## Referências
- https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
- https://developer.android.com/design/ui/tv/guides/system/tv-app-icon-guidelines
- https://developer.apple.com/design/human-interface-guidelines/app-icons
