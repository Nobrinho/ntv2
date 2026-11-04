package com.ntv2.app.feature.media.presentation.trailer

/**
 * Página local com o player oficial do YouTube (IFrame API), mudo e sem controles, ocupando a tela
 * toda em fundo preto. Avisa o app pela ponte `NtvTrailer` quando começa a tocar e quando dá erro.
 */
object TrailerHtml {
    /** Domínio do site do projeto: vai no `origin` do player (sem ele o YouTube devolve erro 153). */
    const val BASE_URL = "https://nobrinho.github.io/"
    private const val ORIGIN = "https://nobrinho.github.io"

    private val ID = Regex("^[A-Za-z0-9_-]{11}$")

    fun build(videoId: String): String {
        // O ID entra no HTML/JS: só passa o formato validado (nada de aspas ou tags).
        require(ID.matches(videoId)) { "ID do YouTube inválido" }
        return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
#player { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: 0; pointer-events: none; }
</style>
</head>
<body>
<div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player = null;
function onYouTubeIframeAPIReady() {
  player = new YT.Player('player', {
    width: '100%',
    height: '100%',
    videoId: '$videoId',
    playerVars: {
      autoplay: 1,
      mute: 1,
      controls: 0,
      loop: 1,
      playlist: '$videoId',
      playsinline: 1,
      rel: 0,
      iv_load_policy: 3,
      disablekb: 1,
      enablejsapi: 1,
      origin: '$ORIGIN'
    },
    events: {
      onReady: function (e) { e.target.mute(); e.target.playVideo(); },
      onStateChange: function (e) {
        if (e.data === 1) { NtvTrailer.playing(); }
        else if (e.data === 0) { e.target.seekTo(0); e.target.playVideo(); }
      },
      onError: function (e) { NtvTrailer.error(String(e.data)); }
    }
  });
}
</script>
</body>
</html>
""".trimIndent()
    }
}
