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
                const languageEl = await waitForElement('#select-lang > div > select');
                setValue(languageEl, '6017'); // C++ 20 (gcc); AtCoder rejects submissions without a language

                const editorBtn = await waitForElement('.editor-buttons > button:nth-child(3)');
                if (editorBtn.getAttribute('aria-pressed') !== 'true') editorBtn.click();

                const codeEl = await waitForElement('#plain-textarea');
                await waitFor(() => codeEl.style.display !== 'none');
                setValue(codeEl, data.sourceCode);
                editorBtn.click();

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
