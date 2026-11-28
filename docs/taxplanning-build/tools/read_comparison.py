"""The rehearsal's side-by-side read comparison (W-406, D-981; runbook §0.10).

Reads every GET route of the w-337 build's OpenAPI spec, as every user, from two running builds -- m1.6.1 on the MongoDB
copy and w-337 on the PostgreSQL copy -- and diffs the answers. A difference not on the list fixed beforehand (D-981 C3a)
stops the move. Read-only: it sends GET and nothing else. Removed with the copier at W-337j (WL-50).

  python3 -I read_comparison.py spec    --base URL --users FILE --out spec.json
  python3 -I read_comparison.py fetch   --base URL --users FILE --spec spec.json --out DIR --workspaces FILE [--value name=v1,v2 ...]
  python3 -I read_comparison.py compare BEFORE_DIR AFTER_DIR

The users file holds one "email roles" line per account (roles as user_details.roles stores them, e.g. USER,SUPER_USER).
JWT_SECURITY_KEY is read from a prompt and never written.
"""
import argparse
import base64
import getpass
import hashlib
import hmac
import io
import json
import math
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ElementTree
import zipfile
from datetime import datetime, timezone
from zoneinfo import ZoneInfo

MANIFEST = "manifest.json"
SPEC_PATH = "/api/v1/admin/docs/openapi"
# AccessTokens.LIFETIME (AccessTokens.java:22) and the factor a password sign-in adds (AuthService.java:107 - 111).
TOKEN_SECONDS = 15 * 60
PASSWORD_FACTOR = "FACTOR_PASSWORD"
# spring.data.web.pageable.max-page-size (application.yaml:29).
PAGE_SIZE = 200
# D-958 (4): m1.6.1 ran in this zone; w-337 sends the same instant in UTC until W-403.
BUSINESS_ZONE = "Asia/Kolkata"
PUBLIC_USER = "(public)"
ADMIN_USER = "(admin)"
SUPER_USER = "SUPER_USER"
WORKSPACE = "/workspaces/{taxYear}"
STORED_WORKSPACE = re.compile(r"/profiles/([^/]+)/workspaces/([^/?]+)")

# Where each path id comes from: the list route that names it, and the key it is under in that route's answer.
PARAM_SOURCES = {
    "profileId": [("/api/v1/me/tax/profiles", "id")],
    "draftId": [("/api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}/drafts", "id")],
    "number": [("/api/v1/me/tax/drafts/{draftId}/revisions", "revisionNumber")],
    "receipt": [("/api/v1/me/tax/profiles/{profileId}/computations", "receipt")],
    "transactionId": [("/api/v1/me/portfolio/transactions", "transactionId")],
    "stockCode": [("/api/v1/me/portfolio/stocks", "stockCode")],
    "id": [("/api/v1/me/corporate-actions", "id")],
    "scheduleCode": [("/api/v1/me/charges/charge-schedules/unverified", "scheduleCode"),
                     ("/api/v1/me/charges/charge-schedules", "scheduleCode")],
}

# D-981 C3a (2): fields filled at request time, as (route, JSON path with [*] for any index), each with the line that
# fills it. Empty until the dev run names them (plans/W-406.md appendix); until then every such field stops the move.
REQUEST_TIME_FIELDS = (
    # AnalyticsService.java:86: .lastUpdated(LocalDateTime.now(clock.withZone(ZoneOffset.UTC))), the moment of the read
    ("/api/v1/me/portfolio/analytics/portfolio-summary", "$.lastUpdated"),
)

# D-983 C5a: the response times m1.6.1 already held in UTC, as (route glob, path glob), each with the m1.6.1 line that
# wrote it. These must come back equal; every other date-time must come back shifted (m1.6.1 showed it in BUSINESS_ZONE).
# In a path, [*] is any index, * any one key and ** any depth; in a route, * is any text.
UTC_HELD_FIELDS = (
    # UserChargeService.java:157 row.setComputedOn(TLocalDateTime.now()), and TLocalDateTime.java:16 - 18 is UTC
    ("/api/v1/*/charges/user-charges*", "**.computedOn"),
    # ChargeSummaryReport.java:71 lastUpdatedTime = TLocalDateTime.now(); the yearly and monthly summaries extend it
    # (YearlyChargeSummary.java:22, MonthlyChargeSummary.java:18) and each half is one
    ("/api/v1/me/portfolio/profit-and-loss", "$.realisedProfits.yearlyChargeSummary.lastUpdatedTime"),
    ("/api/v1/me/portfolio/profit-and-loss", "$.realisedProfits.yearlyChargeSummary.monthlyReport.*.lastUpdatedTime"),
    ("/api/v1/me/portfolio/profit-and-loss", "$.realisedProfits.yearlyChargeSummary.monthlyReport.*.firstHalfCharges.lastUpdatedTime"),
    ("/api/v1/me/portfolio/profit-and-loss", "$.realisedProfits.yearlyChargeSummary.monthlyReport.*.secondHalfCharges.lastUpdatedTime"),
    # DraftComputationService.java:102 setComputedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)))
    ("/api/v1/me/tax/profiles/{profileId}/computations", "$.content[*].computedAt"),
    # StalenessSweep.java:90 new DraftStaleness(context.nowUtc(), ...)
    ("/api/v1/me/tax/drafts/{draftId}", "$.staleness.since"),
    ("/api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}/drafts", "$[*].staleness.since"),
    # GrievanceService.java:30, 57 LocalDateTime.now(clock.withZone(ZoneOffset.UTC)); respondBy counts from receivedAt
    ("/api/v1/*/privacy/grievances*", "**.receivedAt"),
    ("/api/v1/*/privacy/grievances*", "**.respondBy"),
    ("/api/v1/*/privacy/grievances*", "**.answeredAt"),
    # BreachNoticeService.java:188 - 189 now() is LocalDateTime.now(clock.withZone(ZoneOffset.UTC))
    ("/api/v1/me/privacy/breach-notices", "**.postedAt"),
    # AccountErasureService.java:38, 84 the request and erasure times in UTC
    ("/api/v1/*/privacy/erasure*", "**.requestedAt"),
    ("/api/v1/*/privacy/erasure*", "**.erasedAt"),
    # DocumentService.java:84, 123, 176 - 177 now() in UTC; ParseDocuments.java:103, 125 context.nowUtc()
    ("/api/v1/me/tax/documents/*", "**.uploadedAt"),
    ("/api/v1/me/tax/documents/*", "**.confirmedAt"),
    ("/api/v1/me/tax/documents/*", "**.startedAt"),
    ("/api/v1/me/tax/documents/*", "**.finishedAt"),
    # GazetteWatch.java:111 lead.setFoundAt(context.nowUtc())
    ("/api/v1/admin/tax/gazette-leads", "**.foundAt"),
    # AssetRequest.java:159, 162 TLocalDateTime.atUtc(...): the order time is held in UTC (D-961)
    ("/api/v1/me/portfolio/transactions", "$.content[*].orderExecutionTime"),
    # the same UTC time on each lot: PortfolioService.java:445, 453 assetRequest.orderExecutionDateTime(), LotLedger.java:201
    # trade.getOrderExecutionTime() (W-419, D-1002); the holdings lists leave the field out (HOLDING_ROUTES)
    ("/api/v1/me/portfolio/stocks/{stockCode}", "$[*].orderTimeQuantities[*].orderExecutionTime"),
)

# W-419 R1-C1a, R3-C1a (D-1002): these views combine a holding's lots, copy every field from the first lot read and then set
# the holding's own (PortfolioQueryService.java:115 – 156, 87). m1.6.1 read lots in MongoDB's stored order, which the
# 2026-11-21 rewrite left and no field records, so its first lot was arbitrary; the w-337 build reads oldest first
# (PortfolioRepository.findByEmail). They are compared holding by holding on HOLDING_FIELDS only — the key and what the
# view works out for the holding — each lot's own fields being compared on /stocks/{stockCode},
# put in one order on both sides first (ORDERED_FOR_COMPARISON, W-422). A worked-out number is a double summed lot by lot, so lots in another order move its last digits: it is
# equal within HOLDING_RELATIVE_TOLERANCE.
HOLDING_ROUTES = (
    "/api/v1/me/portfolio/stocks",
    "/api/v1/me/portfolio/mutual-funds",
    "/api/v1/admin/portfolio/users/{email}/stocks",
    "/api/v1/admin/portfolio/users/{email}/mutual-funds",
)
HOLDING_KEY = ("stockCode", "brokerName", "accountHolder")
HOLDING_FIELDS = HOLDING_KEY + ("quantity", "totalQuantity", "totalValue", "price", "brokerCharges", "miscCharges",
                                "transactionQuantities", "daysHeld", "transactionDate", "charges")
HOLDING_RELATIVE_TOLERANCE = 1e-9

# W-419 C3a, R2-C1a (D-1002): the lists whose order is not the move's — one the w-337 build orders where m1.6.1 left ties in
# MongoDB's stored order, or one neither build orders — as (route, the key fields, each with descending True or False). Both
# answers are put in that order before comparing; every other list keeps its position.
ORDERED_FOR_COMPARISON = {
    # ChargeScheduleRepository.java:43 findByBrokerNameOrderByStartDateDescScheduleCodeAsc (W-411, D-987); m1.6.1's
    # findByBrokerNameOrderByStartDateDesc left cards sharing a start date in MongoDB's stored order
    "/api/v1/me/charges/charge-schedules": (("startDate", True), ("scheduleCode", False)),
    # AnalyticsService.java:94 – 110 (and :93 – 108 at m1.6.1) groups by AssetType into a HashMap, whose order follows the
    # enum's identity hash: it changes with each start of either build
    "/api/v1/me/portfolio/analytics/asset-allocation": (("assetType", False),),
    # PortfolioRepository.findByEmailAndStockCodeOrderByTransactionDate orders by trade date, then oldest first (W-422,
    # D-1011); m1.6.1's (PortfolioRepository.java:36 at m1.6.1) by trade date alone, so lots sharing a date came back in
    # MongoDB's stored order. AssetResponse carries no id: a lot is told by its broker and the buys it was made from
    "/api/v1/me/portfolio/stocks/{stockCode}": (("transactionDate", False), ("brokerName", False), ("buyTransactionIds", False)),
}

DATE_TIME = re.compile(r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(\.\d{1,9})?$")
SHEET_NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
RELATIONSHIP_NS = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}"
PACKAGE_NS = "{http://schemas.openxmlformats.org/package/2006/relationships}"


def b64url(raw):
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def mint(key, email, roles, now):
    """An access token as AccessTokens issues one at a password sign-in: HS256 on the key, the roles and the factor."""
    authorities = ["ROLE_" + role for role in roles.split(",")] + [PASSWORD_FACTOR]
    header = b64url(json.dumps({"alg": "HS256"}, separators=(",", ":")).encode())
    claims = {"sub": email, "iat": now, "exp": now + TOKEN_SECONDS, "authorities": authorities}
    payload = b64url(json.dumps(claims, separators=(",", ":")).encode())
    signature = hmac.new(base64.b64decode(key), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    return f"{header}.{payload}.{b64url(signature)}"


def read_workspaces(path):
    with open(path) as lines:
        return {tuple(line.split()) for line in lines if line.strip()}


def http_get(base):
    def get(url, token):
        request = urllib.request.Request(base + url, method="GET")
        if token is not None:
            request.add_header("Authorization", "Bearer " + token)
        try:
            with urllib.request.urlopen(request, timeout=120) as answer:
                return answer.status, answer.headers.get("Content-Type", ""), answer.read()
        except urllib.error.HTTPError as refused:
            return refused.code, refused.headers.get("Content-Type", ""), refused.read()
    return get


def read_users(path):
    with open(path) as lines:
        users = [tuple(line.split()) for line in lines if line.strip()]
    for user in users:
        if len(user) != 2:
            sys.exit(f"A users line must be 'email roles': {' '.join(user)}")
    return users


def get_routes(spec):
    """Every GET route: (path, path parameter names, required query parameter names, whether it pages)."""
    routes = []
    for path, operations in sorted(spec["paths"].items()):
        if "get" not in operations:
            continue
        parameters = operations["get"].get("parameters", [])
        path_names = re.findall(r"{(\w+)}", path)
        required = [p["name"] for p in parameters if p.get("in") == "query" and p.get("required")]
        pages = any(p.get("in") == "query" and p["name"] == "page" for p in parameters)
        routes.append((path, path_names, required, pages))
    return routes


def zone_of(path):
    return path.split("/")[3] if path.startswith("/api/v1/") else "public"


def collect(value, key, into):
    if isinstance(value, dict):
        for name, inner in value.items():
            if name == key and isinstance(inner, (str, int)) and not isinstance(inner, bool):
                into.add(str(inner))
            collect(inner, key, into)
    elif isinstance(value, list):
        for inner in value:
            collect(inner, key, into)


def fill(path, names, known):
    """Every concrete path for the template, one per combination of known values; none if a name has no value."""
    paths = [path]
    for name in names:
        values = sorted(known.get(name, ()))
        if not values:
            return []
        paths = [p.replace("{" + name + "}", urllib.parse.quote(v, safe="")) for p in paths for v in values]
    return paths


def queries(required, given):
    combos = [[]]
    for name in required:
        if name not in given:
            return None
        combos = [c + [(name, v)] for c in combos for v in given[name]]
    return combos


class Reader:
    """Reads one caller's routes, saving each answer and gathering the ids later routes need."""

    def __init__(self, get, out, given, sources, answers, workspaces):
        self.get, self.out, self.given, self.sources, self.answers = get, out, given, sources, answers
        self.workspaces = workspaces

    def read(self, user, token_for, routes, known):
        done = set()
        progressed = True
        while progressed:
            progressed = False
            for route in routes:
                path, names, required, pages = route
                if path in done or any(n not in known and self.waits_on(n, routes, done) for n in names):
                    continue
                done.add(path)
                progressed = True
                combos = queries(required, self.given)
                for concrete in self.stored(path, fill(path, names, known)) if combos is not None else []:
                    for combo in combos:
                        self.read_one(user, token_for, path, concrete, combo, pages, known)

    def waits_on(self, name, routes, done):
        pending = {r[0] for r in routes} - done
        return any(source in pending for source, _ in self.sources.get(name, []))

    def stored(self, path, concretes):
        """A workspace route only for a (profile, year) the copy holds: reading any other creates it (DraftService.java:55 - 58,
        80), so each build would make its own (D-983 C6a)."""
        if WORKSPACE not in path:
            return concretes
        return [c for c in concretes if STORED_WORKSPACE.search(c).groups() in self.workspaces]

    def read_one(self, user, token_for, path, concrete, combo, pages, known):
        number = 0
        while True:
            query = combo + ([("page", str(number)), ("size", str(PAGE_SIZE))] if pages else [])
            url = concrete + ("?" + urllib.parse.urlencode(query) if query else "")
            status, content_type, body = self.get(url, token_for())
            self.save(user, path, url, status, content_type, body)
            parsed = json.loads(body) if status == 200 and "json" in content_type else None
            for name, sources in self.sources.items():
                for source, key in sources:
                    if source == path and parsed is not None:
                        collect(parsed, key, known.setdefault(name, set()))
            total = parsed.get("page", {}).get("totalPages", 1) if pages and isinstance(parsed, dict) else 1
            number += 1
            if number >= total:
                return

    def save(self, user, path, url, status, content_type, body):
        name = f"{len(self.answers):06d}.body"
        with open(os.path.join(self.out, name), "wb") as saved:
            saved.write(body)
        self.answers.append({"user": user, "route": path, "url": url, "status": status, "contentType": content_type, "file": name})


def fetch(spec, users, key, get, out, given, sources=PARAM_SOURCES, workspaces=frozenset()):
    """Reads every GET route of the spec: public ones once with no token, admin ones once as the first super user, the
    caller's own (/me) as every user. Refuses before any read if a source route is not in the spec."""
    routes = get_routes(spec)
    paths = {r[0] for r in routes}
    missing = sorted(s for listed in sources.values() for s, _ in listed if s not in paths)
    if missing:
        sys.exit("Source routes not in the spec (PARAM_SOURCES is stale): " + ", ".join(missing))
    os.makedirs(out, exist_ok=True)
    answers = []
    reader = Reader(get, out, given, sources, answers, workspaces)
    by_zone = {zone: [r for r in routes if zone_of(r[0]) == zone] for zone in ("public", "me", "admin")}
    by_zone["public"] += [r for r in routes if zone_of(r[0]) not in by_zone]
    reader.read(PUBLIC_USER, lambda: None, by_zone["public"], {n: set(v) for n, v in given.items()})
    admins = [u for u in users if SUPER_USER in u[1].split(",")]
    if admins:
        email, roles = admins[0]
        known = {n: set(v) for n, v in given.items()} | {"email": {u[0] for u in users}}
        reader.read(ADMIN_USER, lambda: mint(key, email, roles, int(time.time())), by_zone["admin"], known)
    for email, roles in users:
        token_for = lambda e=email, r=roles: mint(key, e, r, int(time.time()))  # noqa: E731
        reader.read(email, token_for, by_zone["me"], {n: set(v) for n, v in given.items()})
    not_reached = sorted({r[0] for r in routes} - {a["route"] for a in answers})
    with open(os.path.join(out, MANIFEST), "w") as manifest:
        json.dump({"answers": answers, "notReached": not_reached}, manifest, indent=1)
    return answers


def shown_in_zone(utc_text, zone):
    return datetime.fromisoformat(utc_text).replace(tzinfo=timezone.utc).astimezone(ZoneInfo(zone)).replace(tzinfo=None)


def glob(pattern, is_path):
    if not is_path:
        return re.compile("^" + ".*".join(map(re.escape, pattern.split("*"))) + "$")
    parts = re.split(r"(\[\*\]|\*\*\.|\*)", pattern)
    tokens = {"[*]": r"\[\d+\]", "**.": r"(?:.*\.)?", "*": r"[^.\[]+"}
    return re.compile("^" + "".join(tokens.get(p, re.escape(p)) for p in parts) + "$")


def held_in_utc(route, path):
    return any(glob(r, False).match(route) and glob(p, True).match(path) for r, p in UTC_HELD_FIELDS)


def same_value(before, after, zone, utc_held=False):
    if isinstance(before, str) and isinstance(after, str) and DATE_TIME.match(before) and DATE_TIME.match(after):
        if utc_held:
            return datetime.fromisoformat(before) == datetime.fromisoformat(after)
        return datetime.fromisoformat(before) == shown_in_zone(after, zone)
    if isinstance(before, bool) or isinstance(after, bool):
        return before is after
    return before == after


def allowed(route, path):
    generic = re.sub(r"\[\d+\]", "[*]", path)
    return any(route == r and generic == p for r, p in REQUEST_TIME_FIELDS)


def summed_alike(route, before, after):
    """A HOLDING_ROUTES number summed in another lot order: equal within HOLDING_RELATIVE_TOLERANCE."""
    numbers = all(isinstance(value, (int, float)) and not isinstance(value, bool) for value in (before, after))
    return route in HOLDING_ROUTES and numbers and math.isclose(before, after, rel_tol=HOLDING_RELATIVE_TOLERANCE)


def diff_json(before, after, path, route, zone, found):
    if isinstance(before, dict) and isinstance(after, dict):
        for name in sorted(set(before) | set(after)):
            inner = f"{path}.{name}"
            if name not in before or name not in after:
                if not allowed(route, inner):
                    found.append(f"{inner}: only in {'before' if name in before else 'after'}")
            else:
                diff_json(before[name], after[name], inner, route, zone, found)
    elif isinstance(before, list) and isinstance(after, list):
        if len(before) != len(after):
            found.append(f"{path}: {len(before)} items before, {len(after)} after")
        for index, (b, a) in enumerate(zip(before, after)):
            diff_json(b, a, f"{path}[{index}]", route, zone, found)
    elif not same_value(before, after, zone, held_in_utc(route, path)) and not allowed(route, path) \
            and not summed_alike(route, before, after):
        found.append(f"{path}: before {json.dumps(before)}, after {json.dumps(after)}")


def cells(book_bytes):
    """Every cell's text, keyed 'sheet!ref', read from the workbook's XML: its other parts carry the time it was made."""
    with zipfile.ZipFile(io.BytesIO(book_bytes)) as book:
        names = book.namelist()
        shared = []
        if "xl/sharedStrings.xml" in names:
            for item in ElementTree.fromstring(book.read("xl/sharedStrings.xml")).iter(SHEET_NS + "si"):
                shared.append("".join(t.text or "" for t in item.iter(SHEET_NS + "t")))
        targets = {r.get("Id"): r.get("Target") for r in
                   ElementTree.fromstring(book.read("xl/_rels/workbook.xml.rels")).iter(PACKAGE_NS + "Relationship")}
        found = {}
        for sheet in ElementTree.fromstring(book.read("xl/workbook.xml")).iter(SHEET_NS + "sheet"):
            target = targets[sheet.get(RELATIONSHIP_NS + "id")].lstrip("/").removeprefix("xl/")
            for cell in ElementTree.fromstring(book.read("xl/" + target)).iter(SHEET_NS + "c"):
                value = cell.find(SHEET_NS + "v")
                if cell.get("t") == "s":
                    text = shared[int(value.text)]
                elif cell.get("t") == "inlineStr":
                    text = "".join(t.text or "" for t in cell.iter(SHEET_NS + "t"))
                else:
                    text = value.text if value is not None else ""
                found[f"{sheet.get('name')}!{cell.get('r')}"] = text
        return found


def is_workbook(raw):
    """An Excel answer by its bytes: FileType.java:9 serves an .xlsx as application/vnd.ms-excel."""
    if not raw.startswith(b"PK"):
        return False
    with zipfile.ZipFile(io.BytesIO(raw)) as book:
        return "xl/workbook.xml" in book.namelist()


def numeric(text):
    try:
        return float(text)
    except ValueError:
        return text


def ordered_for_comparison(route, answer):
    """A list ORDERED_FOR_COMPARISON names, sorted by its key fields, last key first, so each sort keeps the one before; a
    field absent or null sorts after every value, since a lot may lack its trade date or broker (W-422)."""
    if route not in ORDERED_FOR_COMPARISON or not isinstance(answer, list):
        return answer
    for field, descending in reversed(ORDERED_FOR_COMPARISON[route]):
        answer = sorted(answer, key=lambda item, name=field: (item.get(name) is None, item.get(name)), reverse=descending)
    return answer


def by_holding(route, answer):
    """A HOLDING_ROUTES list as {holding: its HOLDING_FIELDS}; any other answer as it is."""
    if route not in HOLDING_ROUTES or not isinstance(answer, list):
        return answer
    holdings = {}
    for item in answer:
        key = " ".join(str(item.get(field)) for field in HOLDING_KEY)
        if key in holdings:
            raise ValueError(f"{route}: two holdings answer as {key}")
        holdings[key] = {name: value for name, value in item.items() if name in HOLDING_FIELDS}
    return holdings


def diff_body(before, after, before_body, after_body, zone):
    content_type = before["contentType"]
    if "json" in content_type:
        found = []
        route = before["route"]
        diff_json(by_holding(route, ordered_for_comparison(route, json.loads(before_body or b"null"))),
                  by_holding(route, ordered_for_comparison(route, json.loads(after_body or b"null"))), "$", route, zone, found)
        return found
    if is_workbook(before_body) and is_workbook(after_body):
        b, a = cells(before_body), cells(after_body)
        return [f"{ref}: before {b.get(ref)!r}, after {a.get(ref)!r}" for ref in sorted(set(b) | set(a))
                if ref not in b or ref not in a or not same_value(numeric(b[ref]), numeric(a[ref]), zone)]
    if content_type.startswith("application/pdf"):
        return []
    return [] if before_body == after_body else [f"body differs ({len(before_body)} bytes before, {len(after_body)} after)"]


def load(folder):
    with open(os.path.join(folder, MANIFEST)) as manifest:
        saved = json.load(manifest)
    return {(a["user"], a["url"]): a for a in saved["answers"]}, saved["notReached"]


def body(folder, answer):
    with open(os.path.join(folder, answer["file"]), "rb") as saved:
        return saved.read()


def compare(before_dir, after_dir, zone=BUSINESS_ZONE):
    """Every difference between two fetches outside the allowed list, one line each naming user, URL and path."""
    before, _ = load(before_dir)
    after, _ = load(after_dir)
    found = []
    for user, url in sorted(set(before) | set(after)):
        b, a = before.get((user, url)), after.get((user, url))
        where = f"{user} | {url}"
        if b is None or a is None:
            found.append(f"{where} | read only {'before' if a is None else 'after'}")
        elif (b["status"], b["contentType"]) != (a["status"], a["contentType"]):
            found.append(f"{where} | status {b['status']} {b['contentType']} before, {a['status']} {a['contentType']} after")
        else:
            found += [f"{where} | {line}" for line in diff_body(b, a, body(before_dir, b), body(after_dir, a), zone)]
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    spec_command = commands.add_parser("spec")
    fetch_command = commands.add_parser("fetch")
    for command in (spec_command, fetch_command):
        command.add_argument("--base", required=True)
        command.add_argument("--users", required=True)
        command.add_argument("--out", required=True)
    fetch_command.add_argument("--spec", required=True)
    fetch_command.add_argument("--value", action="append", default=[], help="name=v1,v2 for a path or query parameter")
    fetch_command.add_argument("--workspaces", required=True, help="the copy's 'profileId taxYear' lines (runbook §0.10)")
    compare_command = commands.add_parser("compare")
    compare_command.add_argument("before")
    compare_command.add_argument("after")
    args = parser.parse_args()
    if args.command == "compare":
        found = compare(args.before, args.after)
        print("\n".join(found))
        for label, folder in (("before", args.before), ("after", args.after)):
            print(f"not reached {label}: {', '.join(load(folder)[1]) or 'none'}")
        print(f"{len(found)} differences outside the list")
        sys.exit(1 if found else 0)
    users = read_users(args.users)
    key = getpass.getpass("JWT_SECURITY_KEY: ")
    get = http_get(args.base.rstrip("/"))
    if args.command == "spec":
        admins = [u for u in users if SUPER_USER in u[1].split(",")]
        if not admins:
            sys.exit("No SUPER_USER in the users file: the spec is an admin route")
        status, _, spec_body = get(SPEC_PATH, mint(key, admins[0][0], admins[0][1], int(time.time())))
        if status != 200:
            sys.exit(f"The spec answered {status}")
        with open(args.out, "wb") as out:
            out.write(spec_body)
        print(f"spec saved: {len(json.loads(spec_body)['paths'])} paths")
        return
    given = {}
    for pair in args.value:
        name, _, values = pair.partition("=")
        given[name] = values.split(",")
    with open(args.spec) as spec_file:
        answers = fetch(json.load(spec_file), users, key, get, args.out, given, PARAM_SOURCES, read_workspaces(args.workspaces))
    print(f"{len(answers)} answers saved to {args.out}")


if __name__ == "__main__":
    main()
