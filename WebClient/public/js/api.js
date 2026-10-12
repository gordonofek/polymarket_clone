// Every call to the Guess Market server, the web counterpart of the JavaFX client's ClientUtil.
// Same endpoints and parameters; requests go to /guess-market/..., which server.js forwards to Tomcat.

const BASE = '/guess-market';

// an error whose message can be shown to the user as is
export class ServerError extends Error {}

async function readJson(response) {
    const text = await response.text();
    let body;
    try {
        body = JSON.parse(text);
    } catch {
        // e.g. a Tomcat error page instead of our JSON
        throw new ServerError(`Server error (${response.status}).`);
    }
    if (!response.ok) {
        throw new ServerError(body && body.message ? body.message : `Server error (${response.status}).`);
    }
    return body;
}

async function request(method, path, params) {
    const query = new URLSearchParams(params);
    let response;
    try {
        response = method === 'GET'
            ? await fetch(`${BASE}${path}${query.size ? '?' + query : ''}`)
            : await fetch(`${BASE}${path}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                body: query,
            });
    } catch {
        throw new ServerError("Can't reach the web client's server. Is run.bat still running?");
    }
    return readJson(response);
}

const get = (path, params = {}) => request('GET', path, params);
const post = (path, params = {}) => request('POST', path, params);

// ---------- login ----------
// returns the user name as the server stores it (names are case-insensitive)
export async function login(username) {
    const body = await post('/login', { username });
    return body.username;
}

// frees the name on the server; sendBeacon still gets through while the page is closing
export function logoutOnClose(username) {
    navigator.sendBeacon(`${BASE}/logout`, new URLSearchParams({ username }));
}

export function logout(username) {
    return post('/logout', { username });
}

// ---------- events ----------
export const getEvents = () => get('/events');                                      // EventSummaryDTO[]
export const getEventsDelta = (version) => get('/events', { version });             // { serverVersion, updatedEvents }
export const getEventDetails = (eventName) => get('/events', { eventName });        // EventDetailsDTO
export const getParticipants = (eventName) =>
    get('/events', { eventName, action: 'participants' });                          // ParticipantHoldingDTO[]

export const startEvent = (username, eventName) =>
    post('/events', { action: 'startEvent', username, eventName });                 // TradeReceiptDTO

export const closeEvent = (username, eventName, winningOption) =>
    post('/events', { action: 'closeEvent', username, eventName, winningOption });  // CloseEventReceiptDTO

export const placeOrder = (username, eventName, optionName, amount, actionType, limitPrice) =>
    post('/events', {
        action: 'placeOrder', username, eventName, optionName,
        amount: String(amount), actionType, limitPrice: String(limitPrice),
    });                                                                              // TradeReceiptDTO

// ---------- users ----------
export const getUsers = () => get('/users');                                        // UserSummaryDTO[]
export const getUserDetails = (userName) => get('/users', { userName });            // UserDetailsDTO
export const getUserEventOverview = (userName, eventName) =>
    get('/users', { userName, eventName });                                         // UserEventOverviewDTO

export async function deposit(userName, amount) {
    return (await post('/users', { action: 'deposit', userName, amount: String(amount) })).balance;
}

export async function withdraw(userName, amount) {
    return (await post('/users', { action: 'withdraw', userName, amount: String(amount) })).balance;
}
