"""Gera NbrIcons.kt (ImageVector) a partir dos drawables ic_nbr_*.xml.

Assim o código usa `NbrIcons.Heart` no lugar de `Icons.Filled.Favorite`, com o mesmo tipo (ImageVector),
sem mudar assinaturas. Os drawables continuam sendo a fonte do desenho.

Uso: python -I tools/icones/vd2kt.py app/src/main/res/drawable app/src/main/java/com/ntv2/app/core/ui/icons/NbrIcons.kt
"""
import re
import sys
from pathlib import Path


def prop_name(stem: str) -> str:
    return "".join(p[:1].upper() + p[1:] for p in stem.removeprefix("ic_nbr_").split("_"))


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    entries = []
    for f in sorted(src.glob("ic_nbr_*.xml")):
        t = f.read_text(encoding="utf-8")
        paths = re.findall(r'android:pathData="([^"]+)"', t)
        filled = "android:fillColor" in t
        args = ", ".join(f'"{p}"' for p in paths)
        entries.append(
            f'    val {prop_name(f.stem)}: ImageVector by lazy {{ icon("{f.stem}", {str(filled).lower()}, {args}) }}'
        )
    body = "\n".join(entries)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(f'''package com.ntv2.app.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Ícones próprios do NBR Play (os mesmos do protótipo).
 *
 * Arquivo GERADO por tools/icones/vd2kt.py a partir de res/drawable/ic_nbr_*.xml. Não edite à mão:
 * acrescente o ícone em tools/icones/nomes.txt, gere os drawables e rode o script de novo (docs/ICONES.md).
 */
object NbrIcons {{
{body}
}}

private fun icon(name: String, filled: Boolean, vararg paths: String): ImageVector {{
    val b = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    for (d in paths) {{
        b.addPath(
            pathData = addPathNodes(d),
            fill = if (filled) SolidColor(Color.White) else null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }}
    return b.build()
}}
''', encoding="utf-8")
    print(len(entries), "ícones em", out)


if __name__ == "__main__":
    main()
