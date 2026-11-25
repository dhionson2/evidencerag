#!/usr/bin/env python3
"""Offline lexical baseline. This is not a semantic RAG or LLM quality score."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CASES = json.loads((ROOT / "evaluation/cases.json").read_text(encoding="utf-8"))
DOCS = {p.name: p.read_text(encoding="utf-8").lower() for p in (ROOT / "sample-documents").glob("*.md")}
STOP = {"qual", "quais", "como", "quando", "quem", "para", "com", "uma", "dos", "das", "que", "são", "está", "por", "em", "de", "do", "ou", "a", "o", "e", "as", "os", "é", "no", "na", "ao", "se"}

def tokens(text):
    return {w for w in re.findall(r"[a-zà-ÿ0-9]+", text.lower()) if len(w) > 2 and w not in STOP}

def main():
    retrieval_hits = answerable = 0
    abstain_expected = abstain_cases = 0
    evidence_terms = evidence_terms_found = 0
    rows = []
    for case in CASES:
        q = tokens(case["question"])
        scores = sorted(((len(q & tokens(text)), name) for name, text in DOCS.items()), reverse=True)
        retrieved = scores[0][1] if scores and scores[0][0] > 0 else None
        expected = case["expected_document"]
        if expected:
            answerable += 1
            retrieval_hits += int(retrieved == expected)
            source = DOCS.get(retrieved, "")
            for term in case["expected_terms"]:
                evidence_terms += 1
                evidence_terms_found += int(term.lower() in source)
        else:
            abstain_cases += 1
            abstain_expected += int(retrieved is None)
        rows.append({"id": case["id"], "tag": case["tag"], "expected_document": expected, "lexical_top1": retrieved,
                     "retrieval_hit": None if not expected else retrieved == expected,
                     "expected_evidence_terms_found": None if not expected else sum(term.lower() in DOCS.get(retrieved, "") for term in case["expected_terms"])})
    report = {
        "notice": "Lexical baseline only; does not invoke embeddings, vector search, generation, citation validation, or safety classifier.",
        "case_count": len(CASES),
        "answerable_retrieval_top1_coverage": round(retrieval_hits / answerable, 4) if answerable else None,
        "expected_evidence_term_coverage_given_lexical_top1": round(evidence_terms_found / evidence_terms, 4) if evidence_terms else None,
        "unanswerable_no_retrieval_rate": round(abstain_expected / abstain_cases, 4) if abstain_cases else None,
        "cases": rows,
    }
    out = ROOT / "evaluation/deterministic-report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in report.items() if k != "cases"}, ensure_ascii=False, indent=2))
    print(f"Relatório: {out.relative_to(ROOT)}")

if __name__ == "__main__":
    main()
