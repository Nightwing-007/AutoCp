// Minimal Socket.IO v4 client (Engine.IO v4, websocket transport, default namespace).
// Speaks to the AutoCp plugin's SubmitServer (also compatible with a cph-ng router).
// Hand-rolled instead of socket.io-client so the extension has zero dependencies
// and no build step.

/* exported SocketIoClient */
class SocketIoClient {
    /**
     * @param {string} url ws://host:port/ws/?EIO=4&transport=websocket&type=browser
     * @param {{onConnect?: () => void, onDisconnect?: () => void,
     *          onEvent?: (name: string, payload: any) => void}} handlers
     */
    constructor(url, handlers) {
        this.url = url;
        this.handlers = handlers || {};
        this.ws = null;
        this.connected = false;
        this.closedByUs = false;
    }

    connect() {
        this.closedByUs = false;
        try {
            this.ws = new WebSocket(this.url);
        } catch (e) {
            this.handlers.onDisconnect?.();
            return;
        }
        this.ws.onmessage = (event) => this._onMessage(String(event.data));
        this.ws.onclose = () => {
            this.connected = false;
            if (!this.closedByUs) this.handlers.onDisconnect?.();
        };
    }

    isOpen() {
        return !!this.ws && this.ws.readyState === WebSocket.OPEN && this.connected;
    }

    close() {
        this.closedByUs = true;
        this.connected = false;
        try {
            this.ws?.close();
        } catch (e) {
            // already closed
        }
    }

    emit(name, payload) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return;
        const args = payload === undefined ? [name] : [name, payload];
        this.ws.send('42' + JSON.stringify(args));
    }

    _onMessage(message) {
        if (message.startsWith('0')) {
            // engine.io OPEN -> request socket.io connect on the default namespace
            this.ws.send('40');
            return;
        }
        if (message === '2') {
            // server ping -> pong; this traffic also keeps the MV3 service worker alive
            this.ws.send('3');
            return;
        }
        if (message.startsWith('40')) {
            this.connected = true;
            this.handlers.onConnect?.();
            return;
        }
        if (message === '1' || message.startsWith('41')) {
            this.close();
            this.handlers.onDisconnect?.();
            return;
        }
        if (message.startsWith('42')) {
            let body = message.slice(2);
            if (body.startsWith('/')) body = body.slice(body.indexOf(',') + 1);
            body = body.replace(/^\d+/, '');
            try {
                const args = JSON.parse(body);
                this.handlers.onEvent?.(args[0], args[1]);
            } catch (e) {
                // malformed event, ignore
            }
        }
    }
}
