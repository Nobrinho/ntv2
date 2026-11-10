package com.ntv2.app.feature.media.presentation.trailer

/**
 * Página local com o player oficial do YouTube (IFrame API) em fundo preto. Uma página só serve a
 * prévia dos detalhes e a tela cheia: o botão "Trailer" reaproveita o mesmo WebView (sem nova
 * requisição) e só sobe o volume com `ntvSound(100)`.
 *
 * - Sem controles, sem repetir; legenda em português quando o vídeo tiver (`cc_lang_pref`/`hl`, que
 *   também faz o YouTube preferir a faixa de áudio dublada pt-BR em vídeos com várias faixas).
 * - O iframe cobre a área toda em 16:9 (corta em cima/embaixo em vez de deixar barras pretas).
 * - No fim avisa `NtvTrailer.ended()` um pouco antes do vídeo acabar: quem chama esconde o player
 *   antes de a grade de "próximos vídeos" do YouTube aparecer.
 * - `ntvToggle()`/`ntvSeek(delta)`/`ntvRestart()` para o D-pad (o WebView nunca recebe foco).
 */
object TrailerHtml {
    /** Domínio do site do projeto: vai no `origin` do player (sem ele o YouTube devolve erro 153). */
    const val BASE_URL = "https://nobrinho.github.io/"
    private const val ORIGIN = "https://nobrinho.github.io"

    /** Volume da prévia automática (0-100). */
    const val PREVIEW_VOLUME = 50

    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    /**
     * [volume] inicial (0-100). [captions]: legenda em português (tela cheia). A prévia de fundo fica
     * sem legenda (ela aparecia grande no meio dos detalhes); ao virar tela cheia no lugar,
     * `ntvSound` liga a legenda.
     */
    fun build(videoId: String, volume: Int = PREVIEW_VOLUME, captions: Boolean = false): String {
        // O ID entra no HTML/JS: só passa o formato validado (nada de aspas ou tags).
        require(ID.matches(videoId)) { "ID do YouTube inválido" }
        val vol = volume.coerceIn(0, 100)
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
#player, iframe { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; border: 0; pointer-events: none; }
</style>
</head>
<body>
<div id="player"></div>
<script>
var player = null;
var endedSent = false;
var soundOn = false;
var wantCaptions = ${if (captions) "true" else "false"};
function ntvCaptions(on) {
  if (!player || !player.loadModule) return;
  if (on) { player.loadModule('captions'); player.setOption && player.setOption('captions', 'track', {languageCode: 'pt'}); }
  else { player.unloadModule('captions'); }
}
// Cobre a janela em 16:9: sobra cortada em cima/embaixo (ou nas laterais), nunca barras pretas.
function ntvFit() {
  var f = document.getElementById('player');
  if (!f) return;
  var w = window.innerWidth, h = window.innerHeight, r = 16 / 9, fw, fh;
  if (w / h > r) { fw = w; fh = w / r; } else { fh = h; fw = h * r; }
  f.style.width = fw + 'px'; f.style.height = fh + 'px';
  f.style.left = ((w - fw) / 2) + 'px'; f.style.top = ((h - fh) / 2) + 'px';
}
window.addEventListener('resize', ntvFit);
function ntvEnd() {
  if (endedSent) return;
  endedSent = true;
  if (player && player.pauseVideo) player.pauseVideo();
  NtvTrailer.ended();
}
// Termina um pouco antes do fim real: a grade de recomendados do YouTube não chega a aparecer.
setInterval(function () {
  if (!player || !player.getDuration || endedSent) return;
  var d = player.getDuration(), t = player.getCurrentTime();
  if (d > 0 && t > 0 && d - t < 0.6) ntvEnd();
}, 250);
// Definido ANTES do script da API: com o widgetapi no cache do processo ele roda na hora e chama
// este callback; se ainda não existisse, o player nunca seria criado.
function onYouTubeIframeAPIReady() {
  if (player) return;
  player = new YT.Player('player', {
    width: '100%',
    height: '100%',
    videoId: '$videoId',
    playerVars: {
      autoplay: 1,
      controls: 0,
      loop: 0,
      fs: 0,
      playsinline: 1,
      rel: 0,
      iv_load_policy: 3,
      disablekb: 1,
      cc_load_policy: ${if (captions) 1 else 0},
      cc_lang_pref: 'pt',
      hl: 'pt-BR',
      enablejsapi: 1,
      origin: '$ORIGIN'
    },
    events: {
      onReady: function (e) {
        NtvTrailer.ready(); ntvFit();
        // Autoplay com som é bloqueado: começa mudo e liga o som quando já está tocando.
        e.target.mute(); e.target.playVideo();
      },
      onStateChange: function (e) {
        NtvTrailer.state(String(e.data));
        if (e.data === 1) {
          if (!soundOn) { soundOn = true; e.target.unMute(); e.target.setVolume($vol); ntvCaptions(wantCaptions); }
          NtvTrailer.playing();
        }
        else if (e.data === 0) { ntvEnd(); }
      },
      onError: function (e) { NtvTrailer.error(String(e.data)); }
    }
  });
  ntvFit();
}
function ntvSound(v) {
  if (!player || !player.setVolume) return;
  player.unMute(); player.setVolume(v);
  wantCaptions = true; ntvCaptions(true);
}
function ntvToggle() {
  if (!player || !player.getPlayerState) return;
  if (player.getPlayerState() === 1) { player.pauseVideo(); } else { player.playVideo(); }
}
function ntvSeek(delta) {
  if (!player || !player.getCurrentTime) return;
  player.seekTo(Math.max(0, player.getCurrentTime() + delta), true);
}
function ntvRestart() {
  if (!player || !player.seekTo) return;
  endedSent = false;
  player.seekTo(0, true); player.playVideo();
}
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body>
</html>
""".trimIndent()
    }
}
