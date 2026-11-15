"""Synthesises Clean's sounds: the ones a rest can end with (a deep bell, a gong, a soft tone, a wood block and the
bright boxing-ring bell) and a soft rising chime for a PR (chime.wav).
Standard library only. Run from anywhere: python tools/bell/make_bell.py"""
import math, random, struct, wave, pathlib

RATE = 22050
OUT = pathlib.Path(__file__).resolve().parents[2] / "app/src/main/res/raw"


def write(name, samples, peak_db=-1.0):
    top = max(abs(s) for s in samples) or 1.0
    gain = 10 ** (peak_db / 20) / top
    with wave.open(str(OUT / name), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * gain)) * 32767)) for s in samples))


def struck(base, partials, length, strike=1.5, attack=0.0):
    """A struck thing: [partials] are (ratio, loudness, decay seconds), each with a slightly detuned twin for shimmer;
    [strike] is how loud the few milliseconds of noise at the start are, [attack] how long the sound takes to swell."""
    random.seed(7)
    out = []
    for n in range(int(RATE * length)):
        t = n / RATE
        s = 0.0
        for ratio, amp, decay in partials:
            e = math.exp(-t / decay)
            f = base * ratio
            s += amp * e * (math.sin(2 * math.pi * f * t) + 0.6 * math.sin(2 * math.pi * f * 1.004 * t))
        if attack > 0:
            s *= min(1.0, t / attack)
        s += strike * math.exp(-t / 0.004) * random.uniform(-1, 1)
        out.append(s)
    # A short fade at the very end so it never clicks.
    for n in range(200):
        out[-1 - n] *= n / 200
    return out


def bright_bell():
    return struck(520.0, [(1.0, 1.0, 0.55), (2.76, 0.7, 0.38), (5.4, 0.45, 0.22), (8.9, 0.25, 0.12)], 1.3)


def deep_bell():
    # An octave under the bright one, with less of the high partials and a softer strike.
    return struck(262.0, [(1.0, 1.0, 0.8), (2.0, 0.55, 0.6), (2.76, 0.45, 0.42), (4.1, 0.16, 0.2)], 1.6, strike=0.5)


def gong():
    # Low and wide: close inharmonic partials that swell in and ring on.
    return struck(165.0, [(1.0, 1.0, 1.1), (1.48, 0.7, 0.95), (2.07, 0.55, 0.8), (2.63, 0.4, 0.6), (3.4, 0.22, 0.4), (4.9, 0.1, 0.25)], 2.0, strike=0.25, attack=0.012)


def notes(pitches, length, decay, octave=0.25, attack=0.012):
    """Plain notes, each (frequency, start seconds): a sine with a little of its octave, a soft start and a decay."""
    out = [0.0] * int(RATE * length)
    for freq, start in pitches:
        for n in range(int(RATE * decay * 6)):
            t = n / RATE
            i = int((start + t) * RATE)
            if i >= len(out):
                break
            env = min(1.0, t / attack) * math.exp(-t / decay)
            out[i] += env * (math.sin(2 * math.pi * freq * t) + octave * math.sin(4 * math.pi * freq * t))
    for n in range(200):
        out[-1 - n] *= n / 200
    return out


def soft_tone():
    # Two gentle low notes, the second a fourth up.
    return notes([(330.0, 0.0), (440.0, 0.22)], 0.9, 0.16)


def wood_block():
    # Two dry knocks.
    return notes([(392.0, 0.0), (392.0, 0.17)], 0.45, 0.035, octave=0.5, attack=0.002)


def chime():
    notes = [(660.0, 0.0), (990.0, 0.14)]
    out = [0.0] * int(RATE * 0.35)
    for freq, start in notes:
        for n in range(int(RATE * 0.21)):
            t = n / RATE
            env = min(1.0, t / 0.008) * math.exp(-t / 0.07)
            i = int((start + t) * RATE)
            if i < len(out):
                out[i] += 0.6 * env * math.sin(2 * math.pi * freq * t)
    return out


OUT.mkdir(parents=True, exist_ok=True)
write("bell.wav", bright_bell())
write("bell_deep.wav", deep_bell())
write("gong.wav", gong())
write("tone_soft.wav", soft_tone())
write("wood_block.wav", wood_block())
write("chime.wav", chime(), -6.0)
