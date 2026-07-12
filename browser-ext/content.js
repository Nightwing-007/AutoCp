// AutoCp Submit — content script. Runs on supported judge pages; asks the
// background whether this tab has a pending submission and fills the form.

(async () => {
    let data = null;
    try {
        data = await chrome.runtime.sendMessage({ type: 'pageReady' });
    } catch (e) {
        return; // background unavailable
    }
    if (!data) return;

    const submitter = AutoCpSubmitters.findSubmitter(new URL(data.url));
    if (!submitter) return;

    const ui = createOverlay('AutoCp: submitting…');
    try {
        // fill() returns undefined when it submitted, or a message when the
        // user must pick the language / press submit themselves
        const manualMessage = await submitter.fill(data, ui);
        await chrome.runtime.sendMessage({ type: 'submitDone', success: true });
        if (manualMessage) ui.setMessage(manualMessage, 20000);
        else ui.remove();
    } catch (e) {
        const message = String((e && e.message) || e);
        await chrome.runtime.sendMessage({ type: 'submitDone', success: false, message });
        ui.setMessage(`AutoCp submit failed: ${message}`, 10000);
    }

    function createOverlay(text) {
        const el = document.createElement('div');
        el.style.cssText = [
            'position:fixed', 'top:16px', 'right:16px', 'z-index:2147483647',
            'background:#1e293b', 'color:#fff', 'padding:10px 16px',
            'border-radius:8px', 'font:13px/1.4 sans-serif',
            'box-shadow:0 4px 12px rgba(0,0,0,.35)', 'max-width:320px',
        ].join(';');
        el.textContent = text;
        document.documentElement.appendChild(el);
        let removeTimer = null;
        return {
            setMessage(message, autoRemoveMs) {
                el.textContent = message;
                if (removeTimer) clearTimeout(removeTimer);
                if (autoRemoveMs) removeTimer = setTimeout(() => el.remove(), autoRemoveMs);
            },
            remove() {
                if (removeTimer) clearTimeout(removeTimer);
                el.remove();
            },
        };
    }
})();
