"""Tests for read_comparison.py (W-406, D-981). Run: python3 -I -B docs/taxplanning-build/tools/read_comparison_test.py"""
import base64
import hashlib
import hmac
import io
import json
import os
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import read_comparison as rc  # noqa: E402

KEY = base64.b64encode(b"k" * 32).decode()


def save(folder, answers):
    """answers: list of (user, route, url, status, content_type, body bytes)."""
    entries = []
    for number, (user, route, url, status, content_type, body) in enumerate(answers):
        name = f"{number:05d}.body"
        with open(os.path.join(folder, name), "wb") as out:
            out.write(body)
        entries.append({"user": user, "route": route, "url": url, "status": status, "contentType": content_type, "file": name})
    with open(os.path.join(folder, rc.MANIFEST), "w") as out:
        json.dump({"answers": entries, "notReached": []}, out)


def as_json(value):
    return json.dumps(value).encode()


def xlsx(cells):
    """A minimal workbook: one sheet, each (ref, text) an inline string cell."""
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as book:
        book.writestr("xl/workbook.xml", '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
                      'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
                      '<sheets><sheet name="Held" sheetId="1" r:id="rId1"/></sheets></workbook>')
        book.writestr("xl/_rels/workbook.xml.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                      '<Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>')
        row = "".join(f'<c r="{ref}" t="inlineStr"><is><t>{text}</t></is></c>' for ref, text in cells)
        book.writestr("xl/worksheets/sheet1.xml", '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
                      f'<sheetData><row r="1">{row}</row></sheetData></worksheet>')
        book.writestr("docProps/core.xml", "<created>" + str(len(cells)) + "</created>")
    return buffer.getvalue()


class CompareTest(unittest.TestCase):

    def differences(self, before, after):
        with tempfile.TemporaryDirectory() as a, tempfile.TemporaryDirectory() as b:
            save(a, before)
            save(b, after)
            return rc.compare(a, b, "Asia/Kolkata")

    def test_compare_whenIdentical_noDifference(self):
        # Given
        answer = [("u@x", "/r", "/r", 200, "application/json", as_json({"total": 10.5, "rows": [1, 2]}))]
        # When
        found = self.differences(answer, answer)
        # Then
        self.assertEqual([], found)

    def test_compare_whenAmountChanged_namesUserRouteAndPath(self):
        # Given
        before = [("u@x", "/r", "/r?page=0", 200, "application/json", as_json({"rows": [{"amount": 10.5}]}))]
        after = [("u@x", "/r", "/r?page=0", 200, "application/json", as_json({"rows": [{"amount": 10.6}]}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))
        self.assertIn("u@x", found[0])
        self.assertIn("/r?page=0", found[0])
        self.assertIn("$.rows[0].amount", found[0])

    def test_compare_whenTimeShownInKolkataBeforeAndUtcAfter_allowed(self):
        # Given: 04:00 UTC is 09:30 in Asia/Kolkata (UTC+05:30, IANA tz database)
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2025-06-01T09:30:00"}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2025-06-01T04:00:00"}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenTimeShiftedTheWrongWay_difference(self):
        # Given: the same 05:30 apart, but after is the later one
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2025-06-01T04:00:00"}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2025-06-01T09:30:00"}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))

    def test_compare_whenTimesEqual_difference(self):
        # Given: unchanged digits mean the copier did not move the time to UTC
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2025-06-01T09:30:00.123"}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2025-06-01T09:30:00.123"}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))

    def test_compare_whenUtcHeldTimeEqual_allowed(self):
        # Given: m1.6.1 wrote computedOn in UTC (UserChargeService.java:157), so both builds show the same digits
        before = [("u@x", "/api/v1/me/charges/user-charges", "/u", 200, "application/json",
                   as_json({"content": [{"computedOn": "2026-11-27T02:51:39.346"}]}))]
        after = [("u@x", "/api/v1/me/charges/user-charges", "/u", 200, "application/json",
                  as_json({"content": [{"computedOn": "2026-11-27T02:51:39.346"}]}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenUtcHeldTimeShifted_difference(self):
        # Given: a UTC-held time moved by the zone means the copier shifted what it must not
        before = [("u@x", "/api/v1/me/charges/user-charges", "/u", 200, "application/json",
                   as_json({"content": [{"computedOn": "2026-11-27T08:21:39.346"}]}))]
        after = [("u@x", "/api/v1/me/charges/user-charges", "/u", 200, "application/json",
                  as_json({"content": [{"computedOn": "2026-11-27T02:51:39.346"}]}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))

    def test_compare_whenHoldingOrderTimeEqual_allowed(self):
        # Given: m1.6.1 kept each lot's order time in UTC (PortfolioService.java:445), so both builds show the same digits
        lot = {"orderTimeQuantities": [{"orderExecutionTime": "2025-06-30T08:13:34", "quantity": 1.0}]}
        before = [("u@x", "/api/v1/me/portfolio/stocks/{stockCode}", "/s/INFY", 200, "application/json", as_json([lot]))]
        after = [("u@x", "/api/v1/me/portfolio/stocks/{stockCode}", "/s/INFY", 200, "application/json", as_json([lot]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenCardsSharingAStartDateInAnotherOrder_allowed(self):
        # Given: W-411 orders the tie by scheduleCode; m1.6.1 gave it in stored order
        cards = [{"startDate": "2025-04-01", "scheduleCode": "B"}, {"startDate": "2025-04-01", "scheduleCode": "A"},
                 {"startDate": "2024-04-01", "scheduleCode": "C"}]
        before = [("u@x", "/api/v1/me/charges/charge-schedules", "/c", 200, "application/json", as_json(cards))]
        after = [("u@x", "/api/v1/me/charges/charge-schedules", "/c", 200, "application/json",
                  as_json([cards[1], cards[0], cards[2]]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenAllocationRowsInAnotherOrder_allowed(self):
        # Given: both builds iterate a HashMap keyed by the enum, so the rows' order changes with each start
        equity = {"assetType": "EQUITY", "investedValue": 209095.98}
        funds = {"assetType": "MUTUAL_FUND", "investedValue": 261467.99}
        route = "/api/v1/me/portfolio/analytics/asset-allocation"
        before = [("u@x", route, "/a", 200, "application/json", as_json([equity, funds]))]
        after = [("u@x", route, "/a", 200, "application/json", as_json([funds, equity]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenCardsDifferBeyondTheirOrder_difference(self):
        # Given: ordering both sides hides no changed value
        before = [("u@x", "/api/v1/me/charges/charge-schedules", "/c", 200, "application/json",
                   as_json([{"startDate": "2025-04-01", "scheduleCode": "A", "rate": 0.03}]))]
        after = [("u@x", "/api/v1/me/charges/charge-schedules", "/c", 200, "application/json",
                  as_json([{"startDate": "2025-04-01", "scheduleCode": "A", "rate": 0.3}]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(["u@x | /c | $[0].rate: before 0.03, after 0.3"], found)

    def test_compare_whenHoldingsInAnotherOrderWithAnotherFirstLot_allowed(self):
        # Given: m1.6.1's first lot was MongoDB's stored order; the w-337 build reads the oldest first
        infy = {"stockCode": "INFY", "brokerName": "ZERODHA", "accountHolder": None, "quantity": 3.0, "exchangeName": "NSE",
                "orderId": "1", "buyTransactionIds": ["t1"]}
        tcs = {"stockCode": "TCS", "brokerName": "ZERODHA", "accountHolder": None, "quantity": 2.0, "exchangeName": "BSE"}
        infy_other_lot = dict(infy, exchangeName="BSE", orderId="2", buyTransactionIds=["t2"])
        before = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json", as_json([infy, tcs]))]
        after = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json", as_json([tcs, infy_other_lot]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenAHoldingsSummedFigureChanged_difference(self):
        # Given: only the first lot's own fields are left out; a total must still match
        before = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json",
                   as_json([{"stockCode": "INFY", "brokerName": "ZERODHA", "accountHolder": None, "quantity": 3.0}]))]
        after = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json",
                  as_json([{"stockCode": "INFY", "brokerName": "ZERODHA", "accountHolder": None, "quantity": 4.0}]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(["u@x | /s | $.INFY ZERODHA None.quantity: before 3.0, after 4.0"], found)

    def test_compare_whenAHoldingIsMissingOnOneSide_difference(self):
        # Given
        infy = {"stockCode": "INFY", "brokerName": "ZERODHA", "accountHolder": None, "quantity": 3.0}
        before = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json", as_json([infy]))]
        after = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json", as_json([]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(["u@x | /s | $.INFY ZERODHA None: only in before"], found)

    def test_compare_whenAHoldingsSumDiffersOnlyInItsLastDigits_allowed(self):
        # Given: 518.02 summed lot by lot in another order (W-419 R3-C1a)
        before = [("u@x", "/api/v1/me/portfolio/mutual-funds", "/m", 200, "application/json",
                   as_json([{"stockCode": "F", "brokerName": "UPSTOX", "accountHolder": None, "quantity": 518.02}]))]
        after = [("u@x", "/api/v1/me/portfolio/mutual-funds", "/m", 200, "application/json",
                  as_json([{"stockCode": "F", "brokerName": "UPSTOX", "accountHolder": None, "quantity": 518.0200000000001}]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenAHoldingsFirstLotCorporateActionDiffers_allowed(self):
        # Given: the view copies corporateActionType from whichever lot it reads first
        before = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json",
                   as_json([{"stockCode": "R", "brokerName": "FYERS", "accountHolder": None, "corporateActionType": "BONUS"}]))]
        after = [("u@x", "/api/v1/me/portfolio/stocks", "/s", 200, "application/json",
                  as_json([{"stockCode": "R", "brokerName": "FYERS", "accountHolder": None, "corporateActionType": None}]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenLastDigitsDifferOutsideTheHoldingsLists_difference(self):
        # Given: the tolerance is the holdings lists' alone
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({"quantity": 518.02}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({"quantity": 518.0200000000001}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))

    def test_compare_whenListReordered_difference(self):
        # Given
        before = [("u@x", "/r", "/r", 200, "application/json", as_json([{"id": "a"}, {"id": "b"}]))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json([{"id": "b"}, {"id": "a"}]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(2, len(found))

    def test_compare_whenFieldMissingOnOneSide_difference(self):
        # Given
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({"a": 1, "b": None}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({"a": 1}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))
        self.assertIn("$.b", found[0])

    def test_compare_whenStatusDiffers_difference(self):
        # Given
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({}))]
        after = [("u@x", "/r", "/r", 500, "application/problem+json", as_json({}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))
        self.assertIn("200", found[0])

    def test_compare_whenAnswerOnlyOnOneSide_difference(self):
        # Given
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({})),
                  ("u@x", "/s", "/s", 200, "application/json", as_json({}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))
        self.assertIn("/s", found[0])

    def test_compare_whenExcelCellChanged_difference(self):
        # Given
        type_ = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        before = [("u@x", "/x", "/x", 200, type_, xlsx([("A1", "INFY"), ("B1", "12")]))]
        after = [("u@x", "/x", "/x", 200, type_, xlsx([("A1", "INFY"), ("B1", "13")]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))
        self.assertIn("Held!B1", found[0])

    def test_compare_whenWorkbookServedAsMsExcel_comparedByCell(self):
        # Given: FileType.java:9 serves an .xlsx as application/vnd.ms-excel
        before = [("(public)", "/t", "/t", 200, "application/vnd.ms-excel", xlsx([("A1", "a")]))]
        after = [("(public)", "/t", "/t", 200, "application/vnd.ms-excel", xlsx([("A1", "b")]))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))
        self.assertIn("Held!A1", found[0])

    def test_compare_whenSpaceSeparatedTimeShownInKolkataBefore_allowed(self):
        # Given: the P&L's lastUpdatedTime is written "yyyy-MM-dd HH:mm:ss"
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2026-11-27 08:21:39"}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({"at": "2026-11-27 02:51:39"}))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenExcelCellsEqualButFileDiffers_noDifference(self):
        # Given: the workbooks differ only outside their cells
        type_ = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        before = [("u@x", "/x", "/x", 200, type_, xlsx([("A1", "INFY")]) + b"")]
        after_book = xlsx([("A1", "INFY")])
        after = [("u@x", "/x", "/x", 200, type_, after_book.replace(b"<created>1</created>", b"<created>9</created>"))]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenPdfBytesDiffer_noDifference(self):
        # Given: a PDF carries its own creation time, so only status and type are compared (C3a (3))
        before = [("u@x", "/p", "/p", 200, "application/pdf", b"%PDF-1 a")]
        after = [("u@x", "/p", "/p", 200, "application/pdf", b"%PDF-1 b")]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual([], found)

    def test_compare_whenOtherBytesDiffer_difference(self):
        # Given
        before = [("(public)", "/privacy", "/privacy", 200, "text/html", b"<p>a</p>")]
        after = [("(public)", "/privacy", "/privacy", 200, "text/html", b"<p>b</p>")]
        # When
        found = self.differences(before, after)
        # Then
        self.assertEqual(1, len(found))

    def test_compare_whenRequestTimeFieldNamed_allowed(self):
        # Given
        before = [("u@x", "/r", "/r", 200, "application/json", as_json({"rows": [{"at": "x"}]}))]
        after = [("u@x", "/r", "/r", 200, "application/json", as_json({"rows": [{"at": "y"}]}))]
        saved = rc.REQUEST_TIME_FIELDS
        rc.REQUEST_TIME_FIELDS = (("/r", "$.rows[*].at"),)
        try:
            # When
            found = self.differences(before, after)
        finally:
            rc.REQUEST_TIME_FIELDS = saved
        # Then
        self.assertEqual([], found)


class TokenTest(unittest.TestCase):

    def test_mint_whenCalled_signsTheClaimsAccessTokensIssues(self):
        # Given
        now = 1_764_000_000
        # When
        token = rc.mint(KEY, "u@x", "USER,SUPER_USER", now)
        # Then
        header, payload, signature = token.split(".")
        expected = hmac.new(base64.b64decode(KEY), f"{header}.{payload}".encode(), hashlib.sha256).digest()
        self.assertEqual(rc.b64url(expected), signature)
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        self.assertEqual({"sub": "u@x", "iat": now, "exp": now + 900,
                          "authorities": ["ROLE_USER", "ROLE_SUPER_USER", "FACTOR_PASSWORD"]}, claims)
        self.assertEqual({"alg": "HS256"}, json.loads(base64.urlsafe_b64decode(header + "=" * (-len(header) % 4))))


SPEC = {"paths": {
    "/api/v1/me/tax/profiles": {"get": {}},
    "/api/v1/me/tax/profiles/{profileId}": {"get": {"parameters": [{"name": "profileId", "in": "path", "required": True}]}},
    "/api/v1/me/tax/drafts/{draftId}": {"get": {"parameters": [{"name": "draftId", "in": "path", "required": True}]}},
    "/api/v1/me/portfolio/transactions": {"get": {"parameters": [{"name": "page", "in": "query"}, {"name": "size", "in": "query"}]},
                                          "post": {}},
    "/api/v1/me/portfolio/profit-and-loss": {"get": {"parameters": [{"name": "financialYear", "in": "query", "required": True}]}},
    "/api/v1/admin/users/{email}/stocks": {"get": {"parameters": [{"name": "email", "in": "path", "required": True}]}},
    "/api/v1/public/privacy/rights": {"get": {}},
}}


class FetchTest(unittest.TestCase):

    def test_fetch_whenRun_readsEveryRouteFillingIdsAndPages(self):
        # Given
        asked = []

        def get(url, token):
            asked.append((url, token is not None))
            if url == "/api/v1/me/tax/profiles":
                return 200, "application/json", as_json([{"id": "p1"}, {"id": "p2"}])
            if url.startswith("/api/v1/me/portfolio/transactions"):
                number = int(url.split("page=")[1].split("&")[0])
                return 200, "application/json", as_json({"content": [number], "page": {"totalPages": 2}})
            return 200, "application/json", as_json({})
        users = [("u@x", "USER"), ("admin@x", "USER,SUPER_USER")]
        with tempfile.TemporaryDirectory() as out:
            # When
            rc.fetch(SPEC, users, KEY, get, out, {"financialYear": ["2025-2026"]}, {"profileId": [("/api/v1/me/tax/profiles", "id")]})
            with open(os.path.join(out, rc.MANIFEST)) as manifest:
                saved = json.load(manifest)
        # Then
        urls = sorted((a["user"], a["url"]) for a in saved["answers"])
        self.assertIn(("u@x", "/api/v1/me/tax/profiles/p1"), urls)
        self.assertIn(("u@x", "/api/v1/me/tax/profiles/p2"), urls)
        self.assertIn(("u@x", "/api/v1/me/portfolio/transactions?page=1&size=200"), urls)
        self.assertIn(("u@x", "/api/v1/me/portfolio/profit-and-loss?financialYear=2025-2026"), urls)
        self.assertIn(("(admin)", "/api/v1/admin/users/u%40x/stocks"), urls)
        self.assertIn(("(public)", "/api/v1/public/privacy/rights"), urls)
        self.assertNotIn(("admin@x", "/api/v1/public/privacy/rights"), urls)
        self.assertIn("/api/v1/me/tax/drafts/{draftId}", " ".join(saved["notReached"]))
        self.assertIn(("/api/v1/public/privacy/rights", False), asked)
        self.assertNotIn("post", json.dumps(saved["answers"]).lower().replace("/api", ""))

    def test_fetch_whenWorkspaceRoute_readsOnlyTheStoredYearsOfEachProfile(self):
        # Given: reading a workspace that does not exist creates it (DraftService.java:55 - 58, 80), so only stored ones are read
        spec = {"paths": {
            "/api/v1/me/tax/profiles": {"get": {}},
            "/api/v1/me/tax/profiles/{profileId}/workspaces/{taxYear}": {"get": {}},
            "/api/v1/me/tax/profiles/{profileId}/capital-gains/{taxYear}": {"get": {}},
        }}

        def get(url, token):
            if url == "/api/v1/me/tax/profiles":
                return 200, "application/json", as_json([{"id": "p1"}, {"id": "p2"}])
            return 200, "application/json", as_json({})
        with tempfile.TemporaryDirectory() as out:
            # When
            rc.fetch(spec, [("u@x", "USER")], KEY, get, out, {"taxYear": ["2025", "2026"]},
                     {"profileId": [("/api/v1/me/tax/profiles", "id")]}, {("p1", "2026")})
            with open(os.path.join(out, rc.MANIFEST)) as manifest:
                urls = sorted(a["url"] for a in json.load(manifest)["answers"])
        # Then
        workspaces = [u for u in urls if "/workspaces/" in u]
        self.assertEqual(["/api/v1/me/tax/profiles/p1/workspaces/2026"], workspaces)
        self.assertEqual(4, len([u for u in urls if "/capital-gains/" in u]))

    def test_fetch_whenSourceRouteNotInSpec_refusesBeforeAnyRead(self):
        # Given
        asked = []
        sources = {"profileId": [("/api/v1/me/tax/missing", "id")]}
        with tempfile.TemporaryDirectory() as out:
            # When / Then
            with self.assertRaises(SystemExit):
                rc.fetch(SPEC, [("u@x", "USER")], KEY, lambda url, token: asked.append(url), out, {}, sources)
        self.assertEqual([], asked)


if __name__ == "__main__":
    unittest.main()
