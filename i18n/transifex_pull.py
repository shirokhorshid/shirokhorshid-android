#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Copyright (c) 2017, Psiphon Inc.
# All rights reserved.
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

'''
Pulls and massages our translations from Transifex.

Run with
# If you don't already have pipenv:
$ pip install pipenv

$ pipenv install --three --ignore-pipfile
$ pipenv run python transifex_pull.py

# To reset your pipenv state (e.g., after a Python upgrade):
$ pipenv --rm
'''


import shutil
import os
import hashlib
import json
import yaml
import transifexlib


APP_LANGS = {
    'am': 'am',         # Amharic
    'ar': 'ar',         # Arabic
    'az@latin': 'az',   # Azerbaijani
    'be': 'be',         # Belarusian
    'bn': 'bn',         # Bengali
    'bo': 'bo',         # Tibetan
    'de': 'de',         # German
    'el_GR': 'el',      # Greek
    'es': 'es',         # Spanish
    'fa': 'fa',         # Farsi/Persian
    'fa_AF': 'fa-rAF',  # Persian (Afghanistan)
    'fi_FI': 'fi',      # Finnish
    'fr': 'fr',         # French
    'hi': 'hi',         # Hindi
    'hr': 'hr',         # Croation
    'id': 'in',         # Indonesian is "in" on Android
    'it': 'it',         # Italian
    'kk': 'kk',         # Kazak
    'km': 'km',         # Khmer
    'ko': 'ko',         # Korean
    'ky': 'ky',         # Kyrgyz
    'ms': 'ms',         # Malay
    'my': 'my',         # Burmese
    'nb_NO': 'nb',      # Norwegian
    'nl': 'nl',         # Dutch
    'om': 'om',         # Afaan Oromoo
    'pt_BR': 'pt-rBR',  # Portuguese-Brazil
    'pt_PT': 'pt',   # Portuguese-Portugal
    'ru': 'ru',         # Russian
    'sn': 'sn',         # Shona
    'tg': 'tg',         # Tajik
    'th': 'th',         # Thai
    'ti': 'ti',         # Tigrinya
    'tk': 'tk',         # Turkmen
    'tr': 'tr',         # Turkish
    'uk': 'uk',         # Ukrainian
    'ur': 'ur',         # Urdu
    'uz': 'uz',         # Uzbek (latin script)
    'vi': 'vi',         # Vietnamese
    'zh': 'zh',         # Chinese (simplified)
    'zh_TW': 'zh-rTW'   # Chinese (traditional)
}

# When modifying the languages here, you MUST modify the language list in feedback.html.tpl
FEEDBACK_LANGS = {
    'en': 'en',
    'fa': 'fa',
    'ar': 'ar',
    'zh': 'zh',
    'zh_TW': 'zh_TW',
    'am': 'am',
    'az@latin': 'az',
    'be': 'be',
    'bn': 'bn',
    'bo': 'bo',
    'de': 'de',
    'el_GR': 'el',
    'es': 'es',
    'fa_AF': 'fa_AF',
    'fi_FI': 'fi',
    'fr': 'fr',
    'hi': 'hi',
    'hr': 'hr',
    'id': 'id',
    'it': 'it',
    'kk': 'kk',
    'km': 'km',
    'ko': 'ko',
    'ky': 'ky',
    'my': 'my',
    'nb_NO': 'nb',
    'nl': 'nl',
    'om': 'om',
    'pt_BR': 'pt_BR',
    'pt_PT': 'pt_PT',
    'ru': 'ru',
    'sn': 'sn',
    'tg': 'tg',
    #'th': 'th',
    'ti': 'ti',
    'tk': 'tk',
    'tr': 'tr',
    #'ug@Latn': 'ug@Latn',
    'uk': 'uk',
    'ur': 'ur',
    'uz': 'uz',
    #'uz@Cyrl': 'uz@Cyrl',
    'vi': 'vi',
}


def pull_app_translations():
    transifexlib.process_resource(
        'android-app-strings',
        APP_LANGS,
        '../app/src/main/res/values/strings.xml',
        lambda lang: f'../app/src/main/res/values-{lang}/strings.xml',
        None) # no mutator
    print(f'android-app-strings: DONE')

    transifexlib.process_resource(
        'android-library-strings',
        APP_LANGS,
        '../app/src/main/res/values/psiphon_android_library_strings.xml',
        lambda lang: f'../app/src/main/res/values-{lang}/psiphon_android_library_strings.xml',
        None) # no mutator
    print(f'android-library-strings: DONE')

    transifexlib.process_resource(
        'android-app-browser-strings',
        APP_LANGS,
        '../app/src/main/res/values/zirco_browser_strings.xml',
        lambda lang: f'../app/src/main/res/values-{lang}/zirco_browser_strings.xml',
        None) # no mutator
    print(f'android-app-browser-strings: DONE')

    transifexlib.process_resource(
        'psiphon-pro-android-strings',
        APP_LANGS,
        '../app/src/main/res/values/pro-strings.xml',
        lambda lang: f'../app/src/main/res/values-{lang}/pro-strings.xml',
        None, # no mutator
        project='psiphon-pro')
    print(f'psiphon-pro-android-strings: DONE')


FEEDBACK_DIR = './feedback'

def pull_feedback_translations():
    transifexlib.process_resource(
        'feedback-template-strings',
        FEEDBACK_LANGS,
        f'{FEEDBACK_DIR}/en.yaml',
        lambda lang: f'{FEEDBACK_DIR}/temp/{lang}.yaml',
        None) # no mutator

    # Copy the English file into temp dir for processing
    shutil.copy2(f'{FEEDBACK_DIR}/en.yaml', f'{FEEDBACK_DIR}/temp/en.yaml')

    # Generate the HTML file
    make_feedback_html(
        f'{FEEDBACK_DIR}/feedback.html.tpl',
        f'{FEEDBACK_DIR}/temp',
        f'{FEEDBACK_DIR}/temp/feedback.html')

    # Copy the HTML file to where it needs to be
    shutil.copy2(f'{FEEDBACK_DIR}/temp/feedback.html',
                 '../app/src/main/assets/feedback.html')

    shutil.rmtree(f'{FEEDBACK_DIR}/temp')

    print(f'feedback-template-strings: DONE')


def make_feedback_html(template_filename, langs_dir, output_filename):
    langs = {}
    for orig_lang, final_lang in FEEDBACK_LANGS.items():
        with open(f'{langs_dir}/{final_lang}.yaml', encoding='utf8') as f:
            lang_yaml = f.read()
            langs[final_lang] = yaml.safe_load(lang_yaml)[orig_lang]

    format = {
        'langJSON': json.dumps(langs, indent=2),
        'smiley': hashlib.md5(langs['en']['smiley_title'].encode('utf8')).hexdigest(),
        'smiley_en': langs['en']['smiley_title'],
    }

    with open(template_filename, encoding='utf8') as f:
        rendered_feedback_html = (f.read()).format(**format)

    # Make line endings consistently Unix-y.
    rendered_feedback_html = rendered_feedback_html.replace('\r\n', '\n')

    # Insert a comment warning about the file being auto-generated. This
    # comment should go below the `DOCTYPE` line to avoid potential problems
    # (see http://stackoverflow.com/a/4897850/729729).
    # Don't start at the very beginning in case there are leading newlines.
    idx = rendered_feedback_html.index('\n', 5)
    rendered_feedback_html = \
        rendered_feedback_html[:idx] \
        + '\n\n<!-- THIS FILE IS AUTOMATICALLY GENERATED. DO NOT MODIFY DIRECTLY. -->\n' \
        + rendered_feedback_html[idx:]

    with open(output_filename, 'wb') as f:
        f.write(rendered_feedback_html.encode('utf8'))


def go():
    pull_app_translations()
    pull_feedback_translations()

    print('FINISHED')


if __name__ == '__main__':
    go()
