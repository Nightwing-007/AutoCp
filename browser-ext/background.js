// AutoCp Submit — background service worker.
//
// Robustness against MV3 service-worker suspension (the reason similar
// extensions "randomly disconnect"):
//  - while connected, the server's 25s socket.io pings reset Chrome's 30s
//    idle timer, so the worker stays alive (Chrome 116+);
//  - a 30s chrome.alarm revives the worker after any suspension/drop and
//    reconnects;
//  - pending submissions live in chrome.storage.session, so they survive a
//    worker restart between opening the submit tab and the page loading.

importScripts('socketio.js', 'submitters.js');

const DEFAULT_PORT = 27121;

let client = null;
let currentPort = null;
let retryTimer = null;

async function getPort() {
    const { port } = await chrome.storage.local.get({ port: DEFAULT_PORT });
    return Number(port) || DEFAULT_PORT;
}

function setBadge(connected) {
    chrome.action.setBadgeText({ text: connected ? 'ON' : 'OFF' });
    chrome.action.setBadgeBackgroundColor({ color: connected ? '#2e7d32' : '#9e9e9e' });
}

function notify(title, message) {
    chrome.notifications.create({
        type: 'basic',
        iconUrl: 'icons/icon128.png',
        title,
        message,
    });
}

function scheduleRetry() {
    // best-effort quick retry; the keepalive alarm is the reliable fallback
    if (retryTimer) clearTimeout(retryTimer);
    retryTimer = setTimeout(() => ensureConnected(), 3000);
}

async function ensureConnected() {
    const port = await getPort();
    if (client && client.isOpen() && currentPort === port) return;
    if (client) client.close();

    currentPort = port;
    client = new SocketIoClient(
        `ws://127.0.0.1:${port}/ws/?EIO=4&transport=websocket&type=browser`,
        {
            onConnect: () => setBadge(true),
            onDisconnect: () => {
                setBadge(false);
                scheduleRetry();
            },
            onEvent: (name, payload) => {
                if (name === 'submitRequest') handleSubmitRequest(payload);
            },
        },
    );
    client.connect();
}

async function handleSubmitRequest(data) {
    if (!data || !data.url) return;
    let submitUrl;
    try {
        submitUrl = AutoCpSubmitters.getSubmitUrl(new URL(data.url));
    } catch (e) {
        notify('AutoCp Submit', `Cannot submit ${data.url}: ${e.message}`);
        return;
    }
    const tab = await chrome.tabs.create({ url: submitUrl });
    const { pending = {} } = await chrome.storage.session.get('pending');
    pending[tab.id] = data;
    await chrome.storage.session.set({ pending });
}

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.type === 'pageReady') {
        (async () => {
            const { pending = {} } = await chrome.storage.session.get('pending');
            sendResponse((sender.tab && pending[sender.tab.id]) || null);
        })();
        return true;
    }
    if (message.type === 'submitDone') {
        (async () => {
            const { pending = {} } = await chrome.storage.session.get('pending');
            if (sender.tab) {
                delete pending[sender.tab.id];
                await chrome.storage.session.set({ pending });
            }
            if (!message.success)
                notify('AutoCp Submit failed', message.message || 'unknown error');
            sendResponse(null);
        })();
        return true;
    }
    if (message.type === 'getStatus') {
        (async () => {
            sendResponse({
                connected: !!(client && client.isOpen()),
                port: await getPort(),
            });
        })();
        return true;
    }
    if (message.type === 'reconnect') {
        (async () => {
            if (client) client.close();
            client = null;
            await ensureConnected();
            sendResponse(null);
        })();
        return true;
    }
    if (message.type === 'setActive') {
        client?.emit('setActive');
        sendResponse(null);
        return false;
    }
    return false;
});

function setupKeepalive() {
    chrome.alarms.create('autocp-keepalive', { periodInMinutes: 0.5 });
}

chrome.runtime.onInstalled.addListener(setupKeepalive);
chrome.runtime.onStartup.addListener(setupKeepalive);
chrome.alarms.onAlarm.addListener((alarm) => {
    if (alarm.name === 'autocp-keepalive') ensureConnected();
});

// clean up finished tabs so storage.session doesn't accumulate stale entries
chrome.tabs.onRemoved.addListener(async (tabId) => {
    const { pending = {} } = await chrome.storage.session.get('pending');
    if (tabId in pending) {
        delete pending[tabId];
        await chrome.storage.session.set({ pending });
    }
});

// runs on every service worker start (install, browser start, wake-up)
setBadge(false);
ensureConnected();
