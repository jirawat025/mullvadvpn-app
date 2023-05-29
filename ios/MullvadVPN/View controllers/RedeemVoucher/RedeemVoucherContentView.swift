//
//  RedeemVoucherContentView.swift
//  MullvadVPN
//
//  Created by Andreas Lif on 2022-08-05.
//  Copyright © 2022 Mullvad VPN AB. All rights reserved.
//

import MullvadREST
import UIKit

enum RedeemVoucherState {
    case initial
    case success
    case verifying
    case failure(Error)
}

private extension UIMetrics {
    enum RedeemVoucher {
        static let cornerRadius = 8.0
    }
}

final class RedeemVoucherContentView: UIView {
    var state: RedeemVoucherState = .initial {
        didSet {
            updateUI()
        }
    }

    private let titleLabel: UILabel = {
        let label = UILabel()
        label.font = UIFont.systemFont(ofSize: 17)
        label.text = NSLocalizedString(
            "REDEEM_VOUCHER_INSTRUCTION",
            tableName: "RedeemVoucher",
            value: "Enter voucher code",
            comment: ""
        )
        label.textColor = .white
        label.translatesAutoresizingMaskIntoConstraints = false
        label.numberOfLines = 0
        return label
    }()

    let textField: VoucherTextField = {
        let textField = VoucherTextField()
        textField.font = UIFont.monospacedSystemFont(ofSize: 20, weight: .regular)
        textField.translatesAutoresizingMaskIntoConstraints = false
        textField.placeholder = "XXXX-XXXX-XXXX-XXXX"
        textField.placeholderTextColor = .lightGray
        textField.backgroundColor = .white
        textField.cornerRadius = UIMetrics.RedeemVoucher.cornerRadius
        textField.keyboardType = .asciiCapable
        textField.autocapitalizationType = .allCharacters
        textField.returnKeyType = .done

        return textField
    }()

    let activityIndicator: SpinnerActivityIndicatorView = {
        let activityIndicator = SpinnerActivityIndicatorView(style: .medium)
        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.tintColor = .white
        return activityIndicator
    }()

    let statusLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.font = UIFont.systemFont(ofSize: 17)
        label.textColor = .white
        label.numberOfLines = .zero
        label.lineBreakMode = .byWordWrapping
        if #available(iOS 14.0, *) {
            // See: https://stackoverflow.com/q/46200027/351305
            label.lineBreakStrategy = []
        }
        return label
    }()

    let redeemButton: AppButton = {
        let button = AppButton(style: .success)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.setTitle(NSLocalizedString(
            "REDEEM_VOUCHER_REDEEM_BUTTON",
            tableName: "RedeemVoucher",
            value: "Redeem",
            comment: ""
        ), for: .normal)
        return button
    }()

    let cancelButton: AppButton = {
        let button = AppButton(style: .default)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.setTitle(NSLocalizedString(
            "REDEEM_VOUCHER_CANCEL_BUTTON",
            tableName: "RedeemVoucher",
            value: "Cancel",
            comment: ""
        ), for: .normal)
        return button
    }()

    private lazy var statusStack: UIStackView = {
        let stackView = UIStackView(arrangedSubviews: [activityIndicator, statusLabel])
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .horizontal
        stackView.spacing = UIMetrics.interButtonSpacing
        return stackView
    }()

    private lazy var topStackView: UIStackView = {
        let stackView = UIStackView(arrangedSubviews: [
            titleLabel,
            textField,
            statusStack,
        ])
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.spacing = UIMetrics.interButtonSpacing
        return stackView
    }()

    private lazy var bottomStackView: UIStackView = {
        let stackView = UIStackView(arrangedSubviews: [redeemButton, cancelButton])
        stackView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.spacing = UIMetrics.interButtonSpacing
        return stackView
    }()

    private var text: String? {
        switch state {
        case let .failure(error):
            guard let restError = error as? REST.Error else {
                return error.localizedDescription
            }

            if restError.compareErrorCode(.invalidVoucher) {
                return NSLocalizedString(
                    "REDEEM_VOUCHER_STATUS_FAILURE",
                    tableName: "RedeemVoucher",
                    value: "Voucher code is invalid.",
                    comment: ""
                )
            } else if restError.compareErrorCode(.usedVoucher) {
                return NSLocalizedString(
                    "REDEEM_VOUCHER_STATUS_FAILURE",
                    tableName: "RedeemVoucher",
                    value: "This voucher code has already been used.",
                    comment: ""
                )
            }
            return restError.displayErrorDescription ?? ""
        case .verifying:
            return NSLocalizedString(
                "REDEEM_VOUCHER_STATUS_WAITING",
                tableName: "RedeemVoucher",
                value: "Verifying voucher...",
                comment: ""
            )
        default: return nil
        }
    }

    private var isEnabledRedeemButton: Bool {
        switch state {
        case .initial, .failure:
            return true
        case .success, .verifying:
            return false
        }
    }

    private var textColor: UIColor {
        switch state {
        case .failure:
            return .dangerColor
        default:
            return .white
        }
    }

    private var isLoading: Bool {
        switch state {
        case .verifying:
            return true
        default:
            return false
        }
    }

    var redeemAction: ((String) -> Void)?
    var cancelAction: (() -> Void)?

    init() {
        super.init(frame: .zero)
        setup()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setup() {
        setupAppearance()
        configureUI()
        addButtonHandlers()
        addTextFieldObserver()
        updateUI()
    }

    private func configureUI() {
        addSubview(topStackView)
        addSubview(bottomStackView)
        addConstraints()
    }

    private func setupAppearance() {
        translatesAutoresizingMaskIntoConstraints = false
        backgroundColor = .secondaryColor
        directionalLayoutMargins = UIMetrics.contentLayoutMargins
    }

    private func addConstraints() {
        NSLayoutConstraint.activate([
            topStackView.topAnchor.constraint(equalTo: layoutMarginsGuide.topAnchor),
            topStackView.leadingAnchor.constraint(equalTo: layoutMarginsGuide.leadingAnchor),
            topStackView.trailingAnchor.constraint(equalTo: layoutMarginsGuide.trailingAnchor),

            bottomStackView.topAnchor.constraint(
                greaterThanOrEqualTo: topStackView.bottomAnchor,
                constant: UIMetrics.interButtonSpacing
            ),
            bottomStackView.leadingAnchor.constraint(equalTo: layoutMarginsGuide.leadingAnchor),
            bottomStackView.trailingAnchor.constraint(equalTo: layoutMarginsGuide.trailingAnchor),
            bottomStackView.bottomAnchor.constraint(equalTo: layoutMarginsGuide.bottomAnchor),
        ])
    }

    private func addTextFieldObserver() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(textDidChange),
            name: UITextField.textDidChangeNotification,
            object: textField
        )
    }

    private func addButtonHandlers() {
        cancelButton.addTarget(
            self,
            action: #selector(cancelButtonTapped),
            for: .touchUpInside
        )

        redeemButton.addTarget(
            self,
            action: #selector(redeemButtonTapped),
            for: .touchUpInside
        )
    }

    private func updateUI() {
        isLoading ? activityIndicator.startAnimating() : activityIndicator.stopAnimating()
        redeemButton.isEnabled = isEnabledRedeemButton && textField.satisfiesVoucherLengthRequirement
        statusLabel.text = text
        statusLabel.textColor = textColor
    }

    @objc private func cancelButtonTapped(_ sender: AppButton) {
        cancelAction?()
    }

    @objc private func redeemButtonTapped(_ sender: AppButton) {
        guard let code = textField.text, !code.isEmpty else {
            return
        }
        redeemAction?(code)
    }

    @objc private func textDidChange() {
        updateUI()
    }
}
