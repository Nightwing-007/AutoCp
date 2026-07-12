// Per-judge submit-page logic, shared by the background service worker
// (getSubmitUrl) and the content script (fill). Plain script on purpose so both
// can load it (importScripts / content_scripts) without a bundler.
//
// Selectors follow the ones battle-tested by cph-ng's browser extension.

/* exported AutoCpSubmitters */
const AutoCpSubmitters = (() => {
    const CF_CONTEST = /^\/(contest|gym)\/(\d+)\/problem\/(\w+)/;
    const CF_PROBLEMSET = /^\/problemset\/problem\/(\d+)\/(\w+)/;
    const ATCODER_TASK = /^\/contests\/([\w-]+)\/tasks\/(\w+)/;

    function waitFor(condition, timeoutMs = 30000) {
        return new Promise((resolve, reject) => {
            if (condition()) return resolve();
            const deadline = Date.now() + timeoutMs;
            const timer = setInterval(() => {
                if (condition()) {
                    clearInterval(timer);
                    resolve();
                } else if (Date.now() >= deadline) {
                    clearInterval(timer);
                    reject(new Error('timed out waiting for the page'));
                }
            }, 100);
        });
    }

    async function waitForElement(selector, timeoutMs = 30000) {
        let element = null;
        await waitFor(() => {
            element = document.querySelector(selector);
            return !!element;
        }, timeoutMs).catch(() => {
            throw new Error(`element not found: ${selector} (are you logged in?)`);
        });
        return element;
    }

    // set value through the prototype setter so framework-controlled inputs notice it
    function setValue(element, value) {
        const descriptor = Object.getOwnPropertyDescriptor(
            Object.getPrototypeOf(element), 'value');
        if (descriptor && descriptor.set) descriptor.set.call(element, value);
        else element.value = value;
        element.dispatchEvent(new Event('input', { bubbles: true }));
        element.dispatchEvent(new Event('change', { bubbles: true }));
    }

    // Normalized language tokens (SubmitData.language, sent by the plugin)
    // mapped to matchers applied against the judge's language <select> option
    // texts. `family` tells whether an option is that language at all — when
    // the currently selected option already matches, it is kept, so the user's
    // preferred dialect (say, a specific C++ standard on codeforces) survives.
    // `prefer` picks a new option: first pattern with a hit wins, and within
    // that tier the candidates are ranked by `version` (highest first; the
    // generic scorer below when absent) and then by having an O2/optimized
    // variant ("C++23 O2" over "C++23", "Rust O2" over "Rust").
    const LANGUAGE_SPECS = {
        cpp: {
            family: /[cg]\+\+/i,
            prefer: [/[cg]\+\+\s*2[3-9]/i, /[cg]\+\+\s*20/i, /[cg]\+\+/i],
            version(text) {
                const match = text.match(/[cg]\+\+\s*(\d{2})/i);
                if (!match) return 0;
                const std = Number(match[1]);
                return std >= 90 ? 1900 + std : 2000 + std; // 98 → 1998, 23 → 2023
            },
        },
        c: {
            // anchored negative lookaheads reject the whole option when it
            // mentions C++ or C# anywhere (e.g. "C++ 20 (gcc 12.2)")
            family: /^(?!.*\+\+)(?!.*c#).*(?:\bgcc\b|\bc(?:[0-9][0-9])?\b)/i,
            prefer: [/^(?!.*\+\+)(?!.*c#).*\bgcc\b/i, /^(?!.*\+\+)(?!.*c#).*\bc\b/i],
        },
        rust: { family: /rust/i, prefer: [/rust/i] },
        python: {
            family: /python|pypy/i,
            prefer: [/pypy\s*3/i, /(?:c?python)\s*3/i, /pypy|python/i],
        },
        java: {
            family: /\bjava\b(?!script)/i,
            prefer: [/\bjava\b(?!script)/i],
            // rank by the JDK number, not stray bitness digits ("Java 8 64bit")
            version: (text) => Number((text.match(/\bjava\s*(\d+)/i) || [])[1] || 0),
        },
        kotlin: { family: /kotlin/i, prefer: [/kotlin/i] },
        go: { family: /\bgo\b/i, prefer: [/\bgo\b/i] },
        csharp: { family: /c#|csharp|mono/i, prefer: [/c#/i, /csharp|mono/i] },
        javascript: { family: /javascript|node/i, prefer: [/node/i, /javascript/i] },
        ruby: { family: /ruby/i, prefer: [/ruby/i] },
        haskell: { family: /haskell|ghc/i, prefer: [/haskell|ghc/i] },
        pascal: { family: /pascal|delphi|fpc/i, prefer: [/pascal|delphi|fpc/i] },
        d: { family: /\bdmd\b|\bd\b/i, prefer: [/\bdmd\b|\bd\b/i] },
        ocaml: { family: /ocaml/i, prefer: [/ocaml/i] },
        scala: { family: /\bscala\b/i, prefer: [/\bscala\b/i] }, // \b: "PascalABC" contains "scala"
        php: { family: /php/i, prefer: [/php/i] },
    };

    // Generic version signal: prefer a year-like number (language editions,
    // e.g. "Rust ... (2024)" over "(2021)"), else the largest number around.
    // "O2" is stripped first so the optimization suffix can't pose as one.
    function defaultVersionScore(text) {
        const numbers = (text.replace(/\bO2\b/gi, '').match(/\d+(?:\.\d+)?/g) || []).map(Number);
        if (!numbers.length) return 0;
        const years = numbers.filter((n) => n >= 1990 && n <= 2100);
        return years.length ? Math.max(...years) : Math.max(...numbers);
    }

    function findLanguageOption(selectEl, language) {
        const spec = LANGUAGE_SPECS[language];
        if (!spec) return null;
        const options = Array.from(selectEl.options);
        const current = options.find((o) => o.value === selectEl.value);
        if (current && spec.family.test(current.text)) return current; // keep the user's pick
        const score = (o) => [
            (spec.version || defaultVersionScore)(o.text),
            /\bO2\b/i.test(o.text) ? 1 : 0,
        ];
        for (const pattern of spec.prefer) {
            const candidates = options.filter((o) => pattern.test(o.text));
            if (!candidates.length) continue;
            return candidates.reduce((best, o) => {
                const [bestVersion, bestO2] = score(best);
                const [version, o2] = score(o);
                return version > bestVersion || (version === bestVersion && o2 > bestO2) ? o : best;
            });
        }
        return null;
    }

    // Drives the language <select> to match the submission's language.
    // Returns null on success, or a message telling the user to finish the
    // submission by hand (fill() then must NOT auto-click submit).
    function applyLanguage(selectEl, language) {
        if (!language)
            return 'AutoCp: unknown source language — pick it and press submit yourself';
        const option = findLanguageOption(selectEl, language);
        if (!option)
            return `AutoCp: no "${language}" option in the language list — pick it and press submit yourself`;
        if (option.value !== selectEl.value) setValue(selectEl, option.value);
        return null;
    }

    // For judges whose language control may be missing or undrivable
    // (markup drift, custom widgets): C++ keeps the old auto-submit behavior
    // since every judge defaults to / remembers it, anything else stops.
    function applyLanguageLenient(selectEl, language) {
        if (selectEl) return applyLanguage(selectEl, language);
        if (language === 'cpp') return null;
        return 'AutoCp: pick the language on the page, then press submit yourself';
    }

    const submitters = {
        codeforces: {
            domains: ['codeforces.com', 'm1.codeforces.com', 'm2.codeforces.com', 'm3.codeforces.com'],
            getSubmitUrl(url) {
                const contest = url.pathname.match(CF_CONTEST);
                if (contest) return `${url.origin}/${contest[1]}/${contest[2]}/submit`;
                const problem = url.pathname.match(CF_PROBLEMSET);
                if (problem) return `${url.origin}/problemset/submit`;
                throw new Error('unrecognized codeforces problem url');
            },
            async fill(data, ui) {
                const url = new URL(data.url);
                const sourceEl = await waitForElement('#sourceCodeTextarea');
                setValue(sourceEl, data.sourceCode);

                const contest = url.pathname.match(CF_CONTEST);
                if (contest) {
                    const indexEl = await waitForElement('select[name="submittedProblemIndex"]');
                    setValue(indexEl, contest[3]);
                } else {
                    const problem = url.pathname.match(CF_PROBLEMSET);
                    const codeEl = await waitForElement('input[name="submittedProblemCode"]');
                    setValue(codeEl, `${problem[1]}${problem[2]}`);
                }

                const languageEl = await waitForElement('select[name="programTypeId"]');
                const manual = applyLanguage(languageEl, data.language);
                if (manual) return manual;

                const submitBtn = await waitForElement('.submit');
                submitBtn.disabled = false;
                submitBtn.click();
            },
        },

        atcoder: {
            domains: ['atcoder.jp'],
            getSubmitUrl(url) {
                const task = url.pathname.match(ATCODER_TASK);
                if (!task) throw new Error('unrecognized atcoder task url');
                return `${url.origin}/contests/${task[1]}/submit?taskScreenName=${task[2]}`;
            },
            async fill(data, ui) {
                // AtCoder rejects submissions without a language, so set it before the code
                const languageEl = await waitForElement('#select-lang > div > select');
                const manual = applyLanguage(languageEl, data.language);

                const editorBtn = await waitForElement('.editor-buttons > button:nth-child(3)');
                if (editorBtn.getAttribute('aria-pressed') !== 'true') editorBtn.click();

                const codeEl = await waitForElement('#plain-textarea');
                await waitFor(() => codeEl.style.display !== 'none');
                setValue(codeEl, data.sourceCode);
                editorBtn.click();

                if (manual) return manual;

                if (document.querySelector('.cf-challenge') !== null) {
                    ui?.setMessage('AutoCp: solve the Cloudflare challenge to continue…');
                    const captchaEl = await waitForElement('.cf-challenge > div > input');
                    await waitFor(() => captchaEl.value.trim() !== '', 300000);
                    ui?.setMessage('AutoCp: submitting…');
                }

                (await waitForElement('#submit')).click();
            },
        },

        luogu: {
            domains: ['www.luogu.com.cn'],
            getSubmitUrl(url) {
                return url.toString();
            },
            async fill(data, ui) {
                const showSubmit = await waitForElement(
                    '#app > div.main-container > div > header > div > div > div > div.bottom-row > div.left > div > ul > li:nth-child(2)');
                showSubmit.click();

                const editor = await waitForElement('.cm-content');
                editor.innerText = data.sourceCode;

                // luogu's language picker is a custom widget we cannot drive
                const manual = applyLanguageLenient(null, data.language);
                if (manual) return manual;

                const submitBtn = await waitForElement(
                    '#app > div.main-container > div > main > div > div > div.main > div > div.body > button');
                submitBtn.click();

                // a captcha dialog may pop up after submit; leave it to the user
                waitForElement('#--swal-problem-submit-captcha', 5000)
                    .then(() => ui?.setMessage('AutoCp: solve the captcha to finish the submission', 15000))
                    .catch(() => {});
            },
        },

        hydro: {
            domains: ['hydro.ac'],
            getSubmitUrl(url) {
                const submitUrl = new URL(url);
                submitUrl.pathname += '/submit';
                return submitUrl.toString();
            },
            async fill(data, ui) {
                const sourceEl = await waitForElement('textarea');
                setValue(sourceEl, data.sourceCode);

                const manual = applyLanguageLenient(
                    document.querySelector('select[name="lang"]'), data.language);
                if (manual) return manual;

                (await waitForElement('input[type="submit"]')).click();
            },
        },

        vjudge: {
            domains: ['vjudge.net'],
            getSubmitUrl(url) {
                return url.toString();
            },
            async fill(data, ui) {
                (await waitForElement('#btn-submit')).click();
                const sourceEl = await waitForElement('#submit-solution');
                setValue(sourceEl, data.sourceCode);

                const manual = applyLanguageLenient(
                    document.querySelector('#submit-language, .modal select[name="language"]'),
                    data.language);
                if (manual) return manual;

                (await waitForElement('.modal #btn-submit')).click();
            },
        },
    };

    function findSubmitter(url) {
        return Object.values(submitters).find((s) => s.domains.includes(url.hostname)) || null;
    }

    function getSubmitUrl(url) {
        const submitter = findSubmitter(url);
        if (!submitter) throw new Error(`unsupported judge: ${url.hostname}`);
        return submitter.getSubmitUrl(url);
    }

    return { findSubmitter, getSubmitUrl };
})();
