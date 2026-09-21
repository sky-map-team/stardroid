# Room, Hilt, Compose, Coil, kotlinx.coroutines, and the Firebase SDKs (gms flavor) all ship
# their own consumer ProGuard rules, so no project-specific keep rules are needed for them.

# Glance keys its persisted receiver map by GlanceAppWidget.javaClass.canonicalName, and
# updateAll() resolves a widget's instances through that map. Nothing else references our
# widget classes by name, so R8 horizontally merges Moon/Tonight/Countdown into one class:
# they then share a name, MoonWidget().updateAll() reaches every placed widget, and they all
# render as the moon until the launcher re-requests each one (e.g. on reboot).
# Keeping the classes stops both the merge and the renaming.
-keep class * extends androidx.glance.appwidget.GlanceAppWidget
