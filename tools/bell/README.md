`make_bell.py` synthesises Clean's own sounds with the standard library only: the five a rest can end with (a deep bell,
a gong, a soft two-note tone, a wood block and the bright boxing-ring bell: inharmonic partials with detuned twins, a noise
burst for the strike, exponential decays) and a soft two-note chime for a PR.
Run `python tools/bell/make_bell.py` to write `bell.wav`, `bell_deep.wav`, `gong.wav`, `tone_soft.wav`, `wood_block.wav` and
`chime.wav` into `app/src/main/res/raw`. No audio is downloaded or copied.
