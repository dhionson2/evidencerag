"""Deterministic lexical diagnostic for the fictional corpus; not an LLM/RAG quality score."""
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORPUS = ROOT / "docs" / "demo-corpus"
CASES = ROOT / "eval" / "public-demo-cases.json"


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


documents = []
for path in sorted(CORPUS.glob("*.md")):
    paragraphs = [p.strip() for p in path.read_text(encoding="utf-8").split("\n\n") if p.strip()]
    documents.extend((path.name, p, normalize(p)) for p in paragraphs)

cases = json.loads(CASES.read_text(encoding="utf-8"))
answerable = [case for case in cases if case["answerable"]]
unanswerable = [case for case in cases if not case["answerable"]]
hits, reciprocal_rank = 0, 0.0
for case in answerable:
    expected = normalize(case["expected_quote"])
    ranked = sorted(documents, key=lambda d: len(set(normalize(case["question"]).split()) & set(d[2].split())), reverse=True)
    rank = next((i + 1 for i, row in enumerate(ranked[:5]) if expected in row[2]), None)
    if rank:
        hits += 1
        reciprocal_rank += 1 / rank

for case in unanswerable:
    if case["expected_quote"]:
        raise SystemExit(f"Caso sem evidência inconsistente: {case['id']}")

print("Avaliação lexical determinística (não mede qualidade semântica nem resposta de LLM)")
print(f"Corpus: {len(list(CORPUS.glob('*.md')))} arquivo(s), {len(documents)} parágrafos")
print(f"Casos: {len(cases)} total; {len(answerable)} respondíveis; {len(unanswerable)} sem evidência; {sum(bool(c.get('promptInjection')) for c in cases)} com prompt injection")
print(f"Recall de trecho esperado @5: {hits}/{len(answerable)} ({hits / len(answerable):.1%})")
print(f"MRR @5: {reciprocal_rank / len(answerable):.3f}")
