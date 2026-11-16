import sys, time, sherpa_onnx as so
d = sys.argv[1]
k = so.OfflineTtsKokoroModelConfig(model=f"{d}/model.int8.onnx", voices=f"{d}/voices.bin", tokens=f"{d}/tokens.txt",
  data_dir=f"{d}/espeak-ng-data", dict_dir=f"{d}/dict", lexicon=f"{d}/lexicon-us-en.txt,{d}/lexicon-zh.txt", lang="en-us")
cfg = so.OfflineTtsConfig(model=so.OfflineTtsModelConfig(kokoro=k, num_threads=int(sys.argv[2]), debug=False), rule_fsts="", max_num_sentences=1)
print("valid", cfg.validate())
s0=time.time(); t = so.OfflineTts(cfg); print("create", time.time()-s0)
print("sr", t.sample_rate, "speakers", t.num_speakers)
g = so.GenerationConfig(); g.sid=3; g.speed=1.0; g.extra={"lang":"en-us"}
for text in ["Hello there.", "It was the best of times, it was the worst of times, it was the age of wisdom, it was the age of foolishness, it was the epoch of belief, it was the epoch of incredulity, it was the season."]:
    for n in [1]:
        s=time.time(); a = t.generate(text, g)
        print("chars", len(text), "audio_s", round(len(a.samples)/a.sample_rate,2), "gen_s", round(time.time()-s,2), flush=True)
