import XCTest

/// Smoke test of the real app on the simulator: add a habit, check it off, send the app to the background and
/// bring it back. Run on a Mac with the language set to English (the labels below are the app's English copy):
///
///     ruby scripts/ios/add_ui_test_target.rb          # once: adds this target and scheme entry
///     xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
///       -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:iosAppUITests
///
/// It does not assume a fresh install: the habit name is unique per run and the tutorial is skipped only if shown.
final class AppSmokeUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// Compose Multiplatform exposes its semantics as accessibility elements; find one by its label.
    private func element(_ app: XCUIApplication, label: String, contains: Bool = false) -> XCUIElement {
        let predicate = NSPredicate(format: contains ? "label CONTAINS %@" : "label == %@", label)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    func testAddAHabitCheckItOffAndComeBackFromTheBackground() throws {
        let app = XCUIApplication()
        app.launch()

        let skip = element(app, label: "Skip tutorial")
        if skip.waitForExistence(timeout: 5) { skip.tap() }

        let habitName = "Smoke \(Int(Date().timeIntervalSince1970))"

        // Habits tab -> Add Daily Habit -> name and goal -> Save
        let habitsTab = element(app, label: "Habits")
        XCTAssertTrue(habitsTab.waitForExistence(timeout: 15), "the bottom bar did not appear")
        habitsTab.tap()
        let add = element(app, label: "Add Daily Habit", contains: true)
        XCTAssertTrue(add.waitForExistence(timeout: 10))
        add.tap()
        let fields = app.textFields
        XCTAssertTrue(fields.element(boundBy: 0).waitForExistence(timeout: 10))
        fields.element(boundBy: 0).tap()
        fields.element(boundBy: 0).typeText(habitName)
        fields.element(boundBy: 1).tap()
        fields.element(boundBy: 1).typeText("12")
        app.buttons["Save"].tap()
        XCTAssertTrue(element(app, label: habitName, contains: true).waitForExistence(timeout: 10))

        // Back on the tracker, today's list shows it unchecked; tap it.
        element(app, label: "Tracker").tap()
        let row = element(app, label: habitName, contains: true)
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        row.tap()
        XCTAssertEqual(element(app, label: habitName, contains: true).value as? String, "Completed")

        // Background and foreground: the state is still there.
        XCUIDevice.shared.press(.home)
        app.activate()
        let again = element(app, label: habitName, contains: true)
        XCTAssertTrue(again.waitForExistence(timeout: 15))
        XCTAssertEqual(again.value as? String, "Completed")
    }
}
