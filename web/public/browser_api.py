import json
from ssq_core import *

def browser_request(text):
    payload = json.loads(text)
    draws = [SsqDraw(**item) for item in payload['draws']]
    if payload['action'] == 'settle':
        actual = {d.issue: d for d in draws}
        return json.dumps({run['id']: build_server_settlement(actual[run['targetIssue']], run['predictions'])
                           for run in payload['runs'] if run['targetIssue'] in actual}, ensure_ascii=False)
    plan = payload['plan']
    options = dict(recent_window=plan['recentWindow'], single_count=plan['singleCount'],
                   compound_count=plan['compoundCount'], red_count=plan['redCount'], blue_count=plan['blueCount'])
    evaluation = payload.get('evaluation') or build_ssq_model_evaluation(draws, **options)
    weights = compute_fusion_weights(evaluation['modelReports'])
    version = ENSEMBLE_MODEL_VERSION if plan['modelVersion'] == 'auto' else plan['modelVersion']
    analysis = analyze(draws, plan['recentWindow'], version)
    predictions = recommend(draws, plan['singleCount'], plan['compoundCount'], plan['redCount'],
                            plan['blueCount'], analysis, version, plan['recentWindow'], fusion_weights=weights)
    score = compute_fused_selection_score(evaluation['modelReports'], weights) if version == ENSEMBLE_MODEL_VERSION else next((r['selectionScore'] for r in evaluation['modelReports'] if r['version'] == version), 0)
    for prediction in predictions:
        prediction['score'] = score
    return json.dumps(dict(analysis=analysis, predictions=predictions, evaluation=evaluation,
                          narrative=build_narrative(analysis, version), weights=weights,
                          rankings=build_number_rankings(draws, version, plan['recentWindow'])), ensure_ascii=False)
