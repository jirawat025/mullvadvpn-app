//
//  Account.swift
//  MullvadVPNUITests
//
//  Created by Niklas Berglund on 2024-06-10.
//  Copyright © 2024 Mullvad VPN AB. All rights reserved.
//

import Foundation

enum AccountType {
    case temporaryAccount
    case permanentAccount
}

struct Account {
    var type: AccountType
    var accountNumber: String

    func deleteIfTemporary() {
        PartnerAPIClient().deleteAccount(accountNumber: accountNumber)
    }

    public static func getAccountWithTime() -> Account {
        return createAccountWithTime()
    }

    private static func createAccountWithTime() -> Account {
        let accountNumber = PartnerAPIClient().createAccountWithTime()
        return Account(type: .temporaryAccount, accountNumber: accountNumber)
    }

    private static func createAccountWithoutTime() -> Account {
        let accountNumber = PartnerAPIClient().createAccountWithoutTime()
        return Account(type: .temporaryAccount, accountNumber: accountNumber)
    }
}
