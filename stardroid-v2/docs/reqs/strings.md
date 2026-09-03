# Rethinking Strings
The current design for handling strings seems cumbersome and complex.
Relevant docs: stardroid-v2/docs/design/data-layer.md stardroid-v2/docs/design/data-packs.md stardroid-v2/docs/design/localization.md

## Requirements
*  The files should be easy to understand and maintain by both humans and machine translation.
*  As much should be in Android's native .xml format as possible.
*  Ease of transformation to platforms other than Android should be a factor.
*  The current design (see above) asserted that bundling ALL locales for all users was not a significant size cost. This assumption should be tested.
*  The solution needs to be compatible with a future datapack solution such as stardroid-v2/docs/design/data-packs.md
*  Astronomical objects need to have a primary name that we show as labels. In some cases these primary names will be locale-specific.
*  Astronomical objects will often also have multiple secondary names which are used in the search index and are available as alternative names on the info card. These can also be locale-specific and there might be more alternative names in one locale than another.
*  Astronomical objects might also have universal names which are the same in any language - think about M31 etc. These should never be translated. They may or may not be primary.
*  Not all strings (including names) need their own translation. For example, en-US and en-GB have the same word for "Moon". So we should follow the usual fallback rules - if there is no translation available in en-GB use the one from en-US.
*  en-US is the root - we should fall back to en-US even from (say) fr-FR if no translation exists in fr-FR - the text should never be blank. Obviously this should be flagged by the translation tool though.
*  We need a mechanism to tag translations as "identical to parent" so that we do not carry unnecessary translations, but the tool knows that they are not coverage losses.
*  We need a mechanism to tag translations as "human generated" so that the tm tool does not overwrite them unless forced.
*  We need a mechanism to determine when translations are stale. These mechanisms need to be easy for humans to use and should not add to the size of the app.
*  

We should not discount that the current solution could be optimal.

Design decisions should be made in parallel on Sky Map code and also https://github.com/jaydeetay/translationmanager
Cleaning up stardroid-v2/source-data to simplify and remove duplication is in scope.
