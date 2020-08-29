# Revamp analytics

Without knowing how we're doing it's harder to improve. We used to have Google Analytics
in the non-F-droid version of the app, but a couple of years ago our hit rate grew so high
they disabled us. Putting a throttle on the events hasn't helped - the Analytics never
got re-enabled for some reason.

The new hotness at Google for Analytic seems to be Firebase. We should switch to that. As
always we should respect user privacy and allow users to disable it if they wish. At present
this isn't as good as it should be since they need to go to settings to do it by which time
some analytics will have been sent.

## Requirements

*   Ask users up front if they want to disable analytics
    * Ideally show them what we're collecting
*   Track which features are useful, and which we can safely make less accessible or retire
altogether
*   Track crashes
*   Get an idea of language breakdown so we can spend some money on professional translations
*   Track popular searches so we can see which objects to add
*   Get a sense of the hardware population: which devices have which OS and in particular which
sensors.

## Implementation

*   Google provides an SDK that looks pretty straightforward to use.
*   We have an existing AnalyticsInterface.java class which has most of the things we currently
log and will probably be able to hide away the change from much of the rest of the app. Some of
the things we currently explicitly log are implictly logged by firebase, so step one is just
to add the SDK and see what we get by default.
