# Time to first audio with the app's config, before and after the change.
import sys, time, sherpa_onnx as so

def next_chunk(text, frm, max_length=200):
    start = max(frm, 0)
    while start < len(text) and text[start].isspace(): start += 1
    if start >= len(text): return None
    end = text.find('\n', start)
    if end == -1: end = len(text)
    if end - start > max_length:
        limit = start + max_length
        sentence_end = max(text.rfind(s, 0, limit + 1) for s in (". ", "! ", "? "))
        word_end = text.rfind(' ', 0, limit + 1)
        end = sentence_end + 1 if sentence_end > start else word_end if word_end > start else limit
    return (start, end)

def next_sentence(text, frm, max_length=200):
    chunk = next_chunk(text, frm, max_length)
    if chunk is None: return None
    first, last = chunk[0], chunk[1] - 1
    end = last + 1
    i = first + 10 - 1
    while i < last:
        if text[i] in ".!?…":
            after = i + 1
            while after <= last and text[after] in "\"'”’»)]": after += 1
            if after <= last and text[after].isspace():
                end = after
                break
        i += 1
    if end - first > 80:
        limit = first + 80
        clause = max(text.rfind(c, 0, limit + 1) for c in (", ", "; ", ": "))
        if clause >= first + 10: end = clause + 1
    return (first, end)

d = sys.argv[1]
def make():
    k = so.OfflineTtsKokoroModelConfig(model=f"{d}/model.int8.onnx", voices=f"{d}/voices.bin", tokens=f"{d}/tokens.txt",
        data_dir=f"{d}/espeak-ng-data", dict_dir=f"{d}/dict", lexicon=f"{d}/lexicon-us-en.txt,{d}/lexicon-zh.txt", lang="en-us")
    return so.OfflineTts(so.OfflineTtsConfig(model=so.OfflineTtsModelConfig(kokoro=k, num_threads=4), max_num_sentences=1))
g = so.GenerationConfig(); g.sid = 3; g.speed = 1.0; g.extra = {"lang": "en-us"}

books = {
  "Holmes": "To Sherlock Holmes she is always the woman. I have seldom heard him mention her under any other name. In his eyes she eclipses and predominates the whole of her sex. It was not that he felt any emotion akin to love for Irene Adler.",
  "Alice": "Alice was beginning to get very tired of sitting by her sister on the bank, and of having nothing to do: once or twice she had peeped into the book her sister was reading, but it had no pictures or conversations in it. \"And what is the use of a book,\" thought Alice, \"without pictures or conversations?\"",
  "Austen": "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife. However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families.",
}
for name, text in books.items():
    s = time.time(); tts = make(); create = time.time() - s
    a, b = next_chunk(text, 0)
    s = time.time(); audio = tts.generate(text[a:b], g); before = create + time.time() - s
    a, b = next_sentence(text, 0)
    s = time.time(); audio1 = tts.generate(text[a:b], g); after = time.time() - s
    c, e = next_sentence(text, b)
    s = time.time(); audio2 = tts.generate(text[c:e], g); second = time.time() - s
    print(f"{name}: before {before:.2f} s (load {create:.2f} s + {next_chunk(text,0)[1]} chars), "
          f"after {after:.2f} s ({b-a} chars, {len(audio1.samples)/24000:.2f} s audio); "
          f"next sentence ready in {second:.2f} s while the first plays {len(audio1.samples)/24000:.2f} s", flush=True)
