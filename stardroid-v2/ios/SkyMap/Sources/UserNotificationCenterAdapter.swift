/*
 * Copyright (c) 2026 Penterakt LLC.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

import SkyMapKit
import UserNotifications

/// Local notifications over `UNUserNotificationCenter`. Kotlin plans the next nights and renders
/// the words; this only hands them to the system, which delivers each at its time with no code of
/// ours running. See docs/design/ios-platform-surfaces.md section 5.
final class UserNotificationCenterAdapter: NSObject, LocalNotificationCenter,
    UNUserNotificationCenterDelegate
{
    private let center = UNUserNotificationCenter.current()

    override init() {
        super.init()
        // Before the app finishes launching, so the tap that launched it is delivered.
        center.delegate = self
    }

    /// A tap on a notification: the object it carries opens as a card, no object just the map.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let objectId = response.notification.request.content.userInfo["objectId"] as? String
        DispatchQueue.main.async {
            DeepLinksKt.openObjectFromNotification(objectId: objectId)
        }
        completionHandler()
    }

    func replaceAll(notifications: [LocalNotification], onDone: @escaping () -> Void) {
        center.removeAllPendingNotificationRequests()
        let group = DispatchGroup()
        for notification in notifications {
            // An absolute instant, so a time zone change cannot move a pass alert.
            let seconds = Date(timeIntervalSince1970: TimeInterval(notification.fireAtMillis) / 1000)
                .timeIntervalSinceNow
            guard seconds > 1 else { continue }
            let content = UNMutableNotificationContent()
            content.title = notification.title
            content.body = notification.body
            content.sound = .default
            content.threadIdentifier = notification.threadId
            if let objectId = notification.objectId {
                content.userInfo = ["objectId": objectId]
            }
            let request = UNNotificationRequest(
                identifier: notification.id,
                content: content,
                trigger: UNTimeIntervalNotificationTrigger(timeInterval: seconds, repeats: false))
            group.enter()
            center.add(request) { _ in group.leave() }
        }
        group.notify(queue: .main) { onDone() }
    }

    func authorization(onResult: @escaping (NotificationAuthorization) -> Void) {
        center.getNotificationSettings { settings in
            switch settings.authorizationStatus {
            case .authorized, .provisional, .ephemeral: onResult(.authorized)
            case .denied: onResult(.denied)
            default: onResult(.notDetermined)
            }
        }
    }
}
