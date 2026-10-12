// Guess Market web client.
// Serves the web page (public/) and forwards every /guess-market/... request to the Guess Market
// server on Tomcat, so the page and the API share one origin (the servlets send no CORS headers).
// Uses only Node's built-in modules, so there is nothing to install.

const http = require('http');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');

const PORT = Number(process.env.PORT) || 3000;
const API_HOST = 'localhost';
const API_PORT = Number(process.env.API_PORT) || 8080; // Tomcat's port (8080 unless set, e.g. for testing)
const API_PREFIX = '/guess-market'; // the context path of guess-market.war
const PUBLIC_DIR = path.join(__dirname, 'public');

const MIME_TYPES = {
    '.html': 'text/html; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.js': 'text/javascript; charset=utf-8',
    '.svg': 'image/svg+xml',
    '.ico': 'image/x-icon',
};

// passes the request to Tomcat unchanged and streams the answer back
function forwardToServer(req, res) {
    const upstream = http.request({
        host: API_HOST,
        port: API_PORT,
        method: req.method,
        path: req.url,
        headers: { ...req.headers, host: `${API_HOST}:${API_PORT}` },
    }, (serverRes) => {
        res.writeHead(serverRes.statusCode, serverRes.headers);
        serverRes.pipe(res);
    });

    upstream.on('error', () => {
        if (!res.headersSent) {
            res.writeHead(502, { 'Content-Type': 'application/json; charset=utf-8' });
        }
        res.end(JSON.stringify({
            success: false,
            message: `Can't reach the Guess Market server at http://${API_HOST}:${API_PORT}${API_PREFIX}. `
                + 'Is Tomcat running with guess-market.war deployed?',
        }));
    });

    req.pipe(upstream);
}

function sendNotFound(res) {
    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
    res.end('Not found');
}

function serveFile(req, res) {
    let urlPath;
    try {
        urlPath = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
    } catch {
        return sendNotFound(res);
    }
    const filePath = path.join(PUBLIC_DIR, urlPath === '/' ? 'index.html' : urlPath);

    // never serve anything outside public/ (e.g. /../server.js)
    if (!filePath.startsWith(PUBLIC_DIR + path.sep)) {
        return sendNotFound(res);
    }

    fs.readFile(filePath, (err, data) => {
        if (err) {
            return sendNotFound(res);
        }
        const type = MIME_TYPES[path.extname(filePath).toLowerCase()] || 'application/octet-stream';
        res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-cache' });
        res.end(data);
    });
}

const server = http.createServer((req, res) => {
    if (req.url === API_PREFIX || req.url.startsWith(API_PREFIX + '/')) {
        forwardToServer(req, res);
    } else {
        serveFile(req, res);
    }
});

server.on('error', (err) => {
    if (err.code === 'EADDRINUSE') {
        console.error(`Port ${PORT} is already in use. Close the program using it (maybe another copy of this client) and try again.`);
    } else {
        console.error('Could not start the web client:', err.message);
    }
    process.exit(1);
});

server.listen(PORT, () => {
    const url = `http://localhost:${PORT}`;
    console.log(`Guess Market web client is running at ${url}`);
    console.log(`It talks to the server at http://${API_HOST}:${API_PORT}${API_PREFIX}`);
    console.log('Keep this window open while using the client. Press Ctrl+C to stop it.');

    // open the page in the default browser (Windows); NO_BROWSER=1 skips it
    if (process.platform === 'win32' && !process.env.NO_BROWSER) {
        exec(`start "" "${url}"`);
    }
});
