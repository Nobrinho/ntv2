package com.ntv2.app.feature.media.presentation.trailer

/**
 * Página local com o player oficial do YouTube (IFrame API) ocupando a tela toda em fundo preto.
 * Avisa o app pela ponte `NtvTrailer` quando começa a tocar e quando dá erro.
 *
 * - [build]: prévia do fundo dos detalhes — muda, sem controles, em loop.
 * - [buildFullscreen]: botão "Trailer" — com som e controles, sem repetir; avisa o fim e expõe
 *   `ntvToggle()`/`ntvSeek(delta)` para o D-pad (o WebView não recebe foco).
 */
object TrailerHtml {
    /** Domínio do site do projeto: vai no `origin` do player (sem ele o YouTube devolve erro 153). */
    const val BASE_URL = "https://nobrinho.github.io/"
    private const val ORIGIN = "https://nobrinho.github.io"

    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun build(videoId: String): String = page(videoId, fullscreen = false)

    fun buildFullscreen(videoId: String): String = page(videoId, fullscreen = true)

    private fun page(videoId: String, fullscreen: Boolean): String {
        // O ID entra no HTML/JS: só passa o formato validado (nada de aspas ou tags).
        require(ID.matches(videoId)) { "ID do YouTube inválido" }
        val vars = if (fullscreen) {
            """
      autoplay: 1,
      mute: 0,
      controls: 1,
      loop: 0,
            """.trimIndent()
        } else {
            """
      autoplay: 1,
      mute: 1,
      controls: 0,
      loop: 1,
      playlist: '$videoId',
            """.trimIndent()
        }
        val onReady = if (fullscreen) "e.target.unMute(); e.target.playVideo();" else "e.target.mute(); e.target.playVideo();"
        // Prévia: recomeça sozinha no fim. Tela cheia: avisa o app (que mostra "Ver de novo").
        val onEnded = if (fullscreen) "NtvTrailer.ended();" else "e.target.seekTo(0); e.target.playVideo();"
        val controls = if (!fullscreen) "" else """
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
  player.seekTo(0, true); player.playVideo();
}
"""
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
// Definido ANTES do script da API: com o widgetapi no cache do processo ele roda na hora e chama
// este callback; se ainda não existisse, o player nunca seria criado.
function onYouTubeIframeAPIReady() {
  if (player) return;
  player = new YT.Player('player', {
    width: '100%',
    height: '100%',
    videoId: '$videoId',
    playerVars: {
$vars
      playsinline: 1,
      rel: 0,
      iv_load_policy: 3,
      disablekb: 1,
      enablejsapi: 1,
      origin: '$ORIGIN'
    },
    events: {
      onReady: function (e) { NtvTrailer.ready(); $onReady },
      onStateChange: function (e) {
        NtvTrailer.state(String(e.data));
        if (e.data === 1) { NtvTrailer.playing(); }
        else if (e.data === 0) { $onEnded }
      },
      onError: function (e) { NtvTrailer.error(String(e.data)); }
    }
  });
}
$controls
</script>
<script src="https://www.youtube.com/iframe_api"></script>
</body>
</html>
""".trimIndent()
    }
}
