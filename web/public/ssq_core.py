# Generated from server/main.py by scripts/extract_core.py.
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
MODEL_CONFIGS = {'recent_focus_v3': {'full': 0.2, 'recent': 0.5, 'miss': 0.22, 'center': 0.08}, 'hit_rate_v4': {'full': 0.25, 'recent': 0.4, 'miss': 0.28, 'center': 0.07}, 'balanced_v2': {'full': 0.34, 'recent': 0.36, 'miss': 0.22, 'center': 0.08}, 'baseline_v1': {'full': 0.55, 'recent': 0.0, 'miss': 0.35, 'center': 0.1}}
UNIFORM_RANDOM_MODEL_VERSION = 'uniform_random_v0'
ENSEMBLE_MODEL_VERSION = 'ensemble_fusion'
EVALUATION_MODEL_VERSIONS = [*MODEL_CONFIGS, UNIFORM_RANDOM_MODEL_VERSION]
SSQ_EVALUATION_VERSION = 'ssq-evaluation-v4'
SSQ_EVALUATION_SAMPLE_LIMIT = 60
SSQ_EVALUATION_FOLD_COUNT = 6

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

def build_server_settlement(draw: SsqDraw, predictions: list[dict[str, Any]]) -> dict[str, Any] | None:
    details = [settle_server_prediction(prediction, draw) for prediction in predictions]
    details = [detail for detail in details if detail]
    if not details:
        return None
    bet_count = sum((detail['betCount'] for detail in details))
    prize = sum((detail['prizeAmount'] for detail in details))
    invested = bet_count * 2.0
    tiers = {'first': 0, 'second': 0, 'third': 0, 'fourth': 0, 'fifth': 0, 'sixth': 0}
    for detail in details:
        for key, value in detail['tierCounts'].items():
            tiers[key] = tiers.get(key, 0) + value
    return {'issue': draw.issue, 'drawDate': draw.date, 'settledAt': int(date.today().strftime('%Y%m%d')), 'betCount': bet_count, 'investedAmount': invested, 'simulatedPrizeAmount': prize, 'roi': (prize - invested) / invested if invested else 0.0, 'bestRedHits': max((detail['bestRedHits'] for detail in details)), 'blueHit': any((detail['blueHit'] for detail in details)), 'tierCounts': tiers, 'details': details}

def settle_server_prediction(prediction: dict[str, Any], draw: SsqDraw) -> dict[str, Any] | None:
    reds = [int(value) for value in prediction.get('redBalls') or []]
    blues = [int(value) for value in prediction.get('blueBalls') or []]
    if len(reds) < 6 or not blues:
        return None
    red_overlap = len(set(reds) & set(draw.redBalls))
    non_hit_reds = len(reds) - red_overlap
    has_blue = draw.blueBall in blues
    losing_blue_count = len(blues) - (1 if has_blue else 0)
    tiers = {'first': 0, 'second': 0, 'third': 0, 'fourth': 0, 'fifth': 0, 'sixth': 0}
    for red_hits in range(0, 7):
        red_count = combination(red_overlap, red_hits) * combination(non_hit_reds, 6 - red_hits)
        if red_count <= 0:
            continue
        if has_blue:
            add_server_tier(tiers, red_hits, True, red_count)
        if losing_blue_count > 0:
            add_server_tier(tiers, red_hits, False, red_count * losing_blue_count)
    prize = tiers['third'] * 3000.0 + tiers['fourth'] * 200.0 + tiers['fifth'] * 10.0 + tiers['sixth'] * 5.0
    return {'label': '复式' if len(reds) > 6 or len(blues) > 1 else '单式', 'redBalls': sorted(reds), 'blueBalls': sorted(blues), 'betCount': combination(len(reds), 6) * len(blues), 'bestRedHits': min(red_overlap, 6), 'blueHit': has_blue, 'prizeAmount': prize, 'tierCounts': tiers}

def add_server_tier(target: dict[str, int], red_hits: int, blue_hit: bool, count: int) -> None:
    tier = None
    if red_hits == 6 and blue_hit:
        tier = 'first'
    elif red_hits == 6:
        tier = 'second'
    elif red_hits == 5 and blue_hit:
        tier = 'third'
    elif red_hits == 5 or (red_hits == 4 and blue_hit):
        tier = 'fourth'
    elif red_hits == 4 or (red_hits == 3 and blue_hit):
        tier = 'fifth'
    elif blue_hit and 0 <= red_hits <= 2:
        tier = 'sixth'
    if tier:
        target[tier] = target.get(tier, 0) + count

def combination(n: int, k: int) -> int:
    if k < 0 or k > n:
        return 0
    return math.comb(n, k)

def analyze(draws: list[SsqDraw], recent_window: int=500, model_version: str='recent_focus_v3') -> dict[str, Any]:
    if not draws:
        return {'sampleSize': 0}
    ordered = list(reversed(draws))
    recent = ordered[-recent_window:]
    red_freq = Counter((red for draw in ordered for red in draw.redBalls))
    blue_freq = Counter((draw.blueBall for draw in ordered))
    red_miss = omission(draws, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_miss = omission(draws, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_recent = weighted_frequency(recent, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_recent = weighted_frequency(recent, range(1, 17), lambda draw, number: number == draw.blueBall)
    odd_counts = Counter((sum((number % 2 for number in draw.redBalls)) for draw in ordered))
    sums = sorted((sum(draw.redBalls) for draw in ordered))
    zone_counts = Counter((tuple(zone_distribution(draw.redBalls)) for draw in ordered))
    return {'sampleSize': len(draws), 'latestIssue': draws[0].issue, 'latestDate': draws[0].date, 'targetIssue': next_issue(draws[0].issue), 'recentWindow': len(recent), 'hotReds': rank_numbers(red_freq, red_recent, red_miss, range(1, 34), reverse=True, size=8, model_version=model_version), 'coldReds': rank_numbers(red_freq, red_recent, red_miss, range(1, 34), reverse=False, size=8, model_version=model_version), 'overdueReds': top_by_value(red_miss, reverse=True, size=8), 'hotBlues': rank_numbers(blue_freq, blue_recent, blue_miss, range(1, 17), reverse=True, size=5, model_version=model_version), 'coldBlues': rank_numbers(blue_freq, blue_recent, blue_miss, range(1, 17), reverse=False, size=5, model_version=model_version), 'overdueBlues': top_by_value(blue_miss, reverse=True, size=5), 'averageSum': round(mean(sums), 2), 'sumRange': [percentile(sums, 0.15), percentile(sums, 0.85)], 'commonOddCounts': [count for count, _ in odd_counts.most_common(3)], 'commonZonePatterns': [list(pattern) for pattern, _ in zone_counts.most_common(3)]}

def recommend(draws: list[SsqDraw], single_count: int, compound_count: int, red_count: int, blue_count: int, analysis: dict[str, Any], model_version: str, recent_window: int, fusion_weights: dict[str, float] | None=None) -> list[dict[str, Any]]:
    ordered = list(reversed(draws))
    recent = ordered[-recent_window:]
    red_freq = Counter((red for draw in ordered for red in draw.redBalls))
    blue_freq = Counter((draw.blueBall for draw in ordered))
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
    red_scores = {number: detail['total'] for number, detail in red_details.items()}
    blue_scores = {number: detail['total'] for number, detail in blue_details.items()}
    rng = random.Random(stable_seed(f"{analysis['targetIssue']}|{len(draws)}|{red_count}|{blue_count}|{single_count}|{compound_count}"))
    candidates: list[Candidate] = []
    enforce_shape = model_version != UNIFORM_RANDOM_MODEL_VERSION
    candidates.extend(generate_candidates(red_scores, blue_scores, red_details, blue_details, single_count, 6, 1, rng, analysis, '单式', enforce_shape))
    candidates.extend(generate_candidates(red_scores, blue_scores, red_details, blue_details, compound_count, red_count, blue_count, rng, analysis, plan_label(red_count, blue_count), enforce_shape))
    summary = analysis_summary(analysis, model_version)
    return [{'targetIssue': analysis['targetIssue'], 'sourceIssue': analysis['latestIssue'], 'modelVersion': model_version, 'redBalls': candidate.redBalls, 'blueBalls': candidate.blueBalls, 'score': round(candidate.score, 4), 'analysisSummary': summary, 'note': candidate.note, 'reasons': candidate.reasons, 'ballDetails': candidate.ballDetails} for candidate in candidates]

def generate_candidates(red_scores: dict[int, float], blue_scores: dict[int, float], red_details: dict[int, dict[str, Any]], blue_details: dict[int, dict[str, Any]], count: int, red_count: int, blue_count: int, rng: random.Random, analysis: dict[str, Any], label: str, enforce_shape: bool=True) -> list[Candidate]:
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
        if key in seen or (enforce_shape and (not passes_shape(reds, analysis))):
            continue
        seen.add(key)
        score = sum((red_scores[number] for number in reds)) + sum((blue_scores[number] for number in blues))
        bets = math.comb(len(reds), 6) * len(blues)
        strategy_note = '无偏随机覆盖' if not enforce_shape else '选号风格加权'
        note = f'{label}{len(reds)}+{len(blues)}；约{bets}注；{strategy_note}'
        accepted.append(Candidate(reds, blues, score, note, explain_candidate(reds, blues, analysis, not enforce_shape), build_ball_details(reds, blues, red_details, blue_details)))
    return select_diverse_candidates(accepted, count)

def select_diverse_candidates(candidates: list[Candidate], count: int) -> list[Candidate]:
    if count <= 0 or not candidates:
        return []
    remaining = list(candidates)
    selected = [remaining.pop(0)]
    while remaining and len(selected) < count:
        _, candidate = min(enumerate(remaining), key=lambda item: (candidate_overlap_score(item[1], selected), item[0]))
        selected.append(candidate)
        remaining.remove(candidate)
    return selected

def candidate_overlap_score(candidate: Candidate, selected: list[Candidate]) -> int:
    return sum((len(set(candidate.redBalls) & set(other.redBalls)) * 3 + len(set(candidate.blueBalls) & set(other.blueBalls)) for other in selected))

def passes_shape(reds: list[int], analysis: dict[str, Any]) -> bool:
    odd_count = sum((number % 2 for number in reds))
    total = sum(reds)
    zones = zone_distribution(reds)
    consecutive_pairs = sum((1 for left, right in zip(reds, reds[1:]) if right - left == 1))
    allowed_odd = set(analysis['commonOddCounts'])
    low_sum, high_sum = analysis['sumRange']
    common_zones = [tuple(pattern) for pattern in analysis['commonZonePatterns']]
    if len(reds) == 6:
        return odd_count in allowed_odd and low_sum <= total <= high_sum and (consecutive_pairs <= 2) and (tuple(zones) in common_zones)
    return 1 <= min(zones) and consecutive_pairs <= 3

def score_numbers(full_frequency: Counter[int], recent_frequency: dict[int, float], misses: dict[int, int], numbers: range, model_version: str='recent_focus_v3') -> dict[int, float]:
    if model_version == UNIFORM_RANDOM_MODEL_VERSION:
        return {number: 1.0 for number in numbers}
    weights = MODEL_CONFIGS.get(model_version, MODEL_CONFIGS['recent_focus_v3'])
    max_full = max(full_frequency.values(), default=1)
    max_recent = max(recent_frequency.values(), default=1.0)
    max_miss = max(misses.values(), default=1)
    center = (numbers.start + numbers.stop - 1) / 2
    return {number: round(weights['full'] * (full_frequency[number] / max_full if max_full else 0.0) + weights['recent'] * (recent_frequency[number] / max_recent if max_recent else 0.0) + weights['miss'] * (misses[number] / max_miss if max_miss else 0.0) + weights['center'] * (1 - abs(number - center) / max(center, 1)), 6) for number in numbers}

def score_breakdown(full_frequency: Counter[int], recent_frequency: dict[int, float], misses: dict[int, int], numbers: range, model_version: str='recent_focus_v3') -> dict[int, dict[str, Any]]:
    if model_version == UNIFORM_RANDOM_MODEL_VERSION:
        return {number: {'total': 1.0, 'fullFrequencyScore': 0.0, 'recentScore': 0.0, 'omissionScore': 0.0, 'centerBiasScore': 0.0, 'fullCount': full_frequency[number], 'recentWeighted': round(recent_frequency[number], 4), 'missCount': misses[number]} for number in numbers}
    weights = MODEL_CONFIGS.get(model_version, MODEL_CONFIGS['recent_focus_v3'])
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
        total = weights['full'] * full + weights['recent'] * recent + weights['miss'] * miss + weights['center'] * center_bias
        details[number] = {'total': round(total, 6), 'fullFrequencyScore': round(full, 6), 'recentScore': round(recent, 6), 'omissionScore': round(miss, 6), 'centerBiasScore': round(center_bias, 6), 'fullCount': full_frequency[number], 'recentWeighted': round(recent_frequency[number], 4), 'missCount': misses[number]}
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

def rank_numbers(full_frequency: Counter[int], recent_frequency: dict[int, float], misses: dict[int, int], numbers: range, reverse: bool, size: int, model_version: str='recent_focus_v3') -> list[int]:
    scored = score_numbers(full_frequency, recent_frequency, misses, numbers, model_version)
    return [number for number, _ in sorted(scored.items(), key=lambda item: (-item[1], item[0]) if reverse else (item[1], item[0]))[:size]]

def top_by_value(values: dict[int, int], reverse: bool, size: int) -> list[int]:
    return [number for number, _ in sorted(values.items(), key=lambda item: (-item[1], item[0]) if reverse else (item[1], item[0]))[:size]]

def plan_label(red_count: int, blue_count: int) -> str:
    return '单式' if red_count == 6 and blue_count == 1 else '复式'

def zone_distribution(reds: list[int]) -> list[int]:
    return [sum((1 for number in reds if 1 <= number <= 11)), sum((1 for number in reds if 12 <= number <= 22)), sum((1 for number in reds if 23 <= number <= 33))]

def percentile(values: list[int], ratio: float) -> int:
    if not values:
        return 0
    index = min(len(values) - 1, max(0, round((len(values) - 1) * ratio)))
    return values[index]

def stable_seed(text: str) -> int:
    value = 1125899907
    for char in text:
        value = value * 31 + ord(char) & 4294967295
    return value

def analysis_summary(analysis: dict[str, Any], model_version: str='recent_focus_v3') -> str:
    if model_version == UNIFORM_RANDOM_MODEL_VERSION:
        return f"样本{analysis['sampleSize']}期；当前采用无偏随机覆盖，不以冷热、遗漏或中段作为预测依据。"
    prefix = '多模型融合；' if model_version == ENSEMBLE_MODEL_VERSION else ''
    return f"{prefix}样本{analysis['sampleSize']}期；最近窗口{analysis['recentWindow']}期；红球热号{' '.join(map(str, analysis['hotReds'][:6]))}；红球遗漏{' '.join(map(str, analysis['overdueReds'][:6]))}；蓝球热号{' '.join(map(str, analysis['hotBlues'][:3]))}"

def explain_candidate(reds: list[int], blues: list[int], analysis: dict[str, Any], uniform_coverage: bool=False) -> list[str]:
    if uniform_coverage:
        return ['自动策略采用无偏随机覆盖，不把冷热、遗漏或号码位置当作预测因子。', f'本组红球为 {format_numbers(reds)}，蓝球为 {format_numbers(blues)}。']
    hot_reds = sorted(set(reds) & set(analysis['hotReds']))
    overdue_reds = sorted(set(reds) & set(analysis['overdueReds']))
    hot_blues = sorted(set(blues) & set(analysis['hotBlues']))
    odd_count = sum((number % 2 for number in reds))
    total = sum(reds)
    zones = zone_distribution(reds)
    reasons = [f'红球命中热号 {format_numbers(hot_reds)}，兼顾近期强势。', f'红球纳入遗漏较久号码 {format_numbers(overdue_reds)}，保留回补因子。', f'形态为奇偶 {odd_count}:{len(reds) - odd_count}，三区 {zones[0]}-{zones[1]}-{zones[2]}，和值 {total}。', f'蓝球选择 {format_numbers(blues)}，其中热号 {format_numbers(hot_blues)}。']
    return reasons

def build_ball_details(reds: list[int], blues: list[int], red_details: dict[int, dict[str, Any]], blue_details: dict[int, dict[str, Any]]) -> list[dict[str, Any]]:
    details: list[dict[str, Any]] = []
    for color, numbers, source in (('red', reds, red_details), ('blue', blues, blue_details)):
        for number in numbers:
            item = source[number]
            reasons = [f"全量出现 {item['fullCount']} 次，频率分 {item['fullFrequencyScore']:.2f}", f"近期加权值 {item['recentWeighted']:.2f}，近期分 {item['recentScore']:.2f}", f"当前遗漏 {item['missCount']} 期，遗漏分 {item['omissionScore']:.2f}"]
            if item['centerBiasScore'] >= 0.8:
                reasons.append(f"位置接近中段，形态分 {item['centerBiasScore']:.2f}")
            details.append({'color': color, 'number': number, 'totalScore': item['total'], 'fullFrequencyScore': item['fullFrequencyScore'], 'recentScore': item['recentScore'], 'omissionScore': item['omissionScore'], 'centerBiasScore': item['centerBiasScore'], 'fullCount': item['fullCount'], 'recentWeighted': item['recentWeighted'], 'missCount': item['missCount'], 'reasons': reasons})
    return details

def build_number_rankings(draws: list[SsqDraw], model_version: str, recent_window: int) -> dict[str, list[dict[str, Any]]]:
    ordered = list(reversed(draws))
    recent = ordered[-recent_window:]
    red_freq = Counter((red for draw in ordered for red in draw.redBalls))
    blue_freq = Counter((draw.blueBall for draw in ordered))
    red_miss = omission(draws, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_miss = omission(draws, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_recent = weighted_frequency(recent, range(1, 34), lambda draw, number: number in draw.redBalls)
    blue_recent = weighted_frequency(recent, range(1, 17), lambda draw, number: number == draw.blueBall)
    red_details = score_breakdown(red_freq, red_recent, red_miss, range(1, 34), model_version)
    blue_details = score_breakdown(blue_freq, blue_recent, blue_miss, range(1, 17), model_version)
    return {'reds': ranking_rows(draws, red_details, 'red'), 'blues': ranking_rows(draws, blue_details, 'blue')}

def ranking_rows(draws: list[SsqDraw], details: dict[int, dict[str, Any]], color: str) -> list[dict[str, Any]]:
    sorted_items = sorted(details.items(), key=lambda item: (-item[1]['total'], item[0]))
    rows: list[dict[str, Any]] = []
    for rank, (number, detail) in enumerate(sorted_items, start=1):
        predicate = (lambda draw: number in draw.redBalls) if color == 'red' else lambda draw: number == draw.blueBall
        rows.append({'color': color, 'number': number, 'rank': rank, 'totalScore': detail['total'], 'fullFrequencyScore': detail['fullFrequencyScore'], 'recentScore': detail['recentScore'], 'omissionScore': detail['omissionScore'], 'centerBiasScore': detail['centerBiasScore'], 'fullCount': detail['fullCount'], 'recentWeighted': detail['recentWeighted'], 'missCount': detail['missCount'], 'recent30Count': sum((1 for draw in draws[:30] if predicate(draw))), 'recent60Count': sum((1 for draw in draws[:60] if predicate(draw))), 'recent120Count': sum((1 for draw in draws[:120] if predicate(draw))), 'latestAppearances': [draw.issue for draw in draws if predicate(draw)][:5], 'summary': ranking_summary(rank, detail['recentScore'], detail['omissionScore'])})
    return rows

def ranking_summary(rank: int, recent_score: float, omission_score: float) -> str:
    if rank <= 6:
        return '处于当前模型核心候选区。'
    if recent_score >= 0.7:
        return '近期表现较强，但综合排名未进入最前列。'
    if omission_score >= 0.7:
        return '遗漏因子较高，但其他维度暂未同步。'
    return '当前综合评分靠后，暂未成为优先候选。'

def backtest_model(draws: list[SsqDraw], model_version: str, recent_window: int, sample_limit: int, single_count: int=0, compound_count: int=1, red_count: int=6, blue_count: int=3) -> dict[str, Any]:
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
    tier_counts = {'first': 0, 'second': 0, 'third': 0, 'fourth': 0, 'fifth': 0, 'sixth': 0}
    issue_count = 0
    for index in range(start, len(ordered)):
        train_draws = list(reversed(ordered[:index]))
        actual = ordered[index]
        if len(train_draws) < 30:
            continue
        analysis = analyze(train_draws, recent_window, model_version)
        predictions = recommend(train_draws, single_count, compound_count, red_count, blue_count, analysis, model_version, recent_window)
        if not predictions:
            continue
        red_hit = max((len(set(item['redBalls']) & set(actual.redBalls)) for item in predictions))
        blue_hit = any((actual.blueBall in item['blueBalls'] for item in predictions))
        settled = [settle_server_prediction(item, actual) for item in predictions]
        settled = [item for item in settled if item]
        best_red_hits.append(red_hit)
        blue_hits += int(blue_hit)
        at_least_three += int(red_hit >= 3)
        prize_hits += int(any((item['prizeAmount'] > 0 for item in settled)))
        bet_counts.append(sum((item['betCount'] for item in settled)))
        distinct_blue_counts.append(len({blue for item in predictions for blue in item['blueBalls']}))
        invested_amount += sum((item['betCount'] * 2.0 for item in settled))
        simulated_prize_amount += sum((item['prizeAmount'] for item in settled))
        for item in settled:
            for tier, count in item['tierCounts'].items():
                tier_counts[tier] = tier_counts.get(tier, 0) + count
        issue_count += 1
    return {'version': model_version, 'issueCount': issue_count, 'averageBestRedHits': round(sum(best_red_hits) / issue_count, 4) if issue_count else 0.0, 'blueHitRate': round(blue_hits / issue_count, 4) if issue_count else 0.0, 'atLeastThreeRedRate': round(at_least_three / issue_count, 4) if issue_count else 0.0, 'prizeHitRate': round(prize_hits / issue_count, 4) if issue_count else 0.0, 'averageBetCount': round(sum(bet_counts) / issue_count, 4) if issue_count else 0.0, 'averageDistinctBlueCount': round(sum(distinct_blue_counts) / issue_count, 4) if issue_count else 0.0, 'investedAmount': round(invested_amount, 2), 'simulatedPrizeAmount': round(simulated_prize_amount, 2), 'roi': round((simulated_prize_amount - invested_amount) / invested_amount, 4) if invested_amount else 0.0, 'tierCounts': tier_counts}

def build_ssq_model_evaluation(draws: list[SsqDraw], recent_window: int=500, sample_limit: int=SSQ_EVALUATION_SAMPLE_LIMIT, fold_count: int=SSQ_EVALUATION_FOLD_COUNT, single_count: int=0, compound_count: int=1, red_count: int=6, blue_count: int=3) -> dict[str, Any]:
    fold_reports: dict[str, list[dict[str, Any]]] = {version: [] for version in EVALUATION_MODEL_VERSIONS}
    for fold_index in range(fold_count):
        subset = draws[fold_index * sample_limit:]
        if len(subset) < 30 + sample_limit:
            break
        for version in EVALUATION_MODEL_VERSIONS:
            fold_reports[version].append(backtest_model(subset, version, recent_window, sample_limit, single_count=single_count, compound_count=compound_count, red_count=red_count, blue_count=blue_count))
    reports = [aggregate_model_evaluation(version, reports) for version, reports in fold_reports.items() if reports]
    recommended, selection_reason = select_ssq_model(reports)
    return {'evaluationVersion': SSQ_EVALUATION_VERSION, 'latestIssue': draws[0].issue if draws else '', 'generatedAt': datetime.now(timezone.utc).isoformat(), 'recentWindow': recent_window, 'sampleLimit': sample_limit, 'foldCount': max((len(items) for items in fold_reports.values()), default=0), 'singleCount': single_count, 'compoundCount': compound_count, 'redCount': red_count, 'blueCount': blue_count, 'modelReports': reports, 'recommendedModelVersion': recommended, 'selectionReason': selection_reason}

def aggregate_model_evaluation(version: str, reports: list[dict[str, Any]]) -> dict[str, Any]:
    issue_count = sum((item['issueCount'] for item in reports))
    invested = sum((item['investedAmount'] for item in reports))
    prize = sum((item['simulatedPrizeAmount'] for item in reports))
    tiers = {'first': 0, 'second': 0, 'third': 0, 'fourth': 0, 'fifth': 0, 'sixth': 0}
    for report in reports:
        for tier, count in report['tierCounts'].items():
            tiers[tier] = tiers.get(tier, 0) + count
    fold_scores = [ssq_fold_selection_score(report) for report in reports]
    score_mean = mean(fold_scores) if fold_scores else 0.0
    score_stddev = pstdev(fold_scores) if len(fold_scores) > 1 else 0.0
    selection_score = min(100.0, max(0.0, score_mean - 0.25 * score_stddev))
    return {'version': version, 'issueCount': issue_count, 'averageBestRedHits': round(sum((item['averageBestRedHits'] * item['issueCount'] for item in reports)) / issue_count, 4) if issue_count else 0.0, 'blueHitRate': round(sum((item['blueHitRate'] * item['issueCount'] for item in reports)) / issue_count, 4) if issue_count else 0.0, 'atLeastThreeRedRate': round(sum((item['atLeastThreeRedRate'] * item['issueCount'] for item in reports)) / issue_count, 4) if issue_count else 0.0, 'prizeHitRate': round(sum((item['prizeHitRate'] * item['issueCount'] for item in reports)) / issue_count, 4) if issue_count else 0.0, 'averageBetCount': round(sum((item['averageBetCount'] * item['issueCount'] for item in reports)) / issue_count, 4) if issue_count else 0.0, 'averageDistinctBlueCount': round(sum((item['averageDistinctBlueCount'] * item['issueCount'] for item in reports)) / issue_count, 4) if issue_count else 0.0, 'investedAmount': round(invested, 2), 'simulatedPrizeAmount': round(prize, 2), 'roi': round((prize - invested) / invested, 4) if invested else 0.0, 'tierCounts': tiers, 'foldReports': reports, 'foldSelectionScores': [round(score, 2) for score in fold_scores], 'selectionScoreMean': round(score_mean, 2), 'selectionScoreStdDev': round(score_stddev, 2), 'selectionScore': round(selection_score, 2)}

def ssq_fold_selection_score(report: dict[str, Any]) -> float:
    red_coverage = min(1.0, max(0.0, float(report.get('averageBestRedHits', 0.0)) / 6.0))
    blue_hit_rate = min(1.0, max(0.0, float(report.get('blueHitRate', 0.0))))
    prize_hit_rate = min(1.0, max(0.0, float(report.get('prizeHitRate', 0.0))))
    three_red_rate = min(1.0, max(0.0, float(report.get('atLeastThreeRedRate', 0.0))))
    return 100.0 * (0.4 * red_coverage + 0.25 * blue_hit_rate + 0.25 * prize_hit_rate + 0.1 * three_red_rate)

def select_ssq_model(reports: list[dict[str, Any]]) -> tuple[str, str]:
    model_order = {version: index for index, version in enumerate(MODEL_CONFIGS)}
    candidates = [report for report in reports if report.get('version') in MODEL_CONFIGS]
    if not candidates:
        return ('recent_focus_v3', '没有可用的正式模型评估结果，已回退 recent_focus_v3。')
    selected = max(candidates, key=lambda item: (float(item.get('selectionScore', 0.0)), float(item.get('selectionScoreMean', 0.0)), -model_order[str(item['version'])]))
    version = str(selected['version'])
    score = float(selected.get('selectionScore', 0.0))
    return (version, f'{version} 的统一综合分最高（{score:.2f}），自动模式已按稳定性惩罚后的历史验证结果选择。')

def compute_fusion_weights(model_reports: list[dict[str, Any]]) -> dict[str, float]:
    """按各模型 selectionScore 比例归一化，得到融合权重（和为 1）。"""
    scores = {str(report['version']): max(0.0, float(report.get('selectionScore', 0.0))) for report in model_reports if str(report.get('version')) in MODEL_CONFIGS}
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
    per_model = {version: score_breakdown(full_frequency, recent_frequency, misses, numbers, version) for version in MODEL_CONFIGS}
    score_keys = ('total', 'fullFrequencyScore', 'recentScore', 'omissionScore', 'centerBiasScore')
    fused: dict[int, dict[str, Any]] = {}
    for number in numbers:
        detail = per_model['recent_focus_v3'][number]
        merged = {key: round(sum((fusion_weights[version] * per_model[version][number][key] for version in MODEL_CONFIGS)), 6) for key in score_keys}
        merged['fullCount'] = detail['fullCount']
        merged['recentWeighted'] = detail['recentWeighted']
        merged['missCount'] = detail['missCount']
        fused[number] = merged
    return fused

def compute_fused_selection_score(model_reports: list[dict[str, Any]], fusion_weights: dict[str, float]) -> float:
    """多模型综合分 = 各模型 selectionScore 按融合权重加权平均。"""
    score_map = {str(report['version']): float(report.get('selectionScore', 0.0)) for report in model_reports if str(report.get('version')) in MODEL_CONFIGS}
    if not score_map:
        return 0.0
    return sum((fusion_weights.get(version, 0.0) * score for version, score in score_map.items()))

def build_narrative(analysis: dict[str, Any], model_version: str) -> list[str]:
    first = f"当前使用多模型融合（{model_version}），已按 4 个模型的历史回测得分加权统一出号，最近窗口 {analysis['recentWindow']} 期。" if model_version == ENSEMBLE_MODEL_VERSION else f"当前使用 {model_version}，最近窗口 {analysis['recentWindow']} 期，目标是兼顾长期稳定性与近期变化。"
    return [first, f"红球热号集中在 {format_numbers(analysis['hotReds'][:6])}，遗漏较久的是 {format_numbers(analysis['overdueReds'][:6])}。", f"历史常见奇数个数为 {'/'.join(map(str, analysis['commonOddCounts']))}，和值主要落在 {analysis['sumRange'][0]}-{analysis['sumRange'][1]}。"]

def format_numbers(numbers: list[int]) -> str:
    return ' '.join((f'{number:02d}' for number in numbers)) if numbers else '无'

def next_issue(issue: str) -> str:
    try:
        return str(int(issue) + 1).zfill(len(issue))
    except ValueError:
        return 'next'
