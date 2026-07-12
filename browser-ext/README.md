# AutoCp Submit

Companion browser extension for the AutoCp IDE plugin. The plugin hands
`{url, sourceCode}` to this extension, which opens the judge's submit page and
submits the solution.

Supported judges: Codeforces (contest / gym / problemset), AtCoder, Luogu,
Hydro, Vjudge. You must already be logged in to the judge in the browser.
On Luogu (and AtCoder behind Cloudflare) you may need to solve a captcha
manually — the page shows a hint when that happens.

## Install

The extension is plain Manifest V3 with no build step:

1. Open `chrome://extensions`, enable **Developer mode**.
2. **Load unpacked** → select this `browser-ext/` directory.

The toolbar badge shows `ON` (green) while connected to the AutoCp plugin and
`OFF` otherwise. The popup lets you change the port (default `27121`, must
match the plugin) and force a reconnect.

## Why not reuse an existing extension?

The protocol is the same Socket.IO endpoint as cph-ng's router, so the
"CPH-NG Submit" extension also works with AutoCp. This extension exists to fix
the chronic silent-disconnect problem: Manifest V3 service workers are
suspended after ~30s of idle, killing the WebSocket until the user clicks the
popup. AutoCp Submit stays connected by

- relying on the plugin's 25s protocol pings, which reset Chrome's idle timer
  while the socket is healthy (Chrome 116+),
- re-launching the worker with a 30s `chrome.alarms` keepalive that reconnects
  after any suspension, sleep or plugin restart,
- keeping pending submissions in `chrome.storage.session` so a worker restart
  between "tab opened" and "page loaded" doesn't lose the submission.
