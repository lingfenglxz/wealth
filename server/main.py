from __future__ import annotations

import hashlib
import json
import math
import random
import re
import threading
from collections import Counter
from dataclasses import asdict, dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from statistics import mean, pstdev
from typing import Any
from uuid import uuid4

import httpx
from fastapi import BackgroundTasks, Body, FastAPI, Query


app = FastAPI(title="WealthLab Light Server", version="0.2.0")

DATA_DIR = Path(__file__).resolve().parent / "data"
CACHE_FILE = DATA_DIR / "ssq_draws.json"
IMPORT_DIR = DATA_DIR / "imports"
SSQ_STATE_FILE = DATA_DIR / "ssq_state.json"
SSQ_MODEL_EVALUATION_FILE = DATA_DIR / "ssq_model_evaluation.json"
FOOTBALL_CACHE_FILE = DATA_DIR / "football_matches.json"
FOOTBALL_ODDS_SNAPSHOT_FILE = DATA_DIR / "football_odds_snapshots.json"
FOOTBALL_RECOMMENDATION_FILE = DATA_DIR / "football_recommendations.json"
FOOTBALL_RESULTS_FILE = DATA_DIR / "football_results.json"
FOOTBALL_TEAM_RATINGS_FILE = DATA_DIR / "football_team_ratings.json"
FOOTBALL_MATCH_FACTS_FILE = DATA_DIR / "football_match_facts.json"
WORLDCUP_FALLBACK_FILE = DATA_DIR / "worldcup_2026_matches.json"
WORLDCUP_SCHEDULE_FILE = DATA_DIR / "worldcup_2026_schedule.json"
VERIFIED_FOOTBALL_RESULTS = [
    {
        "matchId": "wc2026-101",
        "fullTimeScore": "0:2",
        "halfTimeScore": "",
        "source": "fifa-nbc-verified",
        "updatedAt": "2026-07-14",
    }
]
CWL_URL = "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice"
SPORTTERY_FOOTBALL_URL = "https://webapi.sporttery.cn/gateway/jc/football/getMatchCalculatorV1.qry"
FIFA_WORLDCUP_SCHEDULE_URL = "https://https-www-fifa.com/en/articles/View-the-FIFA-World-Cup-26%E2%84%A2-match-schedule"
OPENFOOTBALL_WORLDCUP_URL = "https://raw.githubusercontent.com/openfootball/worldcup.json/master/2026/worldcup.json"
FIFA_RANKINGS_URL = "https://inside.fifa.com/fifa-world-ranking/men"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
    "Referer": "https://www.cwl.gov.cn/",
    "Accept": "application/json,text/plain,*/*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "X-Requested-With": "XMLHttpRequest",
}

MODEL_CONFIGS = {
    "recent_focus_v3": {"full": 0.20, "recent": 0.50, "miss": 0.22, "center": 0.08},
    "hit_rate_v4": {"full": 0.25, "recent": 0.40, "miss": 0.28, "center": 0.07},
    "balanced_v2": {"full": 0.34, "recent": 0.36, "miss": 0.22, "center": 0.08},
    "baseline_v1": {"full": 0.55, "recent": 0.0, "miss": 0.35, "center": 0.10},
}
UNIFORM_RANDOM_MODEL_VERSION = "uniform_random_v0"
ENSEMBLE_MODEL_VERSION = "ensemble_fusion"
EVALUATION_MODEL_VERSIONS = [*MODEL_CONFIGS, UNIFORM_RANDOM_MODEL_VERSION]
SSQ_EVALUATION_VERSION = "ssq-evaluation-v4"
SSQ_EVALUATION_SAMPLE_LIMIT = 60
SSQ_EVALUATION_FOLD_COUNT = 6

# 模型评估后台任务状态：避免推荐路由在前台同步触发全量回测导致超时
_ssq_evaluation_lock = threading.Lock()
_ssq_evaluation_running = False
_ssq_evaluation_done = threading.Event()
_ssq_evaluation_result: dict[str, Any] | None = None


@dataclass(frozen=True)
class SsqDraw:
    issue: str
    date: str
    redBalls: list[int]
    blueBall: int


@dataclass(frozen=True)
class Candidate:
    redBalls: list[int]
    blueBalls: list[int]
    score: float
    note: str
    reasons: list[str]
    ballDetails: list[dict[str, Any]]


@dataclass(frozen=True)
class FootballMatch:
    matchId: str
    matchNum: str
    leagueName: str
    phase: str
    kickoffTime: str
    homeTeam: str
    awayTeam: str
    neutralVenue: bool
    handicap: int
    teamStrength: dict[str, float]
    pools: dict[str, dict[str, float]]
    stadium: str
    city: str
    source: str
    updatedAt: str


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/api/lottery/ssq/draws")
async def ssq_draws(
    background_tasks: BackgroundTasks,
    limit: int = Query(default=3000, ge=1, le=5000),
    refresh: bool = Query(default=False),
) -> dict[str, Any]:
    draws = await get_draws(limit, refresh)
    state = refresh_ssq_state_settlements(draws)
    evaluation = load_ssq_model_evaluation()
    if refresh and len(draws) >= 30:
        background_tasks.add_task(run_ssq_model_evaluation_async, draws)
    return {
        "source": "cwl",
        "count": len(draws),
        "draws": [asdict(draw) for draw in draws],
        "analysis": analyze(draws),
        "stateBackup": state,
        "modelEvaluation": evaluation,
    }


@app.get("/api/lottery/ssq/recommendations")
async def ssq_recommendations(
    singleCount: int = Query(default=0, ge=0, le=10),
    compoundCount: int = Query(default=1, ge=0, le=5),
    redCount: int = Query(default=6, ge=6, le=20),
    blueCount: int = Query(default=3, ge=1, le=16),
    modelVersion: str = Query(default="auto"),
    recentWindow: int = Query(default=500, ge=30, le=500),
    limit: int = Query(default=3000, ge=30, le=5000),
    refresh: bool = Query(default=False),
) -> dict[str, Any]:
    draws = await get_draws(limit, refresh)
    if len(draws) < 30:
        return {
            "source": "cwl",
            "count": len(draws),
            "draws": [asdict(draw) for draw in draws],
            "analysis": analyze(draws, recentWindow, modelVersion),
            "predictions": [],
        }
    evaluation = get_or_wait_ssq_model_evaluation(
        draws,
        recent_window=recentWindow,
        single_count=singleCount,
        compound_count=compoundCount,
        red_count=redCount,
        blue_count=blueCount,
    )
    model_reports = evaluation.get("modelReports") or []
    fusion_weights = compute_fusion_weights(model_reports)
    if modelVersion == "auto":
        model_version = ENSEMBLE_MODEL_VERSION
    else:
        model_version = modelVersion if modelVersion in MODEL_CONFIGS else ENSEMBLE_MODEL_VERSION
    analysis = analyze(draws, recentWindow, model_version)
    predictions = recommend(
        draws,
        singleCount,
        compoundCount,
        redCount,
        blueCount,
        analysis,
        model_version,
        recentWindow,
        fusion_weights=fusion_weights,
    )
    if model_version == ENSEMBLE_MODEL_VERSION:
        selection_score = compute_fused_selection_score(model_reports, fusion_weights)
        selected_backtest = {
            "version": ENSEMBLE_MODEL_VERSION,
            "selectionScore": round(selection_score, 2),
            "fusionWeights": {version: round(weight, 4) for version, weight in fusion_weights.items()},
        }
    else:
        selected_backtest = next((item for item in model_reports if item["version"] == model_version), None)
        selection_score = float((selected_backtest or {}).get("selectionScore", 0.0))
    for prediction in predictions:
        prediction["score"] = selection_score
    number_rankings = build_number_rankings(draws, model_version, recentWindow)
    response = {
        "source": "cwl",
        "count": len(draws),
        "draws": [asdict(draw) for draw in draws],
        "analysis": analysis,
        "modelVersion": model_version,
        "recentWindow": recentWindow,
        "requestPlan": {
            "singleCount": singleCount,
            "compoundCount": compoundCount,
            "redCount": redCount,
            "blueCount": blueCount,
        },
        "predictions": predictions,
        "backtest": selected_backtest,
        "modelEvaluation": evaluation,
        "narrative": build_narrative(analysis, model_version),
        "numberRankings": number_rankings,
    }
    response["stateBackup"] = save_ssq_recommendation_state(response)
    return response


@app.get("/api/lottery/sports/football/matches")
async def football_matches(
    refresh: bool = Query(default=False),
    limit: int = Query(default=104, ge=1, le=500),
) -> dict[str, Any]:
    matches, source_status, source_message = await get_football_matches(refresh)
    results = load_football_results()
    return {
        "sourceStatus": source_status,
        "sourceMessage": source_message,
        "count": min(len(matches), limit),
        "matches": [asdict(match) for match in matches[:limit]],
        "standings": build_football_standings(matches, results),
        "supportedPlayTypes": FOOTBALL_PLAY_TYPES,
    }


@app.post("/api/lottery/sports/football/import")
def import_football_matches(payload: dict[str, Any] = Body(...)) -> dict[str, Any]:
    matches = parse_football_payload(payload, "manual")
    if not matches:
        return {"sourceStatus": "fallback", "sourceMessage": "导入内容没有有效赛事", "count": 0}
    save_football_cache(matches)
    return {"sourceStatus": "cache", "sourceMessage": "已导入足球赛事", "count": len(matches)}


@app.get("/api/lottery/sports/football/recommendations")
async def football_recommendations(
    playType: str = Query(default="all"),
    refresh: bool = Query(default=False),
    limit: int = Query(default=12, ge=1, le=50),
) -> dict[str, Any]:
    matches, source_status, source_message = await get_football_matches(refresh)
    requested = normalize_play_types(playType)
    generated_at = utc_now()
    save_football_odds_snapshots(matches, source_status, generated_at)
    recommendations = build_football_recommendations(matches, requested, limit, source_status, generated_at)
    save_football_recommendations(recommendations)
    return {
        "sourceStatus": source_status,
        "sourceMessage": source_message,
        "count": len(recommendations),
        "recommendations": recommendations,
        "supportedPlayTypes": FOOTBALL_PLAY_TYPES,
    }


@app.post("/api/lottery/sports/football/results/import")
def import_football_results(payload: dict[str, Any] = Body(...)) -> dict[str, Any]:
    results = parse_football_results_payload(payload)
    if not results:
        return {"sourceStatus": "empty", "sourceMessage": "导入内容没有有效赛果", "count": 0}
    merged = {item["matchId"]: item for item in load_football_results()}
    merged.update({item["matchId"]: item for item in results})
    save_json_list(FOOTBALL_RESULTS_FILE, sorted(merged.values(), key=lambda item: item.get("matchId", "")))
    return {"sourceStatus": "import", "sourceMessage": "已导入足球赛果", "count": len(results)}


@app.post("/api/lottery/sports/football/results/sync")
async def sync_football_results() -> dict[str, Any]:
    results = await fetch_openfootball_results()
    if not results:
        return {"sourceStatus": "empty", "sourceMessage": "未从网络源获取到已完赛赛果", "count": 0}
    merged = {item["matchId"]: item for item in load_football_results()}
    merged.update({item["matchId"]: item for item in results})
    save_json_list(FOOTBALL_RESULTS_FILE, sorted(merged.values(), key=lambda item: item.get("matchId", "")))
    return {"sourceStatus": "openfootball", "sourceMessage": "已同步 openfootball 赛果", "count": len(results)}


@app.get("/api/lottery/sports/football/model-report")
def football_model_report() -> dict[str, Any]:
    return build_football_model_report(load_football_recommendation_history(), load_football_results())


async def get_draws(limit: int, refresh: bool) -> list[SsqDraw]:
    imported = load_imported_ssq_draws()
    if imported:
        save_cache(imported)
        return imported[:limit]
    cached = load_cache()
    if cached and not refresh:
        return cached[:limit]
    try:
        draws = await refresh_ssq_draws(cached, limit) if cached else await fetch_ssq_draws(limit)
    except Exception:
        if cached:
            return cached[:limit]
        raise
    save_cache(draws)
    return draws[:limit]


async def refresh_ssq_draws(cached: list[SsqDraw], limit: int) -> list[SsqDraw]:
    latest_cached_issue = cached[0].issue
    fresh = await fetch_ssq_draws(limit, stop_issue=latest_cached_issue)
    merged = {draw.issue: draw for draw in cached}
    merged.update({draw.issue: draw for draw in fresh})
    return sorted(merged.values(), key=lambda item: item.issue, reverse=True)[:limit]


async def fetch_ssq_draws(limit: int, stop_issue: str | None = None) -> list[SsqDraw]:
    page_size = 100
    page_no = 1
    collected: dict[str, SsqDraw] = {}
    async with httpx.AsyncClient(timeout=20.0, headers=HEADERS, follow_redirects=True) as client:
        while len(collected) < limit:
            params = {
                "name": "ssq",
                "issueCount": "",
                "issueStart": "",
                "issueEnd": "",
                "dayStart": "",
                "dayEnd": "",
                "pageNo": page_no,
                "pageSize": page_size,
                "week": "",
                "systemType": "PC",
            }
            response = await client.get(CWL_URL, params=params)
            response.raise_for_status()
            payload = response.json()
            items = payload.get("result") or []
            if not items:
                break
            for item in items:
                draw = parse_cwl_item(item)
                if draw:
                    if stop_issue and draw.issue == stop_issue:
                        return sorted(collected.values(), key=lambda item: item.issue, reverse=True)
                    collected[draw.issue] = draw
            if len(items) < page_size:
                break
            page_no += 1
    return sorted(collected.values(), key=lambda item: item.issue, reverse=True)


def parse_cwl_item(item: dict[str, Any]) -> SsqDraw | None:
    issue = str(item.get("code") or item.get("issue") or "").strip()
    date = str(item.get("date") or "").split("(")[0].strip()
    reds = [int(value) for value in str(item.get("red") or "").split(",") if value.strip().isdigit()]
    blue_text = str(item.get("blue") or "").strip()
    if not blue_text.isdigit():
        return None
    blue = int(blue_text)
    if not issue or len(reds) != 6 or len(set(reds)) != 6 or not all(1 <= value <= 33 for value in reds) or not 1 <= blue <= 16:
        return None
    return SsqDraw(issue=issue, date=date, redBalls=sorted(reds), blueBall=blue)


def load_cache() -> list[SsqDraw]:
    if not CACHE_FILE.exists():
        return []
    data = json.loads(CACHE_FILE.read_text(encoding="utf-8"))
    return [SsqDraw(**item) for item in data]


def save_cache(draws: list[SsqDraw]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    CACHE_FILE.write_text(json.dumps([asdict(draw) for draw in draws], ensure_ascii=False, indent=2), encoding="utf-8")


def load_imported_ssq_draws() -> list[SsqDraw]:
    path = IMPORT_DIR / "ssq_draws.json"
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return []
    rows = data.get("draws") if isinstance(data, dict) else data
    if not isinstance(rows, list):
        return []
    draws = [SsqDraw(**item) for item in rows if valid_server_draw_dict(item)]
    return sorted(draws, key=lambda item: item.issue, reverse=True)


def empty_ssq_state() -> dict[str, Any]:
    return {"predictions": [], "researchReports": [], "lotterySettlements": []}


def load_ssq_state() -> dict[str, Any]:
    if not SSQ_STATE_FILE.exists():
        return empty_ssq_state()
    try:
        data = json.loads(SSQ_STATE_FILE.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return empty_ssq_state()
    state = empty_ssq_state()
    for key in state:
        value = data.get(key)
        if isinstance(value, list):
            state[key] = value
    return deduplicate_ssq_state(state)


def save_ssq_state(state: dict[str, Any]) -> dict[str, Any]:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    state = deduplicate_ssq_state(state)
    normalized = empty_ssq_state()
    normalized["predictions"] = list(state.get("predictions") or [])[-500:]
    normalized["researchReports"] = list(state.get("researchReports") or [])[-200:]
    normalized["lotterySettlements"] = list(state.get("lotterySettlements") or [])[-500:]
    SSQ_STATE_FILE.write_text(json.dumps(normalized, ensure_ascii=False, indent=2), encoding="utf-8")
    return normalized


def deduplicate_ssq_state(state: dict[str, Any]) -> dict[str, Any]:
    predictions_by_run: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for prediction in state.get("predictions") or []:
        issue = str(prediction.get("targetIssue") or "")
        if issue:
            run_id = str(prediction.get("runId") or f"legacy-{issue}")
            predictions_by_run.setdefault((issue, run_id), []).append(prediction)
    kept_runs: dict[str, tuple[str, str]] = {}
    for run_key, predictions in predictions_by_run.items():
        fingerprint = recommendation_fingerprint({"predictions": predictions})
        previous = kept_runs.get(fingerprint)
        generated_at = min(str(item.get("generatedAt") or "") for item in predictions)
        if previous is None or generated_at < min(str(item.get("generatedAt") or "") for item in predictions_by_run[previous]):
            kept_runs[fingerprint] = run_key
    active_runs = set(kept_runs.values())
    state["predictions"] = [
        item for item in state.get("predictions") or []
        if (str(item.get("targetIssue") or ""), str(item.get("runId") or f"legacy-{item.get('targetIssue') or ''}")) in active_runs
    ]
    state["researchReports"] = [
        item for item in state.get("researchReports") or []
        if (str(item.get("targetIssue") or ""), str(item.get("runId") or f"legacy-{item.get('targetIssue') or ''}")) in active_runs
    ]
    state["lotterySettlements"] = [
        item for item in state.get("lotterySettlements") or []
        if (str(item.get("issue") or ""), str(item.get("runId") or f"legacy-{item.get('issue') or ''}")) in active_runs
    ]
    return state


def save_ssq_recommendation_state(response: dict[str, Any]) -> dict[str, Any]:
    state = load_ssq_state()
    fingerprint = recommendation_fingerprint(response)
    existing = [item for item in state.get("predictions") or [] if item.get("recommendationFingerprint") == fingerprint]
    if existing:
        response["predictions"] = existing
        return save_ssq_state(state)
    generated_at = datetime.now(timezone.utc).isoformat()
    run_id = uuid4().hex
    predictions = [
        {
            **prediction,
            "runId": run_id,
            "generatedAt": generated_at,
            "recommendationFingerprint": fingerprint,
        }
        for prediction in response.get("predictions") or []
    ]
    if predictions:
        response["predictions"] = predictions
        state["predictions"].extend(predictions)
        report = build_research_backup({**response, "predictions": predictions})
        report["runId"] = run_id
        report["generatedAt"] = generated_at
        report["recommendationFingerprint"] = fingerprint
        state["researchReports"].append(report)
        draws = [SsqDraw(**item) for item in response.get("draws", []) if valid_server_draw_dict(item)]
        state = settle_state_predictions(state, draws)
    return save_ssq_state(state)


def recommendation_fingerprint(response: dict[str, Any]) -> str:
    predictions = [
        {
            "targetIssue": str(item.get("targetIssue") or ""),
            "sourceIssue": str(item.get("sourceIssue") or ""),
            "modelVersion": str(item.get("modelVersion") or response.get("modelVersion") or ""),
            "redBalls": sorted(int(value) for value in item.get("redBalls") or []),
            "blueBalls": sorted(int(value) for value in item.get("blueBalls") or []),
        }
        for item in response.get("predictions") or []
    ]
    payload = {
        "predictions": predictions,
    }
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def refresh_ssq_state_settlements(draws: list[SsqDraw]) -> dict[str, Any]:
    state = settle_state_predictions(load_ssq_state(), draws)
    return save_ssq_state(state)


def build_research_backup(response: dict[str, Any]) -> dict[str, Any]:
    predictions = response.get("predictions") or []
    first = predictions[0] if predictions else {}
    report = {
        "modelVersion": response.get("modelVersion", "recent_focus_v3"),
        "recentWindow": response.get("recentWindow", 500),
        "backtest": response.get("backtest"),
        "narrative": response.get("narrative") or [],
        "numberRankings": response.get("numberRankings") or {"reds": [], "blues": []},
    }
    return {
        "targetIssue": first.get("targetIssue", ""),
        "sourceIssue": first.get("sourceIssue", ""),
        "modelVersion": report["modelVersion"],
        "report": report,
    }


def settle_state_predictions(state: dict[str, Any], draws: list[SsqDraw]) -> dict[str, Any]:
    draws_by_issue = {draw.issue: draw for draw in draws}
    predictions_by_run: dict[tuple[str, str], list[dict[str, Any]]] = {}
    for prediction in state.get("predictions") or []:
        issue = str(prediction.get("targetIssue") or "")
        if issue:
            run_id = str(prediction.get("runId") or f"legacy-{issue}")
            predictions_by_run.setdefault((issue, run_id), []).append(prediction)
    settlements: dict[tuple[str, str], dict[str, Any]] = {}
    for (issue, run_id), predictions in predictions_by_run.items():
        draw = draws_by_issue.get(issue)
        if not draw:
            continue
        settlement = build_server_settlement(draw, predictions)
        if settlement:
            settlement["runId"] = run_id
            settlement["sourceIssue"] = predictions[0].get("sourceIssue", "")
            settlement["generatedAt"] = predictions[0].get("generatedAt", "")
            settlements[(issue, run_id)] = settlement
    state["lotterySettlements"] = [
        settlements[key]
        for key in sorted(settlements.keys(), key=lambda item: (item[0], item[1]), reverse=True)
        if key[0]
    ]
    return state


def build_server_settlement(draw: SsqDraw, predictions: list[dict[str, Any]]) -> dict[str, Any] | None:
    details = [settle_server_prediction(prediction, draw) for prediction in predictions]
    details = [detail for detail in details if detail]
    if not details:
        return None
    bet_count = sum(detail["betCount"] for detail in details)
    prize = sum(detail["prizeAmount"] for detail in details)
    invested = bet_count * 2.0
    tiers = {"first": 0, "second": 0, "third": 0, "fourth": 0, "fifth": 0, "sixth": 0}
    for detail in details:
        for key, value in detail["tierCounts"].items():
            tiers[key] = tiers.get(key, 0) + value
    return {
        "issue": draw.issue,
        "drawDate": draw.date,
        "settledAt": int(date.today().strftime("%Y%m%d")),
        "betCount": bet_count,
        "investedAmount": invested,
        "simulatedPrizeAmount": prize,
        "roi": (prize - invested) / invested if invested else 0.0,
        "bestRedHits": max(detail["bestRedHits"] for detail in details),
        "blueHit": any(detail["blueHit"] for detail in details),
        "tierCounts": tiers,
        "details": details,
    }


def settle_server_prediction(prediction: dict[str, Any], draw: SsqDraw) -> dict[str, Any] | None:
    reds = [int(value) for value in prediction.get("redBalls") or []]
    blues = [int(value) for value in prediction.get("blueBalls") or []]
    if len(reds) < 6 or not blues:
        return None
    red_overlap = len(set(reds) & set(draw.redBalls))
    non_hit_reds = len(reds) - red_overlap
    has_blue = draw.blueBall in blues
    losing_blue_count = len(blues) - (1 if has_blue else 0)
    tiers = {"first": 0, "second": 0, "third": 0, "fourth": 0, "fifth": 0, "sixth": 0}
    for red_hits in range(0, 7):
        red_count = combination(red_overlap, red_hits) * combination(non_hit_reds, 6 - red_hits)
        if red_count <= 0:
            continue
        if has_blue:
            add_server_tier(tiers, red_hits, True, red_count)
        if losing_blue_count > 0:
            add_server_tier(tiers, red_hits, False, red_count * losing_blue_count)
    prize = tiers["third"] * 3000.0 + tiers["fourth"] * 200.0 + tiers["fifth"] * 10.0 + tiers["sixth"] * 5.0
    return {
        "label": "复式" if len(reds) > 6 or len(blues) > 1 else "单式",
        "redBalls": sorted(reds),
        "blueBalls": sorted(blues),
        "betCount": combination(len(reds), 6) * len(blues),
        "bestRedHits": min(red_overlap, 6),
        "blueHit": has_blue,
        "prizeAmount": prize,
        "tierCounts": tiers,
    }


def add_server_tier(target: dict[str, int], red_hits: int, blue_hit: bool, count: int) -> None:
    tier = None
    if red_hits == 6 and blue_hit:
        tier = "first"
    elif red_hits == 6:
        tier = "second"
    elif red_hits == 5 and blue_hit:
        tier = "third"
    elif red_hits == 5 or (red_hits == 4 and blue_hit):
        tier = "fourth"
    elif red_hits == 4 or (red_hits == 3 and blue_hit):
        tier = "fifth"
    elif blue_hit and 0 <= red_hits <= 2:
        tier = "sixth"
    if tier:
        target[tier] = target.get(tier, 0) + count


def combination(n: int, k: int) -> int:
    if k < 0 or k > n:
        return 0
    return math.comb(n, k)


def valid_server_draw_dict(item: dict[str, Any]) -> bool:
    return bool(item.get("issue") and item.get("date") and item.get("redBalls") and item.get("blueBall"))


FOOTBALL_PLAY_TYPES = {
    "had": "胜平负",
    "hhad": "让球胜平负",
    "crs": "比分",
    "ttg": "总进球",
    "hafu": "半全场",
}


async def get_football_matches(refresh: bool) -> tuple[list[FootballMatch], str, str]:
    imported = read_football_file(IMPORT_DIR / "football_matches.json", "import")
    if imported:
        matches = merge_schedule_and_odds(load_worldcup_schedule_fallback(), imported, include_extra=True)
        matches = apply_team_ratings(matches, load_team_ratings())
        save_football_cache(matches)
        return matches, "import", "使用服务端导入目录中的足球赛事，并补齐世界杯赛程"
    if refresh:
        try:
            official = await fetch_sporttery_football_matches()
            if official:
                schedule = await fetch_openfootball_worldcup_matches() or await fetch_worldcup_schedule_matches()
                base = merge_schedule_and_odds(
                    schedule or load_worldcup_schedule_fallback(), load_worldcup_fallback(), include_extra=False
                )
                matches = merge_schedule_and_odds(base, official, include_extra=False)
                ratings = await fetch_fifa_team_ratings()
                matches = apply_team_ratings(matches, ratings or load_team_ratings())
                save_football_cache(matches)
                return matches, "official", "官方竞彩数据更新成功，并补齐世界杯赛程"
        except Exception as error:
            cached = load_football_cache()
            if cached:
                return merge_schedule_and_odds(load_worldcup_schedule_fallback(), cached, include_extra=False), "cache", f"官方竞彩抓取失败，使用缓存：{short_error(error)}"
            fallback = apply_team_ratings(merge_schedule_and_odds(load_worldcup_schedule_fallback(), load_worldcup_fallback(), include_extra=False), load_team_ratings())
            return fallback, "fallback", f"官方竞彩抓取失败，使用内置世界杯赛程：{short_error(error)}"
    cached = load_football_cache()
    if cached:
        return merge_schedule_and_odds(load_worldcup_schedule_fallback(), cached, include_extra=False), "cache", "使用本地足球赛事缓存"
    fallback = apply_team_ratings(merge_schedule_and_odds(load_worldcup_schedule_fallback(), load_worldcup_fallback(), include_extra=False), load_team_ratings())
    return fallback, "fallback", "使用内置世界杯赛程"


async def fetch_sporttery_football_matches() -> list[FootballMatch]:
    params = {"poolCode": "hhad,had,crs,ttg,hafu", "channel": "c"}
    async with httpx.AsyncClient(timeout=12.0, headers=HEADERS, follow_redirects=True) as client:
        response = await client.get(SPORTTERY_FOOTBALL_URL, params=params)
        response.raise_for_status()
        payload = response.json()
    return parse_sporttery_matches(payload)


async def fetch_worldcup_schedule_matches() -> list[FootballMatch]:
    async with httpx.AsyncClient(timeout=12.0, headers=HEADERS, follow_redirects=True) as client:
        response = await client.get(FIFA_WORLDCUP_SCHEDULE_URL)
        response.raise_for_status()
        text = response.text
    matches = parse_fifa_schedule_page(text)
    if matches:
        save_football_schedule_cache(matches)
    return matches


async def fetch_openfootball_worldcup_payload() -> dict[str, Any]:
    async with httpx.AsyncClient(timeout=12.0, headers=HEADERS, follow_redirects=True) as client:
        response = await client.get(OPENFOOTBALL_WORLDCUP_URL)
        response.raise_for_status()
        return response.json()


async def fetch_openfootball_worldcup_matches() -> list[FootballMatch]:
    payload = await fetch_openfootball_worldcup_payload()
    matches = parse_openfootball_worldcup(payload)
    if matches:
        save_football_schedule_cache(matches)
    results = parse_openfootball_results(payload)
    if results:
        merged = {item["matchId"]: item for item in load_football_results()}
        merged.update({item["matchId"]: item for item in results})
        save_json_list(FOOTBALL_RESULTS_FILE, sorted(merged.values(), key=lambda item: item.get("matchId", "")))
    return matches


async def fetch_openfootball_results() -> list[dict[str, Any]]:
    payload = await fetch_openfootball_worldcup_payload()
    return parse_openfootball_results(payload)


async def fetch_fifa_team_ratings() -> dict[str, dict[str, Any]]:
    async with httpx.AsyncClient(timeout=12.0, headers=HEADERS, follow_redirects=True) as client:
        response = await client.get(FIFA_RANKINGS_URL)
        response.raise_for_status()
        ratings = parse_fifa_rankings_page(response.text)
    if ratings:
        save_json_list(FOOTBALL_TEAM_RATINGS_FILE, [{"team": team, **data} for team, data in ratings.items()])
    return ratings


def load_football_cache() -> list[FootballMatch]:
    return read_football_file(FOOTBALL_CACHE_FILE, "cache")


def load_worldcup_fallback() -> list[FootballMatch]:
    return read_football_file(WORLDCUP_FALLBACK_FILE, "fallback")


def load_worldcup_schedule_fallback() -> list[FootballMatch]:
    from_file = read_football_file(WORLDCUP_SCHEDULE_FILE, "schedule")
    schedule = from_file or build_static_worldcup_schedule()
    return merge_schedule_and_odds(schedule, load_worldcup_fallback(), include_extra=False)


def read_football_file(path: Path, source: str) -> list[FootballMatch]:
    if not path.exists():
        return []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return []
    return parse_football_payload(payload, source)


def save_football_cache(matches: list[FootballMatch]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    FOOTBALL_CACHE_FILE.write_text(
        json.dumps({"matches": [asdict(match) for match in matches]}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def save_football_schedule_cache(matches: list[FootballMatch]) -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    WORLDCUP_SCHEDULE_FILE.write_text(
        json.dumps({"matches": [asdict(match) for match in matches]}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def parse_football_payload(payload: dict[str, Any], source: str) -> list[FootballMatch]:
    rows = payload.get("matches") or payload.get("value", {}).get("matches") or []
    matches = [normalize_football_match(item, source) for item in rows if isinstance(item, dict)]
    return sorted((match for match in matches if match), key=lambda item: item.kickoffTime)


def parse_openfootball_worldcup(payload: dict[str, Any]) -> list[FootballMatch]:
    rows = payload.get("matches") or []
    matches: list[FootballMatch] = []
    for index, item in enumerate(rows, start=1):
        if not isinstance(item, dict):
            continue
        home = normalize_openfootball_team(str(item.get("team1") or ""))
        away = normalize_openfootball_team(str(item.get("team2") or ""))
        kickoff = openfootball_kickoff_beijing(str(item.get("date") or ""), str(item.get("time") or ""))
        if not home or not away or not kickoff:
            continue
        match_num = str(index).zfill(3)
        ground = str(item.get("ground") or "")
        stadium, city = split_openfootball_ground(ground)
        matches.append(
            FootballMatch(
                matchId=f"wc2026-{match_num}",
                matchNum=match_num,
                leagueName="FIFA World Cup 2026",
                phase=str(item.get("group") or item.get("round") or "World Cup"),
                kickoffTime=kickoff,
                homeTeam=home,
                awayTeam=away,
                neutralVenue=not home_has_host_advantage(home, city),
                handicap=0,
                teamStrength=infer_team_strength(home, away),
                pools={},
                stadium=stadium,
                city=city,
                source="openfootball",
                updatedAt=date.today().isoformat(),
            )
        )
    return matches


def parse_openfootball_results(payload: dict[str, Any]) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for index, item in enumerate(payload.get("matches") or [], start=1):
        if not isinstance(item, dict):
            continue
        score = item.get("score") if isinstance(item.get("score"), dict) else {}
        full_time = score.get("ft")
        if not isinstance(full_time, list) or len(full_time) != 2:
            continue
        half_time = score.get("ht")
        results.append(
            {
                "matchId": f"wc2026-{index:03d}",
                "fullTimeScore": f"{safe_int(full_time[0])}:{safe_int(full_time[1])}",
                "halfTimeScore": f"{safe_int(half_time[0])}:{safe_int(half_time[1])}" if isinstance(half_time, list) and len(half_time) == 2 else "",
                "source": "openfootball",
                "updatedAt": utc_now(),
            }
        )
    return results


def openfootball_kickoff_beijing(date_value: str, time_value: str) -> str:
    date_value = date_value.strip()
    time_value = time_value.strip()
    if not date_value:
        return ""
    match = re.match(r"^(\d{1,2}):(\d{2})(?:\s+UTC([+-]\d{1,2}))?$", time_value)
    if not match:
        return f"{date_value}T00:00:00+08:00"
    hour = int(match.group(1))
    minute = int(match.group(2))
    offset_hours = int(match.group(3) or "0")
    source_time = datetime.fromisoformat(f"{date_value}T{hour:02d}:{minute:02d}:00").replace(tzinfo=timezone(timedelta(hours=offset_hours)))
    return source_time.astimezone(timezone(timedelta(hours=8))).isoformat()


def split_openfootball_ground(ground: str) -> tuple[str, str]:
    cleaned = ground.strip()
    if not cleaned:
        return "", ""
    if "," in cleaned:
        stadium, city = [part.strip() for part in cleaned.split(",", 1)]
        return stadium, city
    city = cleaned
    if "(" in cleaned and ")" in cleaned:
        city = cleaned.split("(", 1)[0].strip()
    stadium = next((name for name, mapped_city in STADIUM_CITY.items() if mapped_city == city), cleaned)
    return stadium, city


def normalize_openfootball_team(name: str) -> str:
    return {
        "South Korea": "Korea Republic",
        "Czech Republic": "Czechia",
        "Ivory Coast": "Cote d'Ivoire",
        "Cape Verde": "Cabo Verde",
    }.get(name.strip(), name.strip())


def parse_fifa_rankings_page(text: str) -> dict[str, dict[str, Any]]:
    rows: dict[str, dict[str, Any]] = {}
    pattern = re.compile(
        r'"rank"\s*:\s*(\d+).*?"name"\s*:\s*"([^"]+)".*?"(?:totalPoints|points)"\s*:\s*([0-9.]+)',
        re.DOTALL,
    )
    matches = pattern.findall(text)
    if not matches:
        pattern = re.compile(
            r'"name"\s*:\s*"([^"]+)".*?"rank"\s*:\s*(\d+).*?"(?:totalPoints|points)"\s*:\s*([0-9.]+)',
            re.DOTALL,
        )
        matches = [(rank, name, points) for name, rank, points in pattern.findall(text)]
    for rank_text, name, points_text in matches:
        rank = safe_int(rank_text)
        points = safe_float(points_text) or 0.0
        if not name or not rank:
            continue
        rows[normalize_openfootball_team(name)] = {
            "rank": rank,
            "points": points,
            "strength": round(max(0.35, min(0.99, 0.99 - (rank - 1) / 240)), 4),
            "source": "fifa_ranking",
            "updatedAt": date.today().isoformat(),
        }
    return rows


def load_team_ratings() -> dict[str, dict[str, Any]]:
    imported = load_json_list(IMPORT_DIR / "team_ratings.json")
    rows = imported or load_json_list(FOOTBALL_TEAM_RATINGS_FILE)
    return {str(item.get("team")): item for item in rows if item.get("team")}


def load_match_facts() -> dict[str, dict[str, Any]]:
    imported = read_json_payload(IMPORT_DIR / "football_match_facts.json")
    cached = read_json_payload(FOOTBALL_MATCH_FACTS_FILE)
    payload = imported or cached or {}
    rows = payload.get("matches") or payload.get("items") or []
    return {str(item.get("matchId")): item for item in rows if isinstance(item, dict) and item.get("matchId")}


def read_json_payload(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return {}
    return payload if isinstance(payload, dict) else {}


def apply_team_ratings(matches: list[FootballMatch], ratings: dict[str, dict[str, Any]]) -> list[FootballMatch]:
    if not ratings:
        return matches
    updated: list[FootballMatch] = []
    for match in matches:
        if match.source.startswith("public-market-snapshot-"):
            updated.append(match)
            continue
        home_rating = ratings.get(match.homeTeam)
        away_rating = ratings.get(match.awayTeam)
        strength = dict(match.teamStrength)
        if home_rating:
            strength["home"] = safe_float(home_rating.get("strength")) or strength.get("home", 0.5)
        if away_rating:
            strength["away"] = safe_float(away_rating.get("strength")) or strength.get("away", 0.5)
        updated.append(FootballMatch(**{**asdict(match), "teamStrength": strength}))
    return updated


def build_football_standings(matches: list[FootballMatch], results: list[dict[str, Any]]) -> list[dict[str, Any]]:
    match_by_id = {match.matchId: match for match in matches}
    table: dict[tuple[str, str], dict[str, Any]] = {}
    for result in results:
        match = match_by_id.get(str(result.get("matchId")))
        score = parse_score(str(result.get("fullTimeScore") or ""))
        if not match or not score or not match.phase.startswith("Group"):
            continue
        home_goals, away_goals = score
        for team, goals_for, goals_against in [
            (match.homeTeam, home_goals, away_goals),
            (match.awayTeam, away_goals, home_goals),
        ]:
            key = (match.phase, team)
            table.setdefault(
                key,
                {"group": match.phase, "team": team, "played": 0, "wins": 0, "draws": 0, "losses": 0, "goalsFor": 0, "goalsAgainst": 0, "goalDifference": 0, "points": 0},
            )
            row = table[key]
            row["played"] += 1
            row["goalsFor"] += goals_for
            row["goalsAgainst"] += goals_against
            row["goalDifference"] = row["goalsFor"] - row["goalsAgainst"]
            if goals_for > goals_against:
                row["wins"] += 1
                row["points"] += 3
            elif goals_for == goals_against:
                row["draws"] += 1
                row["points"] += 1
            else:
                row["losses"] += 1
    return sorted(table.values(), key=lambda item: (item["group"], -item["points"], -item["goalDifference"], -item["goalsFor"], item["team"]))


STADIUM_CITY = {
    "Mexico City Stadium": "Mexico City",
    "Estadio Azteca Mexico City": "Mexico City",
    "Estadio Guadalajara": "Guadalajara",
    "Estadio Akron": "Guadalajara",
    "Toronto Stadium": "Toronto",
    "BMO Field": "Toronto",
    "Los Angeles Stadium": "Los Angeles",
    "SoFi Stadium": "Los Angeles",
    "Boston Stadium": "Boston",
    "Gillette Stadium": "Boston",
    "BC Place Vancouver": "Vancouver",
    "BC Place": "Vancouver",
    "New York New Jersey Stadium": "New York / New Jersey",
    "MetLife Stadium": "New York / New Jersey",
    "San Francisco Bay Area Stadium": "San Francisco Bay Area",
    "Levi's Stadium": "San Francisco Bay Area",
    "Philadelphia Stadium": "Philadelphia",
    "Lincoln Financial Field": "Philadelphia",
    "Houston Stadium": "Houston",
    "NRG Stadium": "Houston",
    "Dallas Stadium": "Dallas",
    "AT&T Stadium": "Dallas",
    "Estadio Monterrey": "Monterrey",
    "Estadio BBVA": "Monterrey",
    "Miami Stadium": "Miami",
    "Hard Rock Stadium": "Miami",
    "Atlanta Stadium": "Atlanta",
    "Mercedes-Benz Stadium": "Atlanta",
    "Seattle Stadium": "Seattle",
    "Lumen Field": "Seattle",
    "Kansas City Stadium": "Kansas City",
    "GEHA Field at Arrowhead Stadium": "Kansas City",
}


STATIC_WORLDCUP_2026_SCHEDULE = """
001|2026-06-11|Group A|Mexico|South Africa|Mexico City Stadium
002|2026-06-11|Group A|Korea Republic|Czechia|Estadio Guadalajara
003|2026-06-12|Group B|Canada|Bosnia and Herzegovina|Toronto Stadium
004|2026-06-12|Group D|USA|Paraguay|Los Angeles Stadium
005|2026-06-13|Group C|Haiti|Scotland|Boston Stadium
006|2026-06-13|Group D|Australia|Turkiye|BC Place Vancouver
007|2026-06-13|Group C|Brazil|Morocco|New York New Jersey Stadium
008|2026-06-13|Group B|Qatar|Switzerland|San Francisco Bay Area Stadium
009|2026-06-14|Group E|Cote d'Ivoire|Ecuador|Philadelphia Stadium
010|2026-06-14|Group E|Germany|Curacao|Houston Stadium
011|2026-06-14|Group F|Netherlands|Japan|Dallas Stadium
012|2026-06-14|Group F|Sweden|Tunisia|Estadio Monterrey
013|2026-06-15|Group H|Saudi Arabia|Uruguay|Miami Stadium
014|2026-06-15|Group H|Spain|Cabo Verde|Atlanta Stadium
015|2026-06-15|Group G|Iran|New Zealand|Los Angeles Stadium
016|2026-06-15|Group G|Belgium|Egypt|Seattle Stadium
017|2026-06-16|Group I|France|Senegal|New York New Jersey Stadium
018|2026-06-16|Group I|Iraq|Norway|Boston Stadium
019|2026-06-16|Group J|Argentina|Algeria|Kansas City Stadium
020|2026-06-16|Group J|Austria|Jordan|San Francisco Bay Area Stadium
021|2026-06-17|Group L|Ghana|Panama|Toronto Stadium
022|2026-06-17|Group L|England|Croatia|Dallas Stadium
023|2026-06-17|Group K|Portugal|DR Congo|Houston Stadium
024|2026-06-17|Group K|Uzbekistan|Colombia|Mexico City Stadium
025|2026-06-18|Group A|Czechia|South Africa|Atlanta Stadium
026|2026-06-18|Group B|Switzerland|Bosnia and Herzegovina|Los Angeles Stadium
027|2026-06-18|Group B|Canada|Qatar|BC Place Vancouver
028|2026-06-18|Group A|Mexico|Korea Republic|Estadio Guadalajara
029|2026-06-19|Group C|Brazil|Haiti|Philadelphia Stadium
030|2026-06-19|Group C|Scotland|Morocco|Boston Stadium
031|2026-06-19|Group D|Turkiye|Paraguay|San Francisco Bay Area Stadium
032|2026-06-19|Group D|USA|Australia|Seattle Stadium
033|2026-06-20|Group E|Germany|Cote d'Ivoire|Toronto Stadium
034|2026-06-20|Group E|Ecuador|Curacao|Kansas City Stadium
035|2026-06-20|Group F|Netherlands|Sweden|Houston Stadium
036|2026-06-20|Group F|Tunisia|Japan|Estadio Monterrey
037|2026-06-21|Group H|Uruguay|Cabo Verde|Miami Stadium
038|2026-06-21|Group H|Spain|Saudi Arabia|Atlanta Stadium
039|2026-06-21|Group G|Belgium|Iran|Los Angeles Stadium
040|2026-06-21|Group G|New Zealand|Egypt|BC Place Vancouver
041|2026-06-22|Group I|Norway|Senegal|New York New Jersey Stadium
042|2026-06-22|Group I|France|Iraq|Philadelphia Stadium
043|2026-06-22|Group J|Argentina|Austria|Dallas Stadium
044|2026-06-22|Group J|Jordan|Algeria|San Francisco Bay Area Stadium
045|2026-06-23|Group L|England|Ghana|Boston Stadium
046|2026-06-23|Group L|Panama|Croatia|Toronto Stadium
047|2026-06-23|Group K|Portugal|Uzbekistan|Houston Stadium
048|2026-06-23|Group K|Colombia|DR Congo|Estadio Guadalajara
049|2026-06-24|Group C|Scotland|Brazil|Miami Stadium
050|2026-06-24|Group C|Morocco|Haiti|Atlanta Stadium
051|2026-06-24|Group B|Switzerland|Canada|BC Place Vancouver
052|2026-06-24|Group B|Bosnia and Herzegovina|Qatar|Seattle Stadium
053|2026-06-24|Group A|Czechia|Mexico|Mexico City Stadium
054|2026-06-24|Group A|South Africa|Korea Republic|Estadio Monterrey
055|2026-06-25|Group E|Curacao|Cote d'Ivoire|Philadelphia Stadium
056|2026-06-25|Group E|Ecuador|Germany|New York New Jersey Stadium
057|2026-06-25|Group F|Japan|Sweden|Dallas Stadium
058|2026-06-25|Group F|Tunisia|Netherlands|Kansas City Stadium
059|2026-06-25|Group D|Turkiye|USA|Los Angeles Stadium
060|2026-06-25|Group D|Paraguay|Australia|San Francisco Bay Area Stadium
061|2026-06-26|Group I|Norway|France|Boston Stadium
062|2026-06-26|Group I|Senegal|Iraq|Toronto Stadium
063|2026-06-26|Group G|Egypt|Iran|Seattle Stadium
064|2026-06-26|Group G|New Zealand|Belgium|BC Place Vancouver
065|2026-06-26|Group H|Cabo Verde|Saudi Arabia|Houston Stadium
066|2026-06-26|Group H|Uruguay|Spain|Estadio Guadalajara
067|2026-06-27|Group L|Panama|England|New York New Jersey Stadium
068|2026-06-27|Group L|Croatia|Ghana|Philadelphia Stadium
069|2026-06-27|Group J|Algeria|Austria|Kansas City Stadium
070|2026-06-27|Group J|Jordan|Argentina|Dallas Stadium
071|2026-06-27|Group K|Colombia|Portugal|Miami Stadium
072|2026-06-27|Group K|DR Congo|Uzbekistan|Atlanta Stadium
073|2026-06-28|Round of 32|Group A Runners Up|Group B Runners Up|Los Angeles Stadium
074|2026-06-29|Round of 32|Group E Winners|Group A/B/C/D/F 3rd Place|Boston Stadium
075|2026-06-29|Round of 32|Group F Winners|Group C Runners Up|Estadio Monterrey
076|2026-06-29|Round of 32|Group C Winners|Group F Runners Up|Houston Stadium
077|2026-06-30|Round of 32|Group I Winners|Group C/D/F/G/H 3rd Place|New York New Jersey Stadium
078|2026-06-30|Round of 32|Group E Runners Up|Group I Runners Up|Dallas Stadium
079|2026-06-30|Round of 32|Group A Winners|Group C/E/F/H/I 3rd Place|Mexico City Stadium
080|2026-07-01|Round of 32|Group L Winners|Group E/H/I/J/K 3rd Place|Atlanta Stadium
081|2026-07-01|Round of 32|Group D Winners|Group B/E/F/I/J 3rd Place|San Francisco Bay Area Stadium
082|2026-07-01|Round of 32|Group G Winners|Group A/E/H/I/J 3rd Place|Seattle Stadium
083|2026-07-02|Round of 32|Group K Runners Up|Group L Runners Up|Toronto Stadium
084|2026-07-02|Round of 32|Group H Winners|Group J Runners Up|Los Angeles Stadium
085|2026-07-02|Round of 32|Group B Winners|Group E/F/G/I/J 3rd Place|BC Place Vancouver
086|2026-07-03|Round of 32|Group J Winners|Group H Runners Up|Miami Stadium
087|2026-07-03|Round of 32|Group K Winners|Group D/E/I/J/L 3rd Place|Kansas City Stadium
088|2026-07-03|Round of 32|Group D Runners Up|Group G Runners Up|Dallas Stadium
089|2026-07-04|Round of 16|Match 74 Winner|Match 77 Winner|Philadelphia Stadium
090|2026-07-04|Round of 16|Match 73 Winner|Match 75 Winner|Houston Stadium
091|2026-07-05|Round of 16|Match 76 Winner|Match 78 Winner|New York New Jersey Stadium
092|2026-07-05|Round of 16|Match 79 Winner|Match 80 Winner|Mexico City Stadium
093|2026-07-06|Round of 16|Match 83 Winner|Match 84 Winner|Dallas Stadium
094|2026-07-06|Round of 16|Match 81 Winner|Match 82 Winner|Seattle Stadium
095|2026-07-07|Round of 16|Match 86 Winner|Match 88 Winner|Atlanta Stadium
096|2026-07-07|Round of 16|Match 85 Winner|Match 87 Winner|BC Place Vancouver
097|2026-07-09|Quarter-final|Match 89 Winner|Match 90 Winner|Boston Stadium
098|2026-07-10|Quarter-final|Match 93 Winner|Match 94 Winner|Los Angeles Stadium
099|2026-07-11|Quarter-final|Match 91 Winner|Match 92 Winner|Miami Stadium
100|2026-07-11|Quarter-final|Match 95 Winner|Match 96 Winner|Kansas City Stadium
101|2026-07-14|Semi-final|France|Spain|Dallas Stadium
102|2026-07-15|Semi-final|England|Argentina|Atlanta Stadium
103|2026-07-18|Third-place|France|Match 102 Loser|Miami Stadium
104|2026-07-19|Final|Spain|Match 102 Winner|New York New Jersey Stadium
""".strip()


def build_static_worldcup_schedule() -> list[FootballMatch]:
    matches: list[FootballMatch] = []
    for row in STATIC_WORLDCUP_2026_SCHEDULE.splitlines():
        match_num, kickoff, phase, home, away, stadium = row.split("|")
        city = STADIUM_CITY.get(stadium, "")
        matches.append(
            FootballMatch(
                matchId=f"wc2026-{match_num}",
                matchNum=match_num,
                leagueName="FIFA World Cup 2026",
                phase=phase,
                kickoffTime=kickoff,
                homeTeam=home,
                awayTeam=away,
                neutralVenue=not home_has_host_advantage(home, city),
                handicap=0,
                teamStrength=infer_team_strength(home, away),
                pools={},
                stadium=stadium,
                city=city,
                source="schedule",
                updatedAt=date.today().isoformat(),
            )
        )
    return matches


def parse_fifa_schedule_page(text: str) -> list[FootballMatch]:
    date_by_match: dict[str, str] = {}
    current_date = ""
    month_numbers = {
        "June": "06",
        "July": "07",
    }
    for raw_line in text.splitlines():
        line = re.sub(r"\s+", " ", raw_line).strip()
        date_match = re.search(r"(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday), (\d{1,2}) (June|July) 2026", line)
        if date_match:
            current_date = f"2026-{month_numbers[date_match.group(2)]}-{int(date_match.group(1)):02d}"
            continue
        match = re.search(r"Match\s+(\d{1,3}).*?(?:-|–)\s*([^–-]+)$", line)
        if match and current_date:
            date_by_match[match.group(1).zfill(3)] = current_date
    if len(date_by_match) < 72:
        return []
    schedule = build_static_worldcup_schedule()
    updated: list[FootballMatch] = []
    for match in schedule:
        updated.append(
            FootballMatch(
                **{
                    **asdict(match),
                    "kickoffTime": date_by_match.get(match.matchNum, match.kickoffTime),
                    "source": "fifa_schedule",
                    "updatedAt": date.today().isoformat(),
                }
            )
        )
    return updated


def home_has_host_advantage(home: str, city: str) -> bool:
    if home == "Mexico" and city in {"Mexico City", "Guadalajara", "Monterrey"}:
        return True
    if home == "Canada" and city in {"Toronto", "Vancouver"}:
        return True
    return home == "USA" and city in {"Los Angeles", "Seattle"}


def is_knockout_team_placeholder(team: str) -> bool:
    return bool(
        re.fullmatch(r"[WL]\d{2,3}", team)
        or re.fullmatch(r"Match \d{1,3} (?:Winner|Loser)", team)
    )


def merge_schedule_and_odds(schedule: list[FootballMatch], odds_matches: list[FootballMatch], include_extra: bool = True) -> list[FootballMatch]:
    by_num = {match.matchNum.zfill(3): match for match in schedule}
    for odds in odds_matches:
        key = odds.matchNum.zfill(3)
        base = by_num.get(key)
        if base:
            by_num[key] = FootballMatch(
                matchId=base.matchId or odds.matchId,
                matchNum=base.matchNum,
                leagueName=base.leagueName or odds.leagueName,
                phase=base.phase or odds.phase,
                kickoffTime=base.kickoffTime or odds.kickoffTime,
                homeTeam=odds.homeTeam if is_knockout_team_placeholder(base.homeTeam) and odds.homeTeam else base.homeTeam or odds.homeTeam,
                awayTeam=odds.awayTeam if is_knockout_team_placeholder(base.awayTeam) and odds.awayTeam else base.awayTeam or odds.awayTeam,
                neutralVenue=base.neutralVenue,
                handicap=odds.handicap,
                teamStrength=odds.teamStrength if odds.pools else base.teamStrength,
                pools=odds.pools or base.pools,
                stadium=base.stadium or odds.stadium,
                city=base.city or odds.city,
                source=odds.source if odds.pools or not base.pools else base.source,
                updatedAt=max(base.updatedAt, odds.updatedAt),
            )
        elif include_extra:
            by_num[key] = odds
    return sorted(by_num.values(), key=lambda item: (item.kickoffTime, item.matchNum))


def parse_sporttery_matches(payload: dict[str, Any]) -> list[FootballMatch]:
    groups = payload.get("value", {}).get("matchInfoList") or []
    matches: list[FootballMatch] = []
    for group in groups:
        business_date = str(group.get("businessDate") or "")
        for item in group.get("subMatchList") or []:
            pools = parse_sporttery_odds(item.get("oddsList") or [])
            if not pools:
                continue
            kickoff = str(item.get("matchDate") or item.get("matchTime") or business_date)
            matches.append(
                FootballMatch(
                    matchId=str(item.get("matchId") or f"{business_date}-{item.get('matchNum', '')}"),
                    matchNum=str(item.get("matchNum") or ""),
                    leagueName=str(item.get("leagueAbbName") or item.get("leagueName") or "竞彩足球"),
                    phase=str(item.get("matchRound") or item.get("phase") or "竞彩赛事"),
                    kickoffTime=kickoff,
                    homeTeam=str(item.get("homeTeamAllName") or item.get("homeTeamAbbName") or ""),
                    awayTeam=str(item.get("awayTeamAllName") or item.get("awayTeamAbbName") or ""),
                    neutralVenue=bool(item.get("neutralVenue") or False),
                    handicap=safe_int(item.get("goalLine")),
                    teamStrength=infer_team_strength(str(item.get("homeTeamAllName") or ""), str(item.get("awayTeamAllName") or "")),
                    pools=pools,
                    stadium=str(item.get("venue") or item.get("stadium") or ""),
                    city=str(item.get("city") or ""),
                    source="official",
                    updatedAt=date.today().isoformat(),
                )
            )
    return sorted(matches, key=lambda item: item.kickoffTime)


def parse_sporttery_odds(rows: list[dict[str, Any]]) -> dict[str, dict[str, float]]:
    pools: dict[str, dict[str, float]] = {}
    for row in rows:
        code = str(row.get("poolCode") or "").lower()
        if code not in FOOTBALL_PLAY_TYPES:
            continue
        if code in {"had", "hhad"}:
            pools[code] = compact_odds({"H": row.get("h"), "D": row.get("d"), "A": row.get("a")})
        elif code == "ttg":
            pools[code] = compact_odds({str(index): row.get(f"s{index}") for index in range(7)} | {"7+": row.get("s7")})
        elif code == "hafu":
            pools[code] = compact_odds({key.upper(): row.get(key.lower()) for key in ["HH", "HD", "HA", "DH", "DD", "DA", "AH", "AD", "AA"]})
        elif code == "crs":
            pools[code] = compact_odds({key: value for key, value in row.items() if key not in {"poolCode", "goalLine"}})
    return pools


def normalize_football_match(item: dict[str, Any], source: str) -> FootballMatch | None:
    match_id = str(item.get("matchId") or item.get("id") or "").strip()
    home = str(item.get("homeTeam") or "").strip()
    away = str(item.get("awayTeam") or "").strip()
    kickoff = str(item.get("kickoffTime") or item.get("date") or "").strip()
    pools = normalize_pools(item.get("pools") or {})
    if not match_id or not home or not away or not kickoff:
        return None
    strength = item.get("teamStrength") if isinstance(item.get("teamStrength"), dict) else infer_team_strength(home, away)
    return FootballMatch(
        matchId=match_id,
        matchNum=str(item.get("matchNum") or match_id),
        leagueName=str(item.get("leagueName") or "FIFA World Cup 2026"),
        phase=str(item.get("phase") or "世界杯"),
        kickoffTime=kickoff,
        homeTeam=home,
        awayTeam=away,
        neutralVenue=bool(item.get("neutralVenue", True)),
        handicap=safe_int(item.get("handicap")),
        teamStrength={"home": float(strength.get("home", 0.5)), "away": float(strength.get("away", 0.5))},
        pools=pools,
        stadium=str(item.get("stadium") or item.get("venue") or ""),
        city=str(item.get("city") or ""),
        source=str(item.get("source") or source),
        updatedAt=str(item.get("updatedAt") or date.today().isoformat()),
    )


def normalize_pools(raw: dict[str, Any]) -> dict[str, dict[str, float]]:
    pools: dict[str, dict[str, float]] = {}
    for key, value in raw.items():
        code = str(key).lower()
        if code not in FOOTBALL_PLAY_TYPES or not isinstance(value, dict):
            continue
        odds = compact_odds(value)
        if odds:
            pools[code] = odds
    return pools


def compact_odds(values: dict[str, Any]) -> dict[str, float]:
    odds: dict[str, float] = {}
    for key, value in values.items():
        number = safe_float(value)
        if number and number > 1.0:
            odds[str(key)] = round(number, 4)
    return odds


def build_football_recommendations(
    matches: list[FootballMatch],
    play_types: list[str],
    limit: int,
    source_status: str = "unknown",
    generated_at: str | None = None,
) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    created_at = generated_at or utc_now()
    for match in matches:
        for play_type in play_types:
            odds = match.pools.get(play_type)
            if not odds:
                continue
            profile = build_match_model_profile(match)
            selection, metrics = select_football_pick(match, play_type, odds, source_status, profile)
            rows.append(
                {
                    "generatedAt": created_at,
                    "modelName": profile["modelName"],
                    "matchId": match.matchId,
                    "matchNum": match.matchNum,
                    "leagueName": match.leagueName,
                    "phase": match.phase,
                    "kickoffTime": match.kickoffTime,
                    "homeTeam": match.homeTeam,
                    "awayTeam": match.awayTeam,
                    "handicap": match.handicap,
                    "playType": play_type,
                    "playName": FOOTBALL_PLAY_TYPES[play_type],
                    "selection": selection,
                    "odds": odds.get(selection),
                    "confidence": metrics["confidence"],
                    "impliedProbability": metrics["impliedProbability"],
                    "fairProbability": metrics["fairProbability"],
                    "modelProbability": metrics["modelProbability"],
                    "overround": metrics["overround"],
                    "edge": metrics["edge"],
                    "expectedValue": metrics["edge"],
                    "expectedGoals": profile["expectedGoals"],
                    "dataQuality": metrics["dataQuality"],
                    "sourceStatus": source_status,
                    "resultStatus": "pending",
                    "reasons": football_reasons(match, play_type, selection, odds, metrics, profile),
                }
            )
    sorted_rows = sorted(
        rows,
        key=lambda item: (item["kickoffTime"], item["confidence"], item["playType"]),
        reverse=True,
    )
    selected: list[dict[str, Any]] = []
    for play_type in play_types:
        item = next((row for row in sorted_rows if row["playType"] == play_type), None)
        if item and item not in selected:
            selected.append(item)
    for item in sorted_rows:
        if len(selected) >= limit:
            break
        if item not in selected:
            selected.append(item)
    return selected[:limit]


def select_football_pick(
    match: FootballMatch,
    play_type: str,
    odds: dict[str, float],
    source_status: str = "unknown",
    profile: dict[str, Any] | None = None,
) -> tuple[str, dict[str, float]]:
    raw_probabilities = raw_implied_probabilities(odds)
    fair_probabilities = implied_probabilities(odds)
    model_probabilities = (profile or build_match_model_profile(match))["playProbabilities"].get(play_type, {})
    adjusted = {
        key: fair_probabilities.get(key, 0.0) * 0.42 + model_probabilities.get(key, 0.0) * 0.58
        for key in set(fair_probabilities) | set(model_probabilities)
    }
    total = sum(max(value, 0.0) for value in adjusted.values()) or 1.0
    blended_probabilities = {key: max(value, 0.0) / total for key, value in adjusted.items()}
    eligible = {key: value for key, value in blended_probabilities.items() if key in odds}
    selection, model_probability = max(eligible.items(), key=lambda item: (item[1], -odds.get(item[0], 999.0)))
    selected_odds = odds.get(selection, 0.0)
    fair_probability = fair_probabilities.get(selection, 0.0)
    edge = model_probability * selected_odds - 1.0 if selected_odds else 0.0
    data_quality = football_data_quality(match, source_status)
    confidence = min(1.0, max(0.0, model_probability * 0.72 + max(edge, 0.0) * 0.18 + data_quality * 0.10))
    return selection, {
        "confidence": round(confidence, 4),
        "impliedProbability": round(raw_probabilities.get(selection, 0.0), 4),
        "fairProbability": round(fair_probability, 4),
        "modelProbability": round(model_probability, 4),
        "overround": round(sum(raw_probabilities.values()), 4),
        "edge": round(edge, 4),
        "dataQuality": round(data_quality, 4),
    }


def raw_implied_probabilities(odds: dict[str, float]) -> dict[str, float]:
    return {key: 1.0 / value for key, value in odds.items() if value > 1.0}


def implied_probabilities(odds: dict[str, float]) -> dict[str, float]:
    inverse = raw_implied_probabilities(odds)
    total = sum(inverse.values()) or 1.0
    return {key: value / total for key, value in inverse.items()}


def build_match_model_profile(match: FootballMatch) -> dict[str, Any]:
    expected = estimate_expected_goals(match)
    score_probs = poisson_score_matrix(expected["home"], expected["away"])
    play_probs = {
        "had": normalize_probability_map(outcome_probabilities(score_probs, 0)),
        "hhad": normalize_probability_map(outcome_probabilities(score_probs, match.handicap)),
        "ttg": normalize_probability_map(total_goals_probabilities(score_probs)),
        "crs": normalize_probability_map(correct_score_probabilities(score_probs)),
        "hafu": normalize_probability_map(half_full_probabilities(match, expected)),
    }
    return {
        "modelName": "poisson_v1",
        "expectedGoals": {"home": round(expected["home"], 3), "away": round(expected["away"], 3)},
        "playProbabilities": play_probs,
    }


def estimate_expected_goals(match: FootballMatch) -> dict[str, float]:
    home_strength = min(1.05, max(0.25, match.teamStrength.get("home", 0.5)))
    away_strength = min(1.05, max(0.25, match.teamStrength.get("away", 0.5)))
    strength_gap = home_strength - away_strength
    stage_modifier = 0.88 if any(token in match.phase for token in ["Round", "Quarter", "Semi", "Final", "Third"]) else 1.0
    host_bonus = 0.10 if not match.neutralVenue else 0.0
    base_total = 2.55 * stage_modifier
    home_share = min(0.72, max(0.28, 0.5 + strength_gap * 0.34 + host_bonus))
    home_goals = min(4.2, max(0.25, base_total * home_share))
    away_goals = min(4.2, max(0.25, base_total * (1.0 - home_share)))
    facts = load_match_facts().get(match.matchId, {})
    home_goals *= max(0.55, 1.0 - min(0.45, safe_float(facts.get("homeUnavailableImpact")) or 0.0))
    away_goals *= max(0.55, 1.0 - min(0.45, safe_float(facts.get("awayUnavailableImpact")) or 0.0))
    total_multiplier = safe_float(facts.get("totalGoalsMultiplier")) or 1.0
    home_goals *= min(1.35, max(0.65, total_multiplier))
    away_goals *= min(1.35, max(0.65, total_multiplier))
    return {"home": home_goals, "away": away_goals}


def poisson_score_matrix(home_goals: float, away_goals: float, max_goals: int = 7) -> dict[tuple[int, int], float]:
    rows: dict[tuple[int, int], float] = {}
    for home in range(max_goals + 1):
        for away in range(max_goals + 1):
            rows[(home, away)] = poisson_probability(home_goals, home) * poisson_probability(away_goals, away)
    total = sum(rows.values()) or 1.0
    return {score: value / total for score, value in rows.items()}


def poisson_probability(lmbda: float, goals: int) -> float:
    return math.exp(-lmbda) * (lmbda ** goals) / math.factorial(goals)


def outcome_probabilities(score_probs: dict[tuple[int, int], float], handicap: int) -> dict[str, float]:
    rows = {"H": 0.0, "D": 0.0, "A": 0.0}
    for (home, away), probability in score_probs.items():
        outcome = match_outcome(home + handicap, away)
        rows[outcome] += probability
    return rows


def total_goals_probabilities(score_probs: dict[tuple[int, int], float]) -> dict[str, float]:
    rows = {str(index): 0.0 for index in range(7)} | {"7+": 0.0}
    for (home, away), probability in score_probs.items():
        total = home + away
        rows["7+" if total >= 7 else str(total)] += probability
    return rows


def correct_score_probabilities(score_probs: dict[tuple[int, int], float]) -> dict[str, float]:
    return {f"{home}:{away}": probability for (home, away), probability in score_probs.items()}


def half_full_probabilities(match: FootballMatch, expected: dict[str, float]) -> dict[str, float]:
    half_matrix = poisson_score_matrix(expected["home"] * 0.45, expected["away"] * 0.45, max_goals=5)
    full_matrix = poisson_score_matrix(expected["home"], expected["away"], max_goals=7)
    half_probs = outcome_probabilities(half_matrix, 0)
    full_probs = outcome_probabilities(full_matrix, 0)
    return {half + full: half_probs[half] * full_probs[full] for half in ["H", "D", "A"] for full in ["H", "D", "A"]}


def normalize_probability_map(values: dict[str, float]) -> dict[str, float]:
    total = sum(max(value, 0.0) for value in values.values()) or 1.0
    return {key: round(max(value, 0.0) / total, 6) for key, value in values.items()}


def football_data_quality(match: FootballMatch, source_status: str) -> float:
    source_score = {"official": 0.95, "import": 0.86, "cache": 0.72, "fallback": 0.55}.get(source_status, 0.6)
    pool_score = min(1.0, len(match.pools) / max(1, len(FOOTBALL_PLAY_TYPES)))
    strength_score = 1.0 if match.teamStrength else 0.6
    facts_score = 1.0 if match.matchId in load_match_facts() else 0.65
    return source_score * 0.5 + pool_score * 0.22 + strength_score * 0.14 + facts_score * 0.14


def football_reasons(
    match: FootballMatch,
    play_type: str,
    selection: str,
    odds: dict[str, float],
    metrics: dict[str, float],
    profile: dict[str, Any] | None = None,
) -> list[str]:
    expected = (profile or {}).get("expectedGoals") or {}
    return [
        f"{FOOTBALL_PLAY_TYPES[play_type]}选择 {selection}，去水公平概率约 {metrics.get('fairProbability', 0.0) * 100:.1f}%，模型概率约 {metrics.get('modelProbability', 0.0) * 100:.1f}%。",
        f"Poisson 预期进球：{match.homeTeam} {expected.get('home', 0.0):.2f}，{match.awayTeam} {expected.get('away', 0.0):.2f}。",
        f"赔率 {odds.get(selection, 0.0):.2f}，理论价值 edge {metrics.get('edge', 0.0):+.2%}，数据质量 {metrics.get('dataQuality', 0.0):.2f}。",
        f"{match.homeTeam} 对 {match.awayTeam}，阶段为{match.phase}，开赛时间 {match.kickoffTime}。",
        "这是基于公开赔率/赛程字段的实验性推荐，不包含投注、下单或中奖承诺。",
    ]


def normalize_play_types(play_type: str) -> list[str]:
    if play_type.lower() == "all":
        return list(FOOTBALL_PLAY_TYPES.keys())
    values = [item.strip().lower() for item in play_type.split(",") if item.strip()]
    return [item for item in values if item in FOOTBALL_PLAY_TYPES] or ["had"]


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def load_json_list(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return []
    rows = payload.get("items") if isinstance(payload, dict) else payload
    return [item for item in rows or [] if isinstance(item, dict)]


def save_json_list(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"items": rows}, ensure_ascii=False, indent=2), encoding="utf-8")


def load_football_odds_snapshots() -> list[dict[str, Any]]:
    return load_json_list(FOOTBALL_ODDS_SNAPSHOT_FILE)


def load_football_recommendation_history() -> list[dict[str, Any]]:
    return load_json_list(FOOTBALL_RECOMMENDATION_FILE)


def load_football_results() -> list[dict[str, Any]]:
    merged = {item["matchId"]: item for item in VERIFIED_FOOTBALL_RESULTS}
    merged.update({item["matchId"]: item for item in load_json_list(FOOTBALL_RESULTS_FILE) if item.get("matchId")})
    return sorted(merged.values(), key=lambda item: item["matchId"])


def save_football_odds_snapshots(matches: list[FootballMatch], source_status: str, captured_at: str) -> None:
    existing = load_football_odds_snapshots()
    rows: list[dict[str, Any]] = []
    for match in matches:
        for play_type, odds in match.pools.items():
            rows.append(
                {
                    "capturedAt": captured_at,
                    "matchId": match.matchId,
                    "matchNum": match.matchNum,
                    "kickoffTime": match.kickoffTime,
                    "homeTeam": match.homeTeam,
                    "awayTeam": match.awayTeam,
                    "playType": play_type,
                    "odds": odds,
                    "sourceStatus": source_status,
                    "source": match.source,
                }
            )
    save_json_list(FOOTBALL_ODDS_SNAPSHOT_FILE, (existing + rows)[-5000:])


def save_football_recommendations(recommendations: list[dict[str, Any]]) -> None:
    existing = load_football_recommendation_history()
    save_json_list(FOOTBALL_RECOMMENDATION_FILE, (existing + recommendations)[-3000:])


def parse_football_results_payload(payload: dict[str, Any]) -> list[dict[str, Any]]:
    rows = payload.get("results") or payload.get("items") or []
    results: list[dict[str, Any]] = []
    for item in rows:
        if not isinstance(item, dict):
            continue
        match_id = str(item.get("matchId") or item.get("id") or "").strip()
        full_time = str(item.get("fullTimeScore") or item.get("score") or "").strip()
        if not match_id or not parse_score(full_time):
            continue
        half_time = str(item.get("halfTimeScore") or "").strip()
        results.append(
            {
                "matchId": match_id,
                "fullTimeScore": full_time,
                "halfTimeScore": half_time,
                "source": str(item.get("source") or "manual"),
                "updatedAt": str(item.get("updatedAt") or utc_now()),
            }
        )
    return results


def parse_score(value: str) -> tuple[int, int] | None:
    normalized = value.replace("-", ":").replace("：", ":")
    parts = normalized.split(":")
    if len(parts) != 2:
        return None
    try:
        return int(parts[0]), int(parts[1])
    except ValueError:
        return None


def build_football_model_report(recommendations: list[dict[str, Any]], results: list[dict[str, Any]]) -> dict[str, Any]:
    result_by_match = {item["matchId"]: item for item in results if item.get("matchId")}
    settled: list[dict[str, Any]] = []
    pending = 0
    for recommendation in recommendations:
        result = result_by_match.get(str(recommendation.get("matchId")))
        if not result:
            pending += 1
            continue
        actual = football_actual_selection(
            play_type=str(recommendation.get("playType") or ""),
            result=result,
            handicap=safe_int(recommendation.get("handicap")),
        )
        if not actual:
            pending += 1
            continue
        hit = actual == recommendation.get("selection")
        odds = safe_float(recommendation.get("odds")) or 0.0
        profit = odds - 1.0 if hit else -1.0
        settled.append({**recommendation, "actualSelection": actual, "hit": hit, "profit": profit})
    settled_count = len(settled)
    hit_count = sum(1 for item in settled if item["hit"])
    total_profit = sum(item["profit"] for item in settled)
    return {
        "generatedAt": utc_now(),
        "recommendationCount": len(recommendations),
        "pendingCount": pending,
        "settledCount": settled_count,
        "hitCount": hit_count,
        "hitRate": round(hit_count / settled_count, 4) if settled_count else 0.0,
        "simulatedProfit": round(total_profit, 4),
        "simulatedRoi": round(total_profit / settled_count, 4) if settled_count else 0.0,
        "averageEdge": round(mean([safe_float(item.get("edge")) or 0.0 for item in recommendations]), 4) if recommendations else 0.0,
        "recentSettlements": settled[-20:],
    }


def football_actual_selection(play_type: str, result: dict[str, Any], handicap: int = 0) -> str:
    score = parse_score(str(result.get("fullTimeScore") or ""))
    if not score:
        return ""
    home, away = score
    if play_type == "had":
        return match_outcome(home, away)
    if play_type == "hhad":
        return match_outcome(home + handicap, away)
    if play_type == "ttg":
        total = home + away
        return "7+" if total >= 7 else str(total)
    if play_type == "crs":
        return f"{home}:{away}"
    if play_type == "hafu":
        half = parse_score(str(result.get("halfTimeScore") or ""))
        if not half:
            return ""
        return match_outcome(half[0], half[1]) + match_outcome(home, away)
    return ""


def match_outcome(home: int, away: int) -> str:
    if home > away:
        return "H"
    if home < away:
        return "A"
    return "D"


def infer_team_strength(home: str, away: str) -> dict[str, float]:
    ratings = {
        "Brazil": 0.92,
        "France": 0.91,
        "Argentina": 0.90,
        "England": 0.88,
        "Spain": 0.87,
        "Germany": 0.86,
        "Portugal": 0.85,
        "Netherlands": 0.84,
        "Mexico": 0.76,
        "Canada": 0.70,
        "United States": 0.74,
        "South Africa": 0.62,
        "Morocco": 0.78,
        "Japan": 0.77,
    }
    return {"home": ratings.get(home, 0.68), "away": ratings.get(away, 0.68)}


def safe_float(value: Any) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def safe_int(value: Any) -> int:
    try:
        return int(float(str(value).replace("+", "")))
    except (TypeError, ValueError):
        return 0


def short_error(error: Exception) -> str:
    message = str(error).strip()
    return message[:80] if message else error.__class__.__name__


def analyze(draws: list[SsqDraw], recent_window: int = 500, model_version: str = "recent_focus_v3") -> dict[str, Any]:
    if not draws:
        return {"sampleSize": 0}
    ordered = list(reversed(draws))
    recent = ordered[-recent_window:]
    red_freq = Counter(red for draw in ordered for red in draw.redBalls)
    blue_freq = Counter(draw.blueBall for draw in ordered)
    red_miss = omission(draws, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_miss = omission(draws, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_recent = weighted_frequency(recent, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_recent = weighted_frequency(recent, range(1, 17), lambda draw, number: number == draw.blueBall)
    odd_counts = Counter(sum(number % 2 for number in draw.redBalls) for draw in ordered)
    sums = sorted(sum(draw.redBalls) for draw in ordered)
    zone_counts = Counter(tuple(zone_distribution(draw.redBalls)) for draw in ordered)
    return {
        "sampleSize": len(draws),
        "latestIssue": draws[0].issue,
        "latestDate": draws[0].date,
        "targetIssue": next_issue(draws[0].issue),
        "recentWindow": len(recent),
        "hotReds": rank_numbers(red_freq, red_recent, red_miss, range(1, 34), reverse=True, size=8, model_version=model_version),
        "coldReds": rank_numbers(red_freq, red_recent, red_miss, range(1, 34), reverse=False, size=8, model_version=model_version),
        "overdueReds": top_by_value(red_miss, reverse=True, size=8),
        "hotBlues": rank_numbers(blue_freq, blue_recent, blue_miss, range(1, 17), reverse=True, size=5, model_version=model_version),
        "coldBlues": rank_numbers(blue_freq, blue_recent, blue_miss, range(1, 17), reverse=False, size=5, model_version=model_version),
        "overdueBlues": top_by_value(blue_miss, reverse=True, size=5),
        "averageSum": round(mean(sums), 2),
        "sumRange": [percentile(sums, 0.15), percentile(sums, 0.85)],
        "commonOddCounts": [count for count, _ in odd_counts.most_common(3)],
        "commonZonePatterns": [list(pattern) for pattern, _ in zone_counts.most_common(3)],
    }


def recommend(
    draws: list[SsqDraw],
    single_count: int,
    compound_count: int,
    red_count: int,
    blue_count: int,
    analysis: dict[str, Any],
    model_version: str,
    recent_window: int,
    fusion_weights: dict[str, float] | None = None,
) -> list[dict[str, Any]]:
    ordered = list(reversed(draws))
    recent = ordered[-recent_window:]
    red_freq = Counter(red for draw in ordered for red in draw.redBalls)
    blue_freq = Counter(draw.blueBall for draw in ordered)
    red_miss = omission(draws, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_miss = omission(draws, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_recent = weighted_frequency(recent, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_recent = weighted_frequency(recent, range(1, 17), lambda draw, number: number == draw.blueBall)
    if model_version == ENSEMBLE_MODEL_VERSION:
        weights = fusion_weights or uniform_fusion_weights()
        red_details = fuse_score_breakdowns(red_freq, red_recent, red_miss, range(1, 34), weights)
        blue_details = fuse_score_breakdowns(blue_freq, blue_recent, blue_miss, range(1, 17), weights)
    else:
        red_details = score_breakdown(red_freq, red_recent, red_miss, range(1, 34), model_version)
        blue_details = score_breakdown(blue_freq, blue_recent, blue_miss, range(1, 17), model_version)
    red_scores = {number: detail["total"] for number, detail in red_details.items()}
    blue_scores = {number: detail["total"] for number, detail in blue_details.items()}
    rng = random.Random(stable_seed(f"{analysis['targetIssue']}|{len(draws)}|{red_count}|{blue_count}|{single_count}|{compound_count}"))
    candidates: list[Candidate] = []
    enforce_shape = model_version != UNIFORM_RANDOM_MODEL_VERSION
    candidates.extend(generate_candidates(red_scores, blue_scores, red_details, blue_details, single_count, 6, 1, rng, analysis, "单式", enforce_shape))
    candidates.extend(generate_candidates(red_scores, blue_scores, red_details, blue_details, compound_count, red_count, blue_count, rng, analysis, plan_label(red_count, blue_count), enforce_shape))
    summary = analysis_summary(analysis, model_version)
    return [
        {
            "targetIssue": analysis["targetIssue"],
            "sourceIssue": analysis["latestIssue"],
            "modelVersion": model_version,
            "redBalls": candidate.redBalls,
            "blueBalls": candidate.blueBalls,
            "score": round(candidate.score, 4),
            "analysisSummary": summary,
            "note": candidate.note,
            "reasons": candidate.reasons,
            "ballDetails": candidate.ballDetails,
        }
        for candidate in candidates
    ]


def generate_candidates(
    red_scores: dict[int, float],
    blue_scores: dict[int, float],
    red_details: dict[int, dict[str, Any]],
    blue_details: dict[int, dict[str, Any]],
    count: int,
    red_count: int,
    blue_count: int,
    rng: random.Random,
    analysis: dict[str, Any],
    label: str,
    enforce_shape: bool = True,
) -> list[Candidate]:
    if count <= 0:
        return []
    accepted: list[Candidate] = []
    candidate_limit = count if count == 1 else min(400, count * 80)
    seen: set[tuple[tuple[int, ...], tuple[int, ...]]] = set()
    guard = 0
    while len(accepted) < candidate_limit and guard < max(3000, candidate_limit * 20):
        guard += 1
        reds = sorted(weighted_sample(red_scores, red_count, rng))
        blues = sorted(weighted_sample(blue_scores, blue_count, rng))
        key = (tuple(reds), tuple(blues))
        if key in seen or (enforce_shape and not passes_shape(reds, analysis)):
            continue
        seen.add(key)
        score = sum(red_scores[number] for number in reds) + sum(blue_scores[number] for number in blues)
        bets = math.comb(len(reds), 6) * len(blues)
        strategy_note = "无偏随机覆盖" if not enforce_shape else "选号风格加权"
        note = f"{label}{len(reds)}+{len(blues)}；约{bets}注；{strategy_note}"
        accepted.append(Candidate(reds, blues, score, note, explain_candidate(reds, blues, analysis, not enforce_shape), build_ball_details(reds, blues, red_details, blue_details)))
    return select_diverse_candidates(accepted, count)


def select_diverse_candidates(candidates: list[Candidate], count: int) -> list[Candidate]:
    if count <= 0 or not candidates:
        return []
    remaining = list(candidates)
    selected = [remaining.pop(0)]
    while remaining and len(selected) < count:
        _, candidate = min(
            enumerate(remaining),
            key=lambda item: (candidate_overlap_score(item[1], selected), item[0]),
        )
        selected.append(candidate)
        remaining.remove(candidate)
    return selected


def candidate_overlap_score(candidate: Candidate, selected: list[Candidate]) -> int:
    return sum(
        len(set(candidate.redBalls) & set(other.redBalls)) * 3
        + len(set(candidate.blueBalls) & set(other.blueBalls))
        for other in selected
    )


def passes_shape(reds: list[int], analysis: dict[str, Any]) -> bool:
    odd_count = sum(number % 2 for number in reds)
    total = sum(reds)
    zones = zone_distribution(reds)
    consecutive_pairs = sum(1 for left, right in zip(reds, reds[1:]) if right - left == 1)
    allowed_odd = set(analysis["commonOddCounts"])
    low_sum, high_sum = analysis["sumRange"]
    common_zones = [tuple(pattern) for pattern in analysis["commonZonePatterns"]]
    if len(reds) == 6:
        return odd_count in allowed_odd and low_sum <= total <= high_sum and consecutive_pairs <= 2 and tuple(zones) in common_zones
    return 1 <= min(zones) and consecutive_pairs <= 3


def score_numbers(
    full_frequency: Counter[int],
    recent_frequency: dict[int, float],
    misses: dict[int, int],
    numbers: range,
    model_version: str = "recent_focus_v3",
) -> dict[int, float]:
    if model_version == UNIFORM_RANDOM_MODEL_VERSION:
        return {number: 1.0 for number in numbers}
    weights = MODEL_CONFIGS.get(model_version, MODEL_CONFIGS["recent_focus_v3"])
    max_full = max(full_frequency.values(), default=1)
    max_recent = max(recent_frequency.values(), default=1.0)
    max_miss = max(misses.values(), default=1)
    center = (numbers.start + numbers.stop - 1) / 2
    return {
        number: round(
            weights["full"] * (full_frequency[number] / max_full if max_full else 0.0)
            + weights["recent"] * (recent_frequency[number] / max_recent if max_recent else 0.0)
            + weights["miss"] * (misses[number] / max_miss if max_miss else 0.0)
            + weights["center"] * (1 - abs(number - center) / max(center, 1)),
            6,
        )
        for number in numbers
    }


def score_breakdown(
    full_frequency: Counter[int],
    recent_frequency: dict[int, float],
    misses: dict[int, int],
    numbers: range,
    model_version: str = "recent_focus_v3",
) -> dict[int, dict[str, Any]]:
    if model_version == UNIFORM_RANDOM_MODEL_VERSION:
        return {
            number: {
                "total": 1.0,
                "fullFrequencyScore": 0.0,
                "recentScore": 0.0,
                "omissionScore": 0.0,
                "centerBiasScore": 0.0,
                "fullCount": full_frequency[number],
                "recentWeighted": round(recent_frequency[number], 4),
                "missCount": misses[number],
            }
            for number in numbers
        }
    weights = MODEL_CONFIGS.get(model_version, MODEL_CONFIGS["recent_focus_v3"])
    max_full = max(full_frequency.values(), default=1)
    max_recent = max(recent_frequency.values(), default=1.0)
    max_miss = max(misses.values(), default=1)
    center = (numbers.start + numbers.stop - 1) / 2
    details: dict[int, dict[str, Any]] = {}
    for number in numbers:
        full = full_frequency[number] / max_full if max_full else 0.0
        recent = recent_frequency[number] / max_recent if max_recent else 0.0
        miss = misses[number] / max_miss if max_miss else 0.0
        center_bias = 1 - abs(number - center) / max(center, 1)
        total = (
            weights["full"] * full
            + weights["recent"] * recent
            + weights["miss"] * miss
            + weights["center"] * center_bias
        )
        details[number] = {
            "total": round(total, 6),
            "fullFrequencyScore": round(full, 6),
            "recentScore": round(recent, 6),
            "omissionScore": round(miss, 6),
            "centerBiasScore": round(center_bias, 6),
            "fullCount": full_frequency[number],
            "recentWeighted": round(recent_frequency[number], 4),
            "missCount": misses[number],
        }
    return details


def weighted_frequency(draws: list[SsqDraw], numbers: range, predicate) -> dict[int, float]:
    values = {number: 0.0 for number in numbers}
    for index, draw in enumerate(draws, start=1):
        weight = index / max(len(draws), 1)
        for number in numbers:
            if predicate(draw, number):
                values[number] += weight
    return values


def weighted_sample(scores: dict[int, float], count: int, rng: random.Random) -> list[int]:
    pool = dict(scores)
    selected: list[int] = []
    for _ in range(count):
        total = sum(pool.values())
        cursor = rng.random() * total
        for number, score in pool.items():
            cursor -= score
            if cursor <= 0:
                selected.append(number)
                del pool[number]
                break
    return selected


def omission(draws: list[SsqDraw], numbers: range, predicate) -> dict[int, int]:
    result: dict[int, int] = {}
    for number in numbers:
        result[number] = next((index for index, draw in enumerate(draws) if predicate(draw, number)), len(draws))
    return result


def rank_numbers(
    full_frequency: Counter[int],
    recent_frequency: dict[int, float],
    misses: dict[int, int],
    numbers: range,
    reverse: bool,
    size: int,
    model_version: str = "recent_focus_v3",
) -> list[int]:
    scored = score_numbers(full_frequency, recent_frequency, misses, numbers, model_version)
    return [number for number, _ in sorted(scored.items(), key=lambda item: (-item[1], item[0]) if reverse else (item[1], item[0]))[:size]]


def top_by_value(values: dict[int, int], reverse: bool, size: int) -> list[int]:
    return [number for number, _ in sorted(values.items(), key=lambda item: (-item[1], item[0]) if reverse else (item[1], item[0]))[:size]]


def plan_label(red_count: int, blue_count: int) -> str:
    return "单式" if red_count == 6 and blue_count == 1 else "复式"


def zone_distribution(reds: list[int]) -> list[int]:
    return [
        sum(1 for number in reds if 1 <= number <= 11),
        sum(1 for number in reds if 12 <= number <= 22),
        sum(1 for number in reds if 23 <= number <= 33),
    ]


def percentile(values: list[int], ratio: float) -> int:
    if not values:
        return 0
    index = min(len(values) - 1, max(0, round((len(values) - 1) * ratio)))
    return values[index]


def stable_seed(text: str) -> int:
    value = 1125899907
    for char in text:
        value = (value * 31 + ord(char)) & 0xFFFFFFFF
    return value


def analysis_summary(analysis: dict[str, Any], model_version: str = "recent_focus_v3") -> str:
    if model_version == UNIFORM_RANDOM_MODEL_VERSION:
        return f"样本{analysis['sampleSize']}期；当前采用无偏随机覆盖，不以冷热、遗漏或中段作为预测依据。"
    prefix = "多模型融合；" if model_version == ENSEMBLE_MODEL_VERSION else ""
    return (
        f"{prefix}样本{analysis['sampleSize']}期；最近窗口{analysis['recentWindow']}期；"
        f"红球热号{' '.join(map(str, analysis['hotReds'][:6]))}；"
        f"红球遗漏{' '.join(map(str, analysis['overdueReds'][:6]))}；"
        f"蓝球热号{' '.join(map(str, analysis['hotBlues'][:3]))}"
    )


def explain_candidate(reds: list[int], blues: list[int], analysis: dict[str, Any], uniform_coverage: bool = False) -> list[str]:
    if uniform_coverage:
        return [
            "自动策略采用无偏随机覆盖，不把冷热、遗漏或号码位置当作预测因子。",
            f"本组红球为 {format_numbers(reds)}，蓝球为 {format_numbers(blues)}。",
        ]
    hot_reds = sorted(set(reds) & set(analysis["hotReds"]))
    overdue_reds = sorted(set(reds) & set(analysis["overdueReds"]))
    hot_blues = sorted(set(blues) & set(analysis["hotBlues"]))
    odd_count = sum(number % 2 for number in reds)
    total = sum(reds)
    zones = zone_distribution(reds)
    reasons = [
        f"红球命中热号 {format_numbers(hot_reds)}，兼顾近期强势。",
        f"红球纳入遗漏较久号码 {format_numbers(overdue_reds)}，保留回补因子。",
        f"形态为奇偶 {odd_count}:{len(reds) - odd_count}，三区 {zones[0]}-{zones[1]}-{zones[2]}，和值 {total}。",
        f"蓝球选择 {format_numbers(blues)}，其中热号 {format_numbers(hot_blues)}。",
    ]
    return reasons


def build_ball_details(
    reds: list[int],
    blues: list[int],
    red_details: dict[int, dict[str, Any]],
    blue_details: dict[int, dict[str, Any]],
) -> list[dict[str, Any]]:
    details: list[dict[str, Any]] = []
    for color, numbers, source in (("red", reds, red_details), ("blue", blues, blue_details)):
        for number in numbers:
            item = source[number]
            reasons = [
                f"全量出现 {item['fullCount']} 次，频率分 {item['fullFrequencyScore']:.2f}",
                f"近期加权值 {item['recentWeighted']:.2f}，近期分 {item['recentScore']:.2f}",
                f"当前遗漏 {item['missCount']} 期，遗漏分 {item['omissionScore']:.2f}",
            ]
            if item["centerBiasScore"] >= 0.8:
                reasons.append(f"位置接近中段，形态分 {item['centerBiasScore']:.2f}")
            details.append(
                {
                    "color": color,
                    "number": number,
                    "totalScore": item["total"],
                    "fullFrequencyScore": item["fullFrequencyScore"],
                    "recentScore": item["recentScore"],
                    "omissionScore": item["omissionScore"],
                    "centerBiasScore": item["centerBiasScore"],
                    "fullCount": item["fullCount"],
                    "recentWeighted": item["recentWeighted"],
                    "missCount": item["missCount"],
                    "reasons": reasons,
                }
            )
    return details


def build_number_rankings(draws: list[SsqDraw], model_version: str, recent_window: int) -> dict[str, list[dict[str, Any]]]:
    ordered = list(reversed(draws))
    recent = ordered[-recent_window:]
    red_freq = Counter(red for draw in ordered for red in draw.redBalls)
    blue_freq = Counter(draw.blueBall for draw in ordered)
    red_miss = omission(draws, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_miss = omission(draws, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_recent = weighted_frequency(recent, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_recent = weighted_frequency(recent, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_details = score_breakdown(red_freq, red_recent, red_miss, range(1, 34), model_version)
    blue_details = score_breakdown(blue_freq, blue_recent, blue_miss, range(1, 17), model_version)
    return {
        "reds": ranking_rows(draws, red_details, "red"),
        "blues": ranking_rows(draws, blue_details, "blue"),
    }


def ranking_rows(draws: list[SsqDraw], details: dict[int, dict[str, Any]], color: str) -> list[dict[str, Any]]:
    sorted_items = sorted(details.items(), key=lambda item: (-item[1]["total"], item[0]))
    rows: list[dict[str, Any]] = []
    for rank, (number, detail) in enumerate(sorted_items, start=1):
        predicate = (lambda draw: number in draw.redBalls) if color == "red" else (lambda draw: number == draw.blueBall)
        rows.append(
            {
                "color": color,
                "number": number,
                "rank": rank,
                "totalScore": detail["total"],
                "fullFrequencyScore": detail["fullFrequencyScore"],
                "recentScore": detail["recentScore"],
                "omissionScore": detail["omissionScore"],
                "centerBiasScore": detail["centerBiasScore"],
                "fullCount": detail["fullCount"],
                "recentWeighted": detail["recentWeighted"],
                "missCount": detail["missCount"],
                "recent30Count": sum(1 for draw in draws[:30] if predicate(draw)),
                "recent60Count": sum(1 for draw in draws[:60] if predicate(draw)),
                "recent120Count": sum(1 for draw in draws[:120] if predicate(draw)),
                "latestAppearances": [draw.issue for draw in draws if predicate(draw)][:5],
                "summary": ranking_summary(rank, detail["recentScore"], detail["omissionScore"]),
            }
        )
    return rows


def ranking_summary(rank: int, recent_score: float, omission_score: float) -> str:
    if rank <= 6:
        return "处于当前模型核心候选区。"
    if recent_score >= 0.7:
        return "近期表现较强，但综合排名未进入最前列。"
    if omission_score >= 0.7:
        return "遗漏因子较高，但其他维度暂未同步。"
    return "当前综合评分靠后，暂未成为优先候选。"


def compare_models(
    draws: list[SsqDraw],
    recent_window: int,
    sample_limit: int = 60,
    single_count: int = 0,
    compound_count: int = 1,
    red_count: int = 6,
    blue_count: int = 3,
) -> list[dict[str, Any]]:
    return [
        backtest_model(
            draws,
            version,
            recent_window,
            sample_limit,
            single_count=single_count,
            compound_count=compound_count,
            red_count=red_count,
            blue_count=blue_count,
        )
        for version in EVALUATION_MODEL_VERSIONS
    ]


def backtest_model(
    draws: list[SsqDraw],
    model_version: str,
    recent_window: int,
    sample_limit: int,
    single_count: int = 0,
    compound_count: int = 1,
    red_count: int = 6,
    blue_count: int = 3,
) -> dict[str, Any]:
    ordered = list(reversed(draws))
    start = max(30, len(ordered) - sample_limit)
    best_red_hits: list[int] = []
    blue_hits = 0
    at_least_three = 0
    prize_hits = 0
    bet_counts: list[int] = []
    distinct_blue_counts: list[int] = []
    invested_amount = 0.0
    simulated_prize_amount = 0.0
    tier_counts = {"first": 0, "second": 0, "third": 0, "fourth": 0, "fifth": 0, "sixth": 0}
    issue_count = 0
    for index in range(start, len(ordered)):
        train_draws = list(reversed(ordered[:index]))
        actual = ordered[index]
        if len(train_draws) < 30:
            continue
        analysis = analyze(train_draws, recent_window, model_version)
        predictions = recommend(
            train_draws,
            single_count,
            compound_count,
            red_count,
            blue_count,
            analysis,
            model_version,
            recent_window,
        )
        if not predictions:
            continue
        red_hit = max(len(set(item["redBalls"]) & set(actual.redBalls)) for item in predictions)
        blue_hit = any(actual.blueBall in item["blueBalls"] for item in predictions)
        settled = [settle_server_prediction(item, actual) for item in predictions]
        settled = [item for item in settled if item]
        best_red_hits.append(red_hit)
        blue_hits += int(blue_hit)
        at_least_three += int(red_hit >= 3)
        prize_hits += int(any(item["prizeAmount"] > 0 for item in settled))
        bet_counts.append(sum(item["betCount"] for item in settled))
        distinct_blue_counts.append(len({blue for item in predictions for blue in item["blueBalls"]}))
        invested_amount += sum(item["betCount"] * 2.0 for item in settled)
        simulated_prize_amount += sum(item["prizeAmount"] for item in settled)
        for item in settled:
            for tier, count in item["tierCounts"].items():
                tier_counts[tier] = tier_counts.get(tier, 0) + count
        issue_count += 1
    return {
        "version": model_version,
        "issueCount": issue_count,
        "averageBestRedHits": round(sum(best_red_hits) / issue_count, 4) if issue_count else 0.0,
        "blueHitRate": round(blue_hits / issue_count, 4) if issue_count else 0.0,
        "atLeastThreeRedRate": round(at_least_three / issue_count, 4) if issue_count else 0.0,
        "prizeHitRate": round(prize_hits / issue_count, 4) if issue_count else 0.0,
        "averageBetCount": round(sum(bet_counts) / issue_count, 4) if issue_count else 0.0,
        "averageDistinctBlueCount": round(sum(distinct_blue_counts) / issue_count, 4) if issue_count else 0.0,
        "investedAmount": round(invested_amount, 2),
        "simulatedPrizeAmount": round(simulated_prize_amount, 2),
        "roi": round((simulated_prize_amount - invested_amount) / invested_amount, 4) if invested_amount else 0.0,
        "tierCounts": tier_counts,
    }


def build_ssq_model_evaluation(
    draws: list[SsqDraw],
    recent_window: int = 500,
    sample_limit: int = SSQ_EVALUATION_SAMPLE_LIMIT,
    fold_count: int = SSQ_EVALUATION_FOLD_COUNT,
    single_count: int = 0,
    compound_count: int = 1,
    red_count: int = 6,
    blue_count: int = 3,
) -> dict[str, Any]:
    fold_reports: dict[str, list[dict[str, Any]]] = {version: [] for version in EVALUATION_MODEL_VERSIONS}
    for fold_index in range(fold_count):
        subset = draws[fold_index * sample_limit:]
        if len(subset) < 30 + sample_limit:
            break
        for version in EVALUATION_MODEL_VERSIONS:
            fold_reports[version].append(
                backtest_model(
                    subset,
                    version,
                    recent_window,
                    sample_limit,
                    single_count=single_count,
                    compound_count=compound_count,
                    red_count=red_count,
                    blue_count=blue_count,
                )
            )
    reports = [aggregate_model_evaluation(version, reports) for version, reports in fold_reports.items() if reports]
    recommended, selection_reason = select_ssq_model(reports)
    return {
        "evaluationVersion": SSQ_EVALUATION_VERSION,
        "latestIssue": draws[0].issue if draws else "",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "recentWindow": recent_window,
        "sampleLimit": sample_limit,
        "foldCount": max((len(items) for items in fold_reports.values()), default=0),
        "singleCount": single_count,
        "compoundCount": compound_count,
        "redCount": red_count,
        "blueCount": blue_count,
        "modelReports": reports,
        "recommendedModelVersion": recommended,
        "selectionReason": selection_reason,
    }


def aggregate_model_evaluation(version: str, reports: list[dict[str, Any]]) -> dict[str, Any]:
    issue_count = sum(item["issueCount"] for item in reports)
    invested = sum(item["investedAmount"] for item in reports)
    prize = sum(item["simulatedPrizeAmount"] for item in reports)
    tiers = {"first": 0, "second": 0, "third": 0, "fourth": 0, "fifth": 0, "sixth": 0}
    for report in reports:
        for tier, count in report["tierCounts"].items():
            tiers[tier] = tiers.get(tier, 0) + count
    fold_scores = [ssq_fold_selection_score(report) for report in reports]
    score_mean = mean(fold_scores) if fold_scores else 0.0
    score_stddev = pstdev(fold_scores) if len(fold_scores) > 1 else 0.0
    selection_score = min(100.0, max(0.0, score_mean - 0.25 * score_stddev))
    return {
        "version": version,
        "issueCount": issue_count,
        "averageBestRedHits": round(sum(item["averageBestRedHits"] * item["issueCount"] for item in reports) / issue_count, 4) if issue_count else 0.0,
        "blueHitRate": round(sum(item["blueHitRate"] * item["issueCount"] for item in reports) / issue_count, 4) if issue_count else 0.0,
        "atLeastThreeRedRate": round(sum(item["atLeastThreeRedRate"] * item["issueCount"] for item in reports) / issue_count, 4) if issue_count else 0.0,
        "prizeHitRate": round(sum(item["prizeHitRate"] * item["issueCount"] for item in reports) / issue_count, 4) if issue_count else 0.0,
        "averageBetCount": round(sum(item["averageBetCount"] * item["issueCount"] for item in reports) / issue_count, 4) if issue_count else 0.0,
        "averageDistinctBlueCount": round(sum(item["averageDistinctBlueCount"] * item["issueCount"] for item in reports) / issue_count, 4) if issue_count else 0.0,
        "investedAmount": round(invested, 2),
        "simulatedPrizeAmount": round(prize, 2),
        "roi": round((prize - invested) / invested, 4) if invested else 0.0,
        "tierCounts": tiers,
        "foldReports": reports,
        "foldSelectionScores": [round(score, 2) for score in fold_scores],
        "selectionScoreMean": round(score_mean, 2),
        "selectionScoreStdDev": round(score_stddev, 2),
        "selectionScore": round(selection_score, 2),
    }


def ssq_fold_selection_score(report: dict[str, Any]) -> float:
    red_coverage = min(1.0, max(0.0, float(report.get("averageBestRedHits", 0.0)) / 6.0))
    blue_hit_rate = min(1.0, max(0.0, float(report.get("blueHitRate", 0.0))))
    prize_hit_rate = min(1.0, max(0.0, float(report.get("prizeHitRate", 0.0))))
    three_red_rate = min(1.0, max(0.0, float(report.get("atLeastThreeRedRate", 0.0))))
    return 100.0 * (
        0.40 * red_coverage
        + 0.25 * blue_hit_rate
        + 0.25 * prize_hit_rate
        + 0.10 * three_red_rate
    )


def select_ssq_model(reports: list[dict[str, Any]]) -> tuple[str, str]:
    model_order = {version: index for index, version in enumerate(MODEL_CONFIGS)}
    candidates = [report for report in reports if report.get("version") in MODEL_CONFIGS]
    if not candidates:
        return "recent_focus_v3", "没有可用的正式模型评估结果，已回退 recent_focus_v3。"
    selected = max(
        candidates,
        key=lambda item: (
            float(item.get("selectionScore", 0.0)),
            float(item.get("selectionScoreMean", 0.0)),
            -model_order[str(item["version"])],
        ),
    )
    version = str(selected["version"])
    score = float(selected.get("selectionScore", 0.0))
    return version, f"{version} 的统一综合分最高（{score:.2f}），自动模式已按稳定性惩罚后的历史验证结果选择。"


def load_ssq_model_evaluation() -> dict[str, Any] | None:
    if not SSQ_MODEL_EVALUATION_FILE.exists():
        return None
    try:
        value = json.loads(SSQ_MODEL_EVALUATION_FILE.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None
    return value if isinstance(value, dict) else None


def save_ssq_model_evaluation(report: dict[str, Any]) -> dict[str, Any]:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    SSQ_MODEL_EVALUATION_FILE.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


def ensure_ssq_model_evaluation(
    draws: list[SsqDraw],
    recent_window: int = 500,
    sample_limit: int = SSQ_EVALUATION_SAMPLE_LIMIT,
    fold_count: int = SSQ_EVALUATION_FOLD_COUNT,
    single_count: int = 0,
    compound_count: int = 1,
    red_count: int = 6,
    blue_count: int = 3,
) -> dict[str, Any]:
    cached = load_ssq_model_evaluation()
    expected = {
        "evaluationVersion": SSQ_EVALUATION_VERSION,
        "latestIssue": draws[0].issue if draws else "",
        "recentWindow": recent_window,
        "sampleLimit": sample_limit,
        "foldCount": fold_count,
        "singleCount": single_count,
        "compoundCount": compound_count,
        "redCount": red_count,
        "blueCount": blue_count,
    }
    if cached and all(cached.get(key) == value for key, value in expected.items()):
        return cached
    report = build_ssq_model_evaluation(
        draws,
        recent_window=recent_window,
        sample_limit=sample_limit,
        fold_count=fold_count,
        single_count=single_count,
        compound_count=compound_count,
        red_count=red_count,
        blue_count=blue_count,
    )
    return save_ssq_model_evaluation(report)


def ssq_evaluation_cache_matches(cached: dict[str, Any] | None, draws: list[SsqDraw], recent_window: int, single_count: int, compound_count: int, red_count: int, blue_count: int) -> bool:
    if not cached:
        return False
    expected = {
        "evaluationVersion": SSQ_EVALUATION_VERSION,
        "latestIssue": draws[0].issue if draws else "",
        "recentWindow": recent_window,
        "sampleLimit": SSQ_EVALUATION_SAMPLE_LIMIT,
        "foldCount": SSQ_EVALUATION_FOLD_COUNT,
        "singleCount": single_count,
        "compoundCount": compound_count,
        "redCount": red_count,
        "blueCount": blue_count,
    }
    return all(cached.get(key) == value for key, value in expected.items())


def run_ssq_model_evaluation_async(draws: list[SsqDraw], recent_window: int = 500, single_count: int = 0, compound_count: int = 1, red_count: int = 6, blue_count: int = 3) -> None:
    """后台异步执行模型评估，结果写入缓存文件，供后续推荐请求直接使用。"""
    global _ssq_evaluation_running, _ssq_evaluation_result
    with _ssq_evaluation_lock:
        if _ssq_evaluation_running:
            return
        cached = load_ssq_model_evaluation()
        if ssq_evaluation_cache_matches(cached, draws, recent_window, single_count, compound_count, red_count, blue_count):
            _ssq_evaluation_result = cached
            _ssq_evaluation_done.set()
            return
        _ssq_evaluation_running = True
        _ssq_evaluation_done.clear()
    try:
        _ssq_evaluation_result = ensure_ssq_model_evaluation(
            draws,
            recent_window=recent_window,
            single_count=single_count,
            compound_count=compound_count,
            red_count=red_count,
            blue_count=blue_count,
        )
    finally:
        with _ssq_evaluation_lock:
            _ssq_evaluation_running = False
        _ssq_evaluation_done.set()


def get_or_wait_ssq_model_evaluation(draws: list[SsqDraw], recent_window: int, single_count: int, compound_count: int, red_count: int, blue_count: int, timeout: float = 90.0) -> dict[str, Any]:
    """推荐路由专用：缓存命中立即返回；后台评估进行中则等待完成；否则前台同步评估（兜底）。"""
    global _ssq_evaluation_running
    cached = load_ssq_model_evaluation()
    if ssq_evaluation_cache_matches(cached, draws, recent_window, single_count, compound_count, red_count, blue_count):
        return cached
    with _ssq_evaluation_lock:
        running = _ssq_evaluation_running
    if running:
        _ssq_evaluation_done.wait(timeout=timeout)
        result = _ssq_evaluation_result
        if result and ssq_evaluation_cache_matches(result, draws, recent_window, single_count, compound_count, red_count, blue_count):
            return result
    return ensure_ssq_model_evaluation(
        draws,
        recent_window=recent_window,
        single_count=single_count,
        compound_count=compound_count,
        red_count=red_count,
        blue_count=blue_count,
    )


def compute_fusion_weights(model_reports: list[dict[str, Any]]) -> dict[str, float]:
    """按各模型 selectionScore 比例归一化，得到融合权重（和为 1）。"""
    scores = {
        str(report["version"]): max(0.0, float(report.get("selectionScore", 0.0)))
        for report in model_reports
        if str(report.get("version")) in MODEL_CONFIGS
    }
    if not scores:
        return uniform_fusion_weights()
    total = sum(scores.values())
    if total <= 0:
        return uniform_fusion_weights()
    return {version: score / total for version, score in scores.items()}


def uniform_fusion_weights() -> dict[str, float]:
    weight = 1.0 / len(MODEL_CONFIGS)
    return {version: weight for version in MODEL_CONFIGS}


def fuse_score_breakdowns(full_frequency: Counter[int], recent_frequency: dict[int, float], misses: dict[int, int], numbers: range, fusion_weights: dict[str, float]) -> dict[int, dict[str, Any]]:
    """按融合权重对 4 个模型的单球评分做加权平均，得到统一分数。"""
    per_model = {
        version: score_breakdown(full_frequency, recent_frequency, misses, numbers, version)
        for version in MODEL_CONFIGS
    }
    score_keys = ("total", "fullFrequencyScore", "recentScore", "omissionScore", "centerBiasScore")
    fused: dict[int, dict[str, Any]] = {}
    for number in numbers:
        detail = per_model["recent_focus_v3"][number]
        merged = {
            key: round(
                sum(fusion_weights[version] * per_model[version][number][key] for version in MODEL_CONFIGS),
                6,
            )
            for key in score_keys
        }
        merged["fullCount"] = detail["fullCount"]
        merged["recentWeighted"] = detail["recentWeighted"]
        merged["missCount"] = detail["missCount"]
        fused[number] = merged
    return fused


def compute_fused_selection_score(model_reports: list[dict[str, Any]], fusion_weights: dict[str, float]) -> float:
    """多模型综合分 = 各模型 selectionScore 按融合权重加权平均。"""
    score_map = {
        str(report["version"]): float(report.get("selectionScore", 0.0))
        for report in model_reports
        if str(report.get("version")) in MODEL_CONFIGS
    }
    if not score_map:
        return 0.0
    return sum(fusion_weights.get(version, 0.0) * score for version, score in score_map.items())


def build_narrative(analysis: dict[str, Any], model_version: str) -> list[str]:
    first = (
        f"当前使用多模型融合（{model_version}），已按 4 个模型的历史回测得分加权统一出号，最近窗口 {analysis['recentWindow']} 期。"
        if model_version == ENSEMBLE_MODEL_VERSION
        else f"当前使用 {model_version}，最近窗口 {analysis['recentWindow']} 期，目标是兼顾长期稳定性与近期变化。"
    )
    return [
        first,
        f"红球热号集中在 {format_numbers(analysis['hotReds'][:6])}，遗漏较久的是 {format_numbers(analysis['overdueReds'][:6])}。",
        f"历史常见奇数个数为 {'/'.join(map(str, analysis['commonOddCounts']))}，和值主要落在 {analysis['sumRange'][0]}-{analysis['sumRange'][1]}。",
    ]


def format_numbers(numbers: list[int]) -> str:
    return " ".join(f"{number:02d}" for number in numbers) if numbers else "无"


def next_issue(issue: str) -> str:
    try:
        return str(int(issue) + 1).zfill(len(issue))
    except ValueError:
        return "next"
