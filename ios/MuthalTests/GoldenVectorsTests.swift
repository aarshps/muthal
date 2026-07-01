import XCTest
@testable import Muthal

/// Parity guard: runs the shared, language-neutral vectors in
/// shared/domain/golden-vectors.json through the iOS implementations, so iOS
/// provably agrees with web + Android. See shared/domain/SPEC.md.
final class GoldenVectorsTests: XCTestCase {

    private func loadVectors() throws -> [String: Any] {
        // #file → ios/MuthalTests/GoldenVectorsTests.swift; up 3 → repo root.
        let root = URL(fileURLWithPath: #file)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let url = root.appendingPathComponent("shared/domain/golden-vectors.json")
        let data = try Data(contentsOf: url)
        return try JSONSerialization.jsonObject(with: data) as! [String: Any]
    }

    private func iso(_ s: String) -> Date {
        let f = ISO8601DateFormatter()
        return f.date(from: s)!
    }

    func testCurrencyFormat() throws {
        let cur = try loadVectors()["currency"] as! [String: Any]
        for case let o as [String: Any] in cur["format"] as! [Any] {
            let amount = (o["amount"] as! NSNumber).doubleValue
            XCTAssertEqual(Currency.format(amount, o["code"] as! String), o["expected"] as! String)
        }
    }

    func testCurrencyCompact() throws {
        let cur = try loadVectors()["currency"] as! [String: Any]
        for case let o as [String: Any] in cur["compact"] as! [Any] {
            let amount = (o["amount"] as! NSNumber).doubleValue
            XCTAssertEqual(Currency.compact(amount), o["expected"] as! String)
        }
    }

    func testCategories() throws {
        for case let o as [String: Any] in try loadVectors()["categories"] as! [Any] {
            let expected = o["expected"] as! [String]
            XCTAssertEqual(Categories.forType(o["type"] as! String), expected)
        }
    }

    func testSummary() throws {
        for case let o as [String: Any] in try loadVectors()["summary"] as! [Any] {
            let now = iso(o["now"] as! String)
            let items = (o["entries"] as! [Any]).compactMap { any -> SummaryCalc.Item? in
                guard let e = any as? [String: Any] else { return nil }
                return SummaryCalc.Item(
                    amount: (e["amount"] as! NSNumber).doubleValue,
                    type: e["type"] as! String,
                    date: iso(e["date"] as! String)
                )
            }
            let s = SummaryCalc.summarize(items, now: now)
            let exp = o["expected"] as! [String: Any]
            XCTAssertEqual(s.totalIncome, (exp["totalIncome"] as! NSNumber).doubleValue, accuracy: 0.001)
            XCTAssertEqual(s.totalExpense, (exp["totalExpense"] as! NSNumber).doubleValue, accuracy: 0.001)
            XCTAssertEqual(s.balance, (exp["balance"] as! NSNumber).doubleValue, accuracy: 0.001)
            XCTAssertEqual(s.monthIncome, (exp["monthIncome"] as! NSNumber).doubleValue, accuracy: 0.001)
            XCTAssertEqual(s.monthExpense, (exp["monthExpense"] as! NSNumber).doubleValue, accuracy: 0.001)
            XCTAssertEqual(s.monthBalance, (exp["monthBalance"] as! NSNumber).doubleValue, accuracy: 0.001)
        }
    }
}
