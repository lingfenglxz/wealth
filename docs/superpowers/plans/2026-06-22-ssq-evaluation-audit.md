# 双色球评估与审计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add on-demand cached rolling evaluation, return-aware backtests, and append-only SSQ prediction audit records.

**Architecture:** `server/main.py` owns state and evaluation persistence. A cached evaluation is keyed by latest issue and the default 6+3 parameters. Prediction records are assigned a run identifier and settled independently.

**Tech Stack:** Python, FastAPI, unittest, JSON files.

---

### Task 1: Return-aware backtest metrics

**Files:**
- Modify: `server/tests/test_ssq_hit_rate_model.py`
- Modify: `server/main.py`

- [ ] Write a failing test that requires `backtest_model()` to expose invested amount, simulated prize amount, ROI, and tier counts for a 6+3 plan.
- [ ] Run `server/.venv/Scripts/python.exe -m unittest tests.test_ssq_hit_rate_model` and confirm the missing-key failure.
- [ ] Accumulate settlement values in `backtest_model()` and return the four metrics without changing existing hit-rate fields.
- [ ] Re-run the focused test and confirm it passes.

### Task 2: Cached rolling evaluation

**Files:**
- Modify: `server/tests/test_ssq_hit_rate_model.py`
- Modify: `server/tests/test_ssq_state_backup.py`
- Modify: `server/main.py`

- [ ] Write failing tests for a six-fold report that persists for a latest issue and is reused until the issue changes.
- [ ] Run the focused tests and confirm the evaluation-cache symbols are missing.
- [ ] Add evaluation load/save, fold aggregation, stable model-selection guardrails, and endpoint integration for data refresh and recommendation generation.
- [ ] Re-run focused tests and confirm cache reuse and invalidation pass.

### Task 3: Append-only prediction audit records

**Files:**
- Modify: `server/tests/test_ssq_state_backup.py`
- Modify: `server/main.py`
- Modify: `server/README.md`

- [ ] Write a failing test that saves two recommendations for one target issue and expects distinct runs and distinct settlements.
- [ ] Run the focused test and confirm the prior target-issue replacement behavior fails it.
- [ ] Add run metadata, append-only persistence, and per-run settlement keys while preserving legacy state readability.
- [ ] Update the server README state-backup description.
- [ ] Re-run focused tests and confirm both runs remain auditable.

### Task 4: Verification

**Files:**
- Verify: `server/tests/*.py`
- Verify: `app/src/test/java/**`

- [ ] Run `server/.venv/Scripts/python.exe -m unittest discover -s tests` from `server`.
- [ ] Run `./gradlew.bat testDebugUnitTest` from the repository root.
- [ ] Inspect `git diff --check` and `git status --short`; report only the files created or changed for this feature.
