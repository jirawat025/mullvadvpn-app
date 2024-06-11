//
//  LoggedInUITestCase.swift
//  MullvadVPNUITests
//
//  Created by Niklas Berglund on 2024-01-22.
//  Copyright © 2024 Mullvad VPN AB. All rights reserved.
//

import Foundation
import XCTest

/// Base class for tests that should start from a state of being logged on to an account with time left
class LoggedInWithTimeUITestCase: BaseUITestCase {
    var hasTimeAccount: Account?

    override func setUp() {
        super.setUp()

        hasTimeAccount = getAccountWithTime()

        agreeToTermsOfServiceIfShown()
        dismissChangeLogIfShown()
        logoutIfLoggedIn()

        guard let hasTimeAccount else {
            XCTFail("hasTimeAccount unexpectedly not set")
            return
        }

        login(accountNumber: hasTimeAccount.accountNumber)

        // Relaunch app so that tests start from a deterministic state
        app.terminate()
        app.launch()
    }
}
