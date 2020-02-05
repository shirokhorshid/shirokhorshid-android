# Psiphon Android Internationalization/Localization

## Translation push/pull

### Pushing

Transifex is used for string resource translation. There are multiple resources under two projects: the [Psiphon Pro](https://www.transifex.com/otf/psiphon-pro/dashboard/) project has only Pro-related strings, and the [Psiphon3](https://www.transifex.com/otf/Psiphon3/dashboard/) project has everything else.

Source strings are updated using Transifex's "auto-update" feature. We tell it which source files in which branches to monitor, and it updates the strings when it sees changes. We use the `awaiting-translation` branch for this purpose, for all our Transifex resources.

Our [`awaiting-translation` branch](https://github.com/Psiphon-Inc/psiphon-android/tree/awaiting-translation) is specialized for this purpose: it has only the relevant string resource files and a script to gather those resources files from their home branches into the `awaiting-translation` branch, where they can be committed, pushed, and then pulled by Transifex. See that branch/script for specific instructions.

### Pulling

Pulling updated translations from Transifex is done using the `i18n/transifex_pull.py` script (or scripts, as right now there is a different script for Pro strings). All that is required is that `transifex_conf.json` (containing Transifex credentials, found in CipherShare) be in place. The script pulls and massages translations for all configured languages.

Occasionally the script will indicate that a language that is not being pulled has a very complete translation. In that case a new language can be added.

## Adding a new language/locale

The locale being added must be added to `transifex_pull.py` in the `APP_LANGS` and/or `FEEDBACK_LANGS` lists, depending on where it's being added. The locale must be mapped from the form used by Transifex to the form expected by Android.

**TODO:** Android-level instructions for adding new app locale.

**TODO:** Android-level instructions for adding new feedback locale.
