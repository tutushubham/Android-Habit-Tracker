import XCTest

/// Smoke test of the real app on the simulator: add a habit, check it off, send the app to the background and
/// bring it back. Run on a Mac with the language set to English (the labels below are the app's English copy):
///
///     ruby scripts/ios/add_ui_test_target.rb          # once: adds this target and scheme entry
///     xcodebuild test -project iosApp/iosApp.xcodeproj -scheme iosApp \
///       -destination 'platform=iOS Simulator,name=iPhone 17 Pro' ARCHS=arm64 -only-testing:iosAppUITests
///
/// It does not assume a fresh install: the habit name is unique per run; the setup wizard and the tutorial are
/// skipped only if shown.
final class AppSmokeUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    /// Compose Multiplatform exposes its semantics as accessibility elements; find one by its label.
    private func element(_ app: XCUIApplication, label: String, contains: Bool = false) -> XCUIElement {
        let predicate = NSPredicate(format: contains ? "label CONTAINS %@" : "label == %@", label)
        return app.descendants(matching: .any).matching(predicate).firstMatch
    }

    /// Fails with the accessibility tree attached to the log: what the app exposes is the thing to look at.
    private func require(_ element: XCUIElement, _ app: XCUIApplication, _ what: String, timeout: TimeInterval = 10) {
        if !element.waitForExistence(timeout: timeout) { print("TREE[\(what)] \(app.debugDescription)") }
        XCTAssertTrue(element.exists, what)
    }

    func testAddAHabitCheckItOffAndComeBackFromTheBackground() throws {
        let app = XCUIApplication()
        app.launch()

        // A fresh install opens the setup wizard: skip it (confirming), then the coach marks if they show.
        let skipSetup = element(app, label: "Skip setup")
        if skipSetup.waitForExistence(timeout: 10) {
            skipSetup.tap()
            let confirm = app.buttons["Skip"]
            require(confirm, app, "the skip confirmation")
            confirm.tap()
        }
        let skip = element(app, label: "Skip tutorial")
        if skip.waitForExistence(timeout: 3) { skip.tap() }

        let habitName = "Smoke \(Int(Date().timeIntervalSince1970))"

        // Habits tab -> Add Daily Habit -> name and goal -> Save
        let habitsTab = element(app, label: "Habits")
        require(habitsTab, app, "the bottom bar did not appear", timeout: 15)
        habitsTab.tap()
        let add = element(app, label: "Add Daily Habit", contains: true)
        require(add, app, "Add Daily Habit button")
        add.tap()
        // Compose text fields are exposed as text views, labelled with the field's label.
        let fields = app.textViews
        require(fields.element(boundBy: 0), app, "name field")
        fields.element(boundBy: 0).tap()
        fields.element(boundBy: 0).typeText(habitName)
        fields.element(boundBy: 1).tap()
        fields.element(boundBy: 1).typeText("12")
        app.buttons["Save"].tap()
        require(element(app, label: habitName, contains: true), app, "habit listed after save")

        // Back on the tracker, today's list shows it unchecked; tap it.
        element(app, label: "Tracker").tap()
        let row = element(app, label: habitName, contains: true)
        require(row, app, "habit row on tracker")
        row.tap()
        XCTAssertEqual(element(app, label: habitName, contains: true).value as? String, "Completed")

        // Background and foreground: the state is still there.
        XCUIDevice.shared.press(.home)
        app.activate()
        let again = element(app, label: habitName, contains: true)
        require(again, app, "habit row after foregrounding", timeout: 15)
        XCTAssertEqual(again.value as? String, "Completed")
    }
}
