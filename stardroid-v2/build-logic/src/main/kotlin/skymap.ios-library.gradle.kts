// Convention for iOS-only Kotlin modules — platform code with no Android or JVM counterpart, such
// as the Metal backend (:render:metal, D117). Just skymap.kmp-base: the iOS targets, the
// commonTest stack and the Xcode rule, with no JVM target. Its Kotlin still sees nothing of
// Android; what it adds over a pure module is the Apple platform libraries (Metal, Foundation).
plugins {
    id("skymap.kmp-base")
}
