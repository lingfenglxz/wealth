"""Fallback for reads; independent confirmation for publishing new SSQ draws."""
from datetime import datetime, timedelta
import os
import draw_sources as readers

POLICY_VERSION = 1
APPROVED = ('500', 'tianji', 'sina')
PENDING_GRACE = timedelta(hours=2)

def health_due(manifest, now):
    try:
        if manifest.get('healthEnvironment') != health_environment(): return True
        return datetime.fromisoformat(manifest['healthCheckedAt']).astimezone(readers.CHINA).date() != now.date()
    except (KeyError, TypeError, ValueError):
        return True

def health_environment():
    return 'github-actions' if os.environ.get('GITHUB_ACTIONS') == 'true' else 'local'

def focus_date(now):
    # This calendar controls extra checks only, never supplies a draw's date.
    if now.weekday() in (1, 3, 6) and now.hour >= 21: return now.date()
    yesterday = now - timedelta(days=1)
    if yesterday.weekday() in (1, 3, 6) and now.hour < 4: return yesterday.date()
    return None

class Session:
    def __init__(self, existing, now, fetchers=None):
        self.known = {row['issue']: readers.validate(row) for row in existing}
        self.now = now; self.cache = {}; self.errors = {}; self.info = {}
        self.fetchers = fetchers or {
            '500': readers.fetch_500,
            'tianji': lambda: readers.fetch_tianji(self.known),
            'sina': readers.fetch_sina,
            'cwl': readers.fetch_cwl,
            '711': readers.fetch_711,
            'jiangsu': readers.fetch_jiangsu,
        }

    def fetch(self, source, observe=False):
        if source in self.errors:
            if isinstance(self.errors[source], readers.Conflict) and not observe: raise self.errors[source]
            return None
        if source in self.cache: return self.cache[source]
        label, url, role = readers.SOURCES[source]
        info = dict(id=source, label=label, url=url, role=role, checkedAt=self.now.isoformat())
        self.info[source] = info
        try:
            rows = self.fetchers[source]()
            if source == 'jiangsu':
                overlap = [issue for issue in rows if issue in self.known]
                if len(overlap) < min(10, len(self.known)): raise ValueError('Insufficient announcement date overlap')
                if any(rows[issue] != self.known[issue]['date'] for issue in overlap): raise ValueError('Announcement date differs from actual draw date')
                info.update(status='healthy', latestIssue=max(rows), rowCount=len(rows), completeDates=False)
            else:
                rows = readers.normalize(rows)
                overlap = [row for row in rows if row['issue'] in self.known]
                if len(overlap) < min(10, len(self.known)): raise ValueError('Insufficient published history overlap')
                for row in overlap:
                    actual = self.known[row['issue']]
                    if readers.numbers(row) != readers.numbers(actual) or (row.get('date') and row['date'] != actual['date']):
                        raise readers.Conflict(f'{label} conflicts with published issue {row["issue"]}')
                latest = rows[0]['issue']
                info.update(status='healthy' if latest >= max(self.known) else 'lagging', latestIssue=latest,
                    rowCount=len(rows), completeDates=all('date' in row for row in rows))
            self.cache[source] = rows
            print(f'Source {source}: {info["status"]}, latest {info["latestIssue"]}', flush=True)
            return rows
        except Exception as error:
            info.update(status='conflict' if isinstance(error, readers.Conflict) else 'unavailable', error=str(error)[:200])
            self.errors[source] = error
            print(f'Source {source}: {info["status"]} ({info["error"]})', flush=True)
            if isinstance(error, readers.Conflict) and not observe: raise
            return None

    def daily_health(self):
        for source in readers.SOURCES: self.fetch(source, observe=True)

def candidates(session, source_ids):
    index = {}
    for source in source_ids:
        for row in session.cache.get(source, []):
            if row['issue'] <= max(session.known): continue
            entry = index.setdefault(row['issue'], {})
            for other in entry.values():
                if readers.numbers(row) != readers.numbers(other): raise readers.Conflict('Independent sources disagree on issue ' + row['issue'])
                if row.get('date') and other.get('date') and row['date'] != other['date']: raise readers.Conflict('Actual draw dates disagree on issue ' + row['issue'])
            entry[source] = row
    return index

def next_contiguous(previous, issue):
    return (issue[:4] == previous[:4] and int(issue) == int(previous) + 1) or (int(issue[:4]) == int(previous[:4]) + 1 and issue[4:] == '001')

def confirmed_prefix(session, index):
    accepted = []; verification = {}; previous = max(session.known)
    previous_date = session.known[previous]['date']
    for issue in sorted(index):
        rows = index[issue]
        full = [row for row in rows.values() if row.get('date')]
        if len(rows) < 2 or not full or not next_contiguous(previous, issue): break
        row = readers.validate(full[0])
        if row['date'] <= previous_date: raise readers.Conflict('New draw dates are not increasing')
        accepted.append(row); verification[issue] = list(rows)
        previous = issue; previous_date = row['date']
    return accepted, verification

def select(session, published_verification=None, old_manifest=None):
    old_manifest = old_manifest or {}; published_verification = published_verification or {}
    approved = list(APPROVED)
    for source in approved:
        if isinstance(session.errors.get(source), readers.Conflict): raise session.errors[source]
    # Only a successful once-daily official check joins this run, not every hourly read.
    if 'cwl' in session.cache: approved.append('cwl')
    primary = None
    for source in approved:
        rows = session.fetch(source)
        if rows and rows[0]['issue'] >= max(session.known): primary = source; break
    if not primary: raise readers.Unavailable('All active sources failed or are older than published history')
    index = candidates(session, approved)
    expected = focus_date(session.now)
    focus = expected is not None and session.cache[primary][0].get('date', session.known[max(session.known)]['date']) < expected.isoformat()
    initialize = published_verification.get('policyVersion') != POLICY_VERSION
    checked = [source for source in approved if source in session.cache]
    if index or focus or initialize:
        for source in approved:
            if source == primary or source in session.cache or source in session.errors: continue
            session.fetch(source); checked.append(source)
            index = candidates(session, approved)
            accepted, votes = confirmed_prefix(session, index)
            if index and len(accepted) == len(index): break
            if not index and session.cache.get(source) and any(row['issue'] == max(session.known) for row in session.cache[source]): break
    index = candidates(session, approved)
    accepted, votes = confirmed_prefix(session, index)
    pending_issues = sorted(set(index) - {row['issue'] for row in accepted})
    pending = None
    if pending_issues:
        old = old_manifest.get('pending') or {}
        first = old.get('firstSeenAt') if set(old.get('issues', [])) & set(pending_issues) else None
        first = first or session.now.isoformat()
        pending = dict(issues=pending_issues, firstSeenAt=first, reason='Waiting for two independent number sources, actual date and consecutive issues')
        if not accepted and session.now - datetime.fromisoformat(first) >= PENDING_GRACE:
            raise readers.Unavailable('New draw remains unconfirmed for two hours; published data retained')
        print('New draws waiting for confirmation: ' + ', '.join(pending_issues), flush=True)
    latest = accepted[-1]['issue'] if accepted else max(session.known)
    supporters = votes.get(latest, [])
    if not supporters and (initialize or focus):
        supporters = [source for source in approved if any(row['issue'] == latest for row in session.cache.get(source, []))]
    if not supporters: supporters = [v['id'] for v in published_verification.get('sources', [])]
    if initialize and len(supporters) < 2: raise readers.Unavailable('Cannot establish independent verification for the existing latest draw')
    date_source = next((source for source in approved if any(row['issue'] == latest and row.get('date') for row in session.cache.get(source, []))), primary)
    verification = dict(policyVersion=POLICY_VERSION, issue=latest,
        sources=[dict(id=source, label=readers.SOURCES[source][0], url=readers.SOURCES[source][1]) for source in supporters],
        dateSource=date_source)
    return dict(draws=accepted, source=readers.SOURCES[date_source][1], verification=verification, pending=pending)

def manifest(previous, session, selection, daily):
    result = dict(schemaVersion=1, policyVersion=POLICY_VERSION,
        latestIssue=selection['verification']['issue'], pending=selection['pending'],
        healthCheckedAt=session.now.isoformat() if daily else previous.get('healthCheckedAt'),
        healthEnvironment=health_environment(),
        sources=[session.info[source] for source in readers.SOURCES if source in session.info] if daily else previous.get('sources', []))
    return result
