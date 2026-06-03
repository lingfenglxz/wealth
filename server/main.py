from __future__ import annotations

import json
import math
import random
from collections import Counter
from dataclasses import asdict, dataclass
from datetime import date
from pathlib import Path
from statistics import mean
from typing import Any

import httpx
from fastapi import Body, FastAPI, Query


app = FastAPI(title="WealthLab Light Server", version="0.2.0")

DATA_DIR = Path(__file__).resolve().parent / "data"
CACHE_FILE = DATA_DIR / "ssq_draws.json"
FOOTBALL_CACHE_FILE = DATA_DIR / "football_matches.json"
WORLDCUP_FALLBACK_FILE = DATA_DIR / "worldcup_2026_matches.json"
CWL_URL = "https://www.cwl.gov.cn/cwl_admin/front/cwlkj/search/kjxx/findDrawNotice"
SPORTTERY_FOOTBALL_URL = "https://webapi.sporttery.cn/gateway/jc/football/getMatchCalculatorV1.qry"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36",
    "Referer": "https://www.cwl.gov.cn/",
    "Accept": "application/json,text/plain,*/*",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "X-Requested-With": "XMLHttpRequest",
}

MODEL_CONFIGS = {
    "baseline_v1": {"full": 0.55, "recent": 0.0, "miss": 0.35, "center": 0.10},
    "balanced_v2": {"full": 0.34, "recent": 0.36, "miss": 0.22, "center": 0.08},
    "recent_focus_v3": {"full": 0.20, "recent": 0.50, "miss": 0.22, "center": 0.08},
}


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
    source: str
    updatedAt: str


@app.get("/api/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/api/lottery/ssq/draws")
async def ssq_draws(
    limit: int = Query(default=3000, ge=1, le=5000),
    refresh: bool = Query(default=False),
) -> dict[str, Any]:
    draws = await get_draws(limit, refresh)
    return {
        "source": "cwl",
        "count": len(draws),
        "draws": [asdict(draw) for draw in draws],
        "analysis": analyze(draws),
    }


@app.get("/api/lottery/ssq/recommendations")
async def ssq_recommendations(
    singleCount: int = Query(default=3, ge=0, le=10),
    compoundCount: int = Query(default=1, ge=0, le=5),
    redCount: int = Query(default=9, ge=6, le=20),
    blueCount: int = Query(default=3, ge=1, le=16),
    modelVersion: str = Query(default="balanced_v2"),
    recentWindow: int = Query(default=120, ge=30, le=500),
    budgetBets: int = Query(default=252, ge=1, le=5000),
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
    model_version = modelVersion if modelVersion in MODEL_CONFIGS else "balanced_v2"
    analysis = analyze(draws, recentWindow, model_version)
    predictions = recommend(
        draws=draws,
        single_count=singleCount,
        compound_count=compoundCount,
        red_count=redCount,
        blue_count=blueCount,
        analysis=analysis,
        model_version=model_version,
        recent_window=recentWindow,
    )
    comparison = compare_models(draws, recentWindow)
    selected_backtest = next((item for item in comparison if item["version"] == model_version), None)
    number_rankings = build_number_rankings(draws, model_version, recentWindow)
    return {
        "source": "cwl",
        "count": len(draws),
        "draws": [asdict(draw) for draw in draws],
        "analysis": analysis,
        "modelVersion": model_version,
        "recentWindow": recentWindow,
        "predictions": predictions,
        "backtest": selected_backtest,
        "modelComparison": comparison,
        "budgetPlan": optimize_budget_plan(draws, analysis, model_version, recentWindow, budgetBets),
        "narrative": build_narrative(analysis, model_version),
        "numberRankings": number_rankings,
    }


@app.get("/api/lottery/sports/football/matches")
async def football_matches(
    refresh: bool = Query(default=False),
    limit: int = Query(default=104, ge=1, le=500),
) -> dict[str, Any]:
    matches, source_status, source_message = await get_football_matches(refresh)
    return {
        "sourceStatus": source_status,
        "sourceMessage": source_message,
        "count": min(len(matches), limit),
        "matches": [asdict(match) for match in matches[:limit]],
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
    recommendations = build_football_recommendations(matches, requested, limit)
    return {
        "sourceStatus": source_status,
        "sourceMessage": source_message,
        "count": len(recommendations),
        "recommendations": recommendations,
        "supportedPlayTypes": FOOTBALL_PLAY_TYPES,
    }


async def get_draws(limit: int, refresh: bool) -> list[SsqDraw]:
    cached = load_cache()
    if cached and not refresh:
        return cached[:limit]
    draws = await refresh_ssq_draws(cached, limit) if cached else await fetch_ssq_draws(limit)
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


FOOTBALL_PLAY_TYPES = {
    "had": "胜平负",
    "hhad": "让球胜平负",
    "crs": "比分",
    "ttg": "总进球",
    "hafu": "半全场",
}


async def get_football_matches(refresh: bool) -> tuple[list[FootballMatch], str, str]:
    if refresh:
        try:
            official = await fetch_sporttery_football_matches()
            if official:
                save_football_cache(official)
                return official, "official", "官方竞彩数据更新成功"
        except Exception as error:
            cached = load_football_cache()
            if cached:
                return cached, "cache", f"官方竞彩抓取失败，使用缓存：{short_error(error)}"
            fallback = load_worldcup_fallback()
            return fallback, "fallback", f"官方竞彩抓取失败，使用内置世界杯赛程：{short_error(error)}"
    cached = load_football_cache()
    if cached:
        return cached, "cache", "使用本地足球赛事缓存"
    fallback = load_worldcup_fallback()
    return fallback, "fallback", "使用内置世界杯赛程"


async def fetch_sporttery_football_matches() -> list[FootballMatch]:
    params = {"poolCode": "hhad,had,crs,ttg,hafu", "channel": "c"}
    async with httpx.AsyncClient(timeout=12.0, headers=HEADERS, follow_redirects=True) as client:
        response = await client.get(SPORTTERY_FOOTBALL_URL, params=params)
        response.raise_for_status()
        payload = response.json()
    return parse_sporttery_matches(payload)


def load_football_cache() -> list[FootballMatch]:
    return read_football_file(FOOTBALL_CACHE_FILE, "cache")


def load_worldcup_fallback() -> list[FootballMatch]:
    return read_football_file(WORLDCUP_FALLBACK_FILE, "fallback")


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


def parse_football_payload(payload: dict[str, Any], source: str) -> list[FootballMatch]:
    rows = payload.get("matches") or payload.get("value", {}).get("matches") or []
    matches = [normalize_football_match(item, source) for item in rows if isinstance(item, dict)]
    return sorted((match for match in matches if match), key=lambda item: item.kickoffTime)


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
    if not match_id or not home or not away or not kickoff or not pools:
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


def build_football_recommendations(matches: list[FootballMatch], play_types: list[str], limit: int) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for match in matches:
        for play_type in play_types:
            odds = match.pools.get(play_type)
            if not odds:
                continue
            selection, confidence = select_football_pick(match, play_type, odds)
            rows.append(
                {
                    "matchId": match.matchId,
                    "matchNum": match.matchNum,
                    "leagueName": match.leagueName,
                    "phase": match.phase,
                    "kickoffTime": match.kickoffTime,
                    "homeTeam": match.homeTeam,
                    "awayTeam": match.awayTeam,
                    "playType": play_type,
                    "playName": FOOTBALL_PLAY_TYPES[play_type],
                    "selection": selection,
                    "odds": odds.get(selection),
                    "confidence": round(confidence, 4),
                    "reasons": football_reasons(match, play_type, selection, odds),
                }
            )
    sorted_rows = sorted(rows, key=lambda item: (-item["confidence"], item["kickoffTime"], item["playType"]))
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


def select_football_pick(match: FootballMatch, play_type: str, odds: dict[str, float]) -> tuple[str, float]:
    probabilities = implied_probabilities(odds)
    strength_bias = match.teamStrength.get("home", 0.5) - match.teamStrength.get("away", 0.5)
    adjusted = dict(probabilities)
    if play_type in {"had", "hhad"}:
        adjusted["H"] = adjusted.get("H", 0.0) + max(strength_bias, 0.0) * 0.12
        adjusted["A"] = adjusted.get("A", 0.0) + max(-strength_bias, 0.0) * 0.12
        adjusted["D"] = adjusted.get("D", 0.0) + (0.03 if abs(strength_bias) < 0.08 else 0.0)
        if play_type == "hhad" and match.handicap:
            adjusted["H"] = adjusted.get("H", 0.0) - max(match.handicap, 0) * 0.05
            adjusted["A"] = adjusted.get("A", 0.0) + max(match.handicap, 0) * 0.05
    elif play_type == "ttg":
        adjusted = {key: value + (0.02 if key in {"2", "3"} else 0.0) for key, value in adjusted.items()}
    elif play_type == "hafu":
        adjusted = {key: value + (0.02 if key in {"HH", "DD", "AA"} else 0.0) for key, value in adjusted.items()}
    elif play_type == "crs":
        adjusted = {key: value + (0.02 if key in {"1:0", "1:1", "2:1", "0:1"} else 0.0) for key, value in adjusted.items()}
    selection, score = max(adjusted.items(), key=lambda item: (item[1], -odds.get(item[0], 999.0)))
    return selection, min(1.0, max(0.0, score))


def implied_probabilities(odds: dict[str, float]) -> dict[str, float]:
    inverse = {key: 1.0 / value for key, value in odds.items() if value > 1.0}
    total = sum(inverse.values()) or 1.0
    return {key: value / total for key, value in inverse.items()}


def football_reasons(match: FootballMatch, play_type: str, selection: str, odds: dict[str, float]) -> list[str]:
    probabilities = implied_probabilities(odds)
    return [
        f"{FOOTBALL_PLAY_TYPES[play_type]}选择 {selection}，对应隐含概率约 {probabilities.get(selection, 0.0) * 100:.1f}%。",
        f"{match.homeTeam} 对 {match.awayTeam}，阶段为{match.phase}，开赛时间 {match.kickoffTime}。",
        "这是基于公开赔率/赛程字段的实验性推荐，不包含投注、下单或中奖承诺。",
    ]


def normalize_play_types(play_type: str) -> list[str]:
    if play_type.lower() == "all":
        return list(FOOTBALL_PLAY_TYPES.keys())
    values = [item.strip().lower() for item in play_type.split(",") if item.strip()]
    return [item for item in values if item in FOOTBALL_PLAY_TYPES] or ["had"]


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


def analyze(draws: list[SsqDraw], recent_window: int = 120, model_version: str = "balanced_v2") -> dict[str, Any]:
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
) -> list[dict[str, Any]]:
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
    red_scores = {number: detail["total"] for number, detail in red_details.items()}
    blue_scores = {number: detail["total"] for number, detail in blue_details.items()}
    rng = random.Random(stable_seed(f"{analysis['targetIssue']}|{len(draws)}|{red_count}|{blue_count}|{single_count}|{compound_count}"))
    candidates: list[Candidate] = []
    candidates.extend(generate_candidates(red_scores, blue_scores, red_details, blue_details, single_count, 6, 1, rng, analysis, "单式"))
    candidates.extend(generate_candidates(red_scores, blue_scores, red_details, blue_details, compound_count, red_count, blue_count, rng, analysis, "复式"))
    summary = analysis_summary(analysis)
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
) -> list[Candidate]:
    if count <= 0:
        return []
    accepted: list[Candidate] = []
    seen: set[tuple[tuple[int, ...], tuple[int, ...]]] = set()
    guard = 0
    while len(accepted) < count and guard < 3000:
        guard += 1
        reds = sorted(weighted_sample(red_scores, red_count, rng))
        blues = sorted(weighted_sample(blue_scores, blue_count, rng))
        key = (tuple(reds), tuple(blues))
        if key in seen or not passes_shape(reds, analysis):
            continue
        seen.add(key)
        score = sum(red_scores[number] for number in reds) + sum(blue_scores[number] for number in blues)
        bets = math.comb(len(reds), 6) * len(blues)
        note = f"{label}{len(reds)}+{len(blues)}；约{bets}注；最近窗口{analysis['recentWindow']}期"
        accepted.append(Candidate(reds, blues, score, note, explain_candidate(reds, blues, analysis), build_ball_details(reds, blues, red_details, blue_details)))
    return sorted(accepted, key=lambda item: item.score, reverse=True)[:count]


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
    model_version: str = "balanced_v2",
) -> dict[int, float]:
    weights = MODEL_CONFIGS.get(model_version, MODEL_CONFIGS["balanced_v2"])
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
    model_version: str = "balanced_v2",
) -> dict[int, dict[str, Any]]:
    weights = MODEL_CONFIGS.get(model_version, MODEL_CONFIGS["balanced_v2"])
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
    model_version: str = "balanced_v2",
) -> list[int]:
    scored = score_numbers(full_frequency, recent_frequency, misses, numbers, model_version)
    return [number for number, _ in sorted(scored.items(), key=lambda item: (-item[1], item[0]) if reverse else (item[1], item[0]))[:size]]


def top_by_value(values: dict[int, int], reverse: bool, size: int) -> list[int]:
    return [number for number, _ in sorted(values.items(), key=lambda item: (-item[1], item[0]) if reverse else (item[1], item[0]))[:size]]


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


def analysis_summary(analysis: dict[str, Any]) -> str:
    return (
        f"样本{analysis['sampleSize']}期；最近窗口{analysis['recentWindow']}期；"
        f"红球热号{' '.join(map(str, analysis['hotReds'][:6]))}；"
        f"红球遗漏{' '.join(map(str, analysis['overdueReds'][:6]))}；"
        f"蓝球热号{' '.join(map(str, analysis['hotBlues'][:3]))}"
    )


def explain_candidate(reds: list[int], blues: list[int], analysis: dict[str, Any]) -> list[str]:
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


def compare_models(draws: list[SsqDraw], recent_window: int, sample_limit: int = 60) -> list[dict[str, Any]]:
    return [backtest_model(draws, version, recent_window, sample_limit) for version in MODEL_CONFIGS]


def backtest_model(draws: list[SsqDraw], model_version: str, recent_window: int, sample_limit: int) -> dict[str, Any]:
    ordered = list(reversed(draws))
    start = max(30, len(ordered) - sample_limit)
    best_red_hits: list[int] = []
    blue_hits = 0
    at_least_three = 0
    issue_count = 0
    for index in range(start, len(ordered)):
        train_draws = list(reversed(ordered[:index]))
        actual = ordered[index]
        if len(train_draws) < 30:
            continue
        analysis = analyze(train_draws, recent_window, model_version)
        predictions = recommend(train_draws, 3, 0, 9, 3, analysis, model_version, recent_window)
        if not predictions:
            continue
        red_hit = max(len(set(item["redBalls"]) & set(actual.redBalls)) for item in predictions)
        blue_hit = any(actual.blueBall in item["blueBalls"] for item in predictions)
        best_red_hits.append(red_hit)
        blue_hits += int(blue_hit)
        at_least_three += int(red_hit >= 3)
        issue_count += 1
    return {
        "version": model_version,
        "issueCount": issue_count,
        "averageBestRedHits": round(sum(best_red_hits) / issue_count, 4) if issue_count else 0.0,
        "blueHitRate": round(blue_hits / issue_count, 4) if issue_count else 0.0,
        "atLeastThreeRedRate": round(at_least_three / issue_count, 4) if issue_count else 0.0,
    }


def optimize_budget_plan(
    draws: list[SsqDraw],
    analysis: dict[str, Any],
    model_version: str,
    recent_window: int,
    budget_bets: int,
) -> dict[str, Any]:
    ordered = list(reversed(draws))
    recent = ordered[-recent_window:]
    red_freq = Counter(red for draw in ordered for red in draw.redBalls)
    blue_freq = Counter(draw.blueBall for draw in ordered)
    red_miss = omission(draws, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_miss = omission(draws, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_recent = weighted_frequency(recent, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_recent = weighted_frequency(recent, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_scores = score_numbers(red_freq, red_recent, red_miss, range(1, 34), model_version)
    blue_scores = score_numbers(blue_freq, blue_recent, blue_miss, range(1, 17), model_version)
    best: tuple[float, int, int, int] | None = None
    for red_count in range(6, 13):
        for blue_count in range(1, 6):
            bet_count = math.comb(red_count, 6) * blue_count
            if bet_count > budget_bets:
                continue
            red_value = sum(sorted(red_scores.values(), reverse=True)[:red_count])
            blue_value = sum(sorted(blue_scores.values(), reverse=True)[:blue_count])
            coverage_bonus = 0.03 * red_count + 0.05 * blue_count
            utility = red_value + blue_value + coverage_bonus
            candidate = (utility, red_count, blue_count, bet_count)
            if best is None or candidate > best:
                best = candidate
    if best is None:
        best = (0.0, 6, 1, 1)
    _, red_count, blue_count, bet_count = best
    return {
        "budgetBets": budget_bets,
        "redCount": red_count,
        "blueCount": blue_count,
        "betCount": bet_count,
        "reason": f"在不超过 {budget_bets} 注的前提下，{red_count}+{blue_count} 在当前评分体系下取得最高覆盖效用。",
    }


def build_narrative(analysis: dict[str, Any], model_version: str) -> list[str]:
    return [
        f"当前使用 {model_version}，最近窗口 {analysis['recentWindow']} 期，目标是兼顾长期稳定性与近期变化。",
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
