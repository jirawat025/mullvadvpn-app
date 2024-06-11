//
//  Account.swift
//  MullvadVPNUITests
//
//  Created by Niklas Berglund on 2024-06-10.
//  Copyright © 2024 Mullvad VPN AB. All rights reserved.
//

import Foundation
import XCTest

enum AccountType {
    case temporaryAccount
    case permanentAccount
    case unknown
}

class Account {
    var type: AccountType
    var accountNumber: String

    init(type: AccountType, accountNumber: String) {
        self.type = type
        self.accountNumber = accountNumber
    }

    /// Delete the account if it's a temporary account, otherwise do nothing
    func deleteIfTemporary() {
        if type == .temporaryAccount {
            PartnerAPIClient().deleteAccount(accountNumber: accountNumber)
        }
    }

    public static func getAccountWithTime() -> Account {
        if let configuredHasTimeAccountNumber = Bundle(for: Account.self)
            .infoDictionary?["HasTimeAccountNumber"] as? String, !configuredHasTimeAccountNumber.isEmpty {
            return Account(type: .permanentAccount, accountNumber: configuredHasTimeAccountNumber)
        } else {
            let partnerAPIClient = PartnerAPIClient()
            let accountNumber = partnerAPIClient.createAccount()
            _ = partnerAPIClient.addTime(accountNumber: accountNumber, days: 1)
            return Account(type: .temporaryAccount, accountNumber: accountNumber)
        }
    }

    public static func getAccountWithoutTime() -> Account {
        if let configuredNoTimeAccountNumber = Bundle(for: Account.self)
            .infoDictionary?["NoTimeAccountNumber"] as? String, !configuredNoTimeAccountNumber.isEmpty {
            return Account(type: .permanentAccount, accountNumber: configuredNoTimeAccountNumber)
        } else {
            do {
                let accountNumber = try MullvadAPIWrapper().createAccount()
                return Account(type: .temporaryAccount, accountNumber: accountNumber)
            } catch {
                XCTFail("Instantiate MullvadAPIWrapper")
                return Account(type: .unknown, accountNumber: "")
            }
        }
    }
}
