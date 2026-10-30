/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import SkyMapKit
import StoreKit

/// The Support screen's tip jar over StoreKit 2: three consumable products, bought any number
/// of times. Kotlin cannot call StoreKit 2 (Swift-only), so this implements its `TipStore`.
final class StoreKitTipStore: NSObject, TipStore {
    private static let productIds = ["tip.mercury", "tip.mars", "tip.jupiter"]

    private var updates: Task<Void, Never>?

    override init() {
        super.init()
        // A purchase that finished outside the app (Ask to Buy approved later, or an interrupted
        // one) arrives here; a consumable must be finished or StoreKit keeps redelivering it.
        updates = Task {
            for await update in Transaction.updates {
                if case .verified(let transaction) = update {
                    await transaction.finish()
                }
            }
        }
    }

    deinit {
        updates?.cancel()
    }

    func loadTips(onLoaded: @escaping ([TipOption]) -> Void) {
        Task { @MainActor in
            let products = (try? await Product.products(for: Self.productIds)) ?? []
            onLoaded(
                products
                    .sorted { $0.price < $1.price }
                    .map { TipOption(id: $0.id, displayPrice: $0.displayPrice) })
        }
    }

    func purchase(id: String, onResult: @escaping (TipResult) -> Void) {
        Task { @MainActor in
            guard let product = try? await Product.products(for: [id]).first else {
                onResult(.failed)
                return
            }
            do {
                switch try await product.purchase() {
                case .success(.verified(let transaction)):
                    await transaction.finish()
                    onResult(.thanks)
                case .success(.unverified):
                    onResult(.failed)
                case .pending:
                    onResult(.pending)
                case .userCancelled:
                    onResult(.cancelled)
                @unknown default:
                    onResult(.failed)
                }
            } catch {
                onResult(.failed)
            }
        }
    }
}
