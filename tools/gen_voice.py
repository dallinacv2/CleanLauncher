"""Genera los audios del saludo (res/raw/*.mp3) con la voz Salome (es-CO).

Cada entrada de tools/frases.json es  nombre_del_clip -> texto que se dice.
Se ejecuta desde GitHub Actions (workflow "Generar voces"); necesita internet y `pip install edge-tts`.
El formato de salida (24 kHz, 48 kbps, mono) es el mismo de los audios originales.
"""
import asyncio
import json
import pathlib

import edge_tts

VOICE = "es-CO-SalomeNeural"
ROOT = pathlib.Path(__file__).resolve().parent.parent


async def main():
    frases = json.loads((ROOT / "tools" / "frases.json").read_text(encoding="utf-8"))
    for clip, text in frases.items():
        out = ROOT / "res" / "raw" / f"{clip}.mp3"
        await edge_tts.Communicate(text, VOICE).save(str(out))
        size = out.stat().st_size
        if size < 2000:
            raise SystemExit(f"{clip}: audio vacio ({size} bytes)")
        print(f"{clip}: {size} bytes - {text}")


asyncio.run(main())
