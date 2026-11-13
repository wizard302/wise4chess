#!/usr/bin/env python3
"""Synthesises the app's sound effects, so no third-party recordings are needed.

Usage (from the repository root):

    python3 tools/sounds/generate.py app/src/main/res/raw

Output is deterministic (fixed noise seed): 16-bit mono WAV at 44.1 kHz.
"""
import math
import random
import struct
import sys
import wave
from pathlib import Path

RATE = 44100


def knock(partials, noise_decay_ms, noise_level, length_ms, seed):
    """A short woody knock: a few fast-decaying partials plus a burst of low-passed noise."""
    rng = random.Random(seed)
    samples = []
    low_passed = 0.0
    for i in range(int(RATE * length_ms / 1000)):
        t = i / RATE
        value = sum(
            amplitude * math.sin(2 * math.pi * frequency * t) * math.exp(-t * 1000 / decay_ms)
            for frequency, amplitude, decay_ms in partials
        )
        low_passed += 0.35 * ((rng.random() * 2 - 1) - low_passed)
        value += noise_level * low_passed * math.exp(-t * 1000 / noise_decay_ms)
        samples.append(value)
    return samples


def tone(frequency, length_ms, decay_ms):
    """A soft bell-like note with a short attack."""
    samples = []
    for i in range(int(RATE * length_ms / 1000)):
        t = i / RATE
        attack = min(1.0, t * 1000 / 6)
        body = math.sin(2 * math.pi * frequency * t) + 0.25 * math.sin(2 * math.pi * frequency * 2 * t)
        samples.append(attack * body * math.exp(-t * 1000 / decay_ms))
    return samples


def mix(*tracks):
    """Each track is (start in ms, gain, samples)."""
    length = max(int(RATE * start / 1000) + len(samples) for start, _, samples in tracks)
    out = [0.0] * length
    for start, gain, samples in tracks:
        offset = int(RATE * start / 1000)
        for i, value in enumerate(samples):
            out[offset + i] += gain * value
    return out


def write(path, samples, peak):
    scale = peak / max(abs(s) for s in samples)
    fade = int(RATE * 0.004)  # no click at the cut-off
    with wave.open(str(path), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        frames = bytearray()
        for i, value in enumerate(samples):
            tail = min(1.0, (len(samples) - i) / fade)
            frames += struct.pack("<h", int(max(-1.0, min(1.0, value * scale * tail)) * 32767))
        f.writeframes(bytes(frames))


def main():
    target = Path(sys.argv[1])
    target.mkdir(parents=True, exist_ok=True)
    move = knock([(540, 1.0, 16), (1210, 0.45, 9), (2480, 0.2, 4)], 5, 0.9, 110, seed=1)
    heavy = knock([(390, 1.0, 20), (910, 0.5, 11), (1900, 0.25, 5)], 8, 1.3, 140, seed=2)
    click = knock([(820, 1.0, 9), (1900, 0.5, 5)], 3, 1.0, 70, seed=3)
    write(target / "sound_move.wav", move, peak=0.75)
    write(target / "sound_capture.wav", mix((0, 1.0, heavy), (34, 0.7, click)), peak=0.9)
    write(target / "sound_game_end.wav", mix((0, 1.0, tone(659.26, 420, 130)), (170, 0.9, tone(523.25, 620, 190))), peak=0.6)
    for path in sorted(target.glob("sound_*.wav")):
        print(path.name, path.stat().st_size, "bytes")


if __name__ == "__main__":
    main()
