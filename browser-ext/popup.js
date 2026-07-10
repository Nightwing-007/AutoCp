const dot = document.getElementById('dot');
const statusText = document.getElementById('statusText');
const portInput = document.getElementById('port');

async function refresh() {
    try {
        const status = await chrome.runtime.sendMessage({ type: 'getStatus' });
        dot.classList.toggle('connected', !!status.connected);
        statusText.textContent = status.connected
            ? `Connected to AutoCp (port ${status.port})`
            : 'Not connected';
        if (document.activeElement !== portInput) portInput.value = status.port;
    } catch (e) {
        statusText.textContent = 'Extension is starting…';
    }
}

document.getElementById('save').addEventListener('click', async () => {
    const port = Number(portInput.value) || 27121;
    await chrome.storage.local.set({ port });
    statusText.textContent = 'Reconnecting…';
    await chrome.runtime.sendMessage({ type: 'reconnect' });
    setTimeout(refresh, 500);
});

document.getElementById('active').addEventListener('click', async () => {
    await chrome.runtime.sendMessage({ type: 'setActive' });
    statusText.textContent = 'This browser is now active for submits';
});

refresh();
setInterval(refresh, 1500);
