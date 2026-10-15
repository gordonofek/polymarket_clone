// Entry point: the login screen, the tabs and the automatic refresh (pull) of the data.

import * as api from './api.js';
import { $, showAlert } from './ui.js';
import { EventsView } from './events.js';
import { ProfileView } from './profile.js';
import { UsersView } from './users.js';

const EVENTS_PULL_MS = 1000;  // delta fetching of the events
const USER_PULL_MS = 2000;    // the logged in user's details and the users list
const SESSION_KEY = 'guess-market-user';

class App {
    constructor() {
        this.username = null;
        this.version = 0;          // the events version this page has (delta fetching)
        this.activeTab = 'events';
        this.timers = [];
        this.pulling = { events: false, users: false };

        this.events = new EventsView(this);
        this.profile = new ProfileView(this);
        this.users = new UsersView(this);

        $('loginForm').addEventListener('submit', (e) => {
            e.preventDefault();
            this.handleLogin();
        });
        $('usernameField').addEventListener('input', () => { $('loginError').hidden = true; });
        $('logoutBtn').addEventListener('click', () => this.logOut());
        $('loadFileBtn').addEventListener('click', () => $('fileInput').click());
        $('fileInput').addEventListener('change', () => this.handleUploadFile());
        for (const tab of document.querySelectorAll('.tab')) {
            tab.addEventListener('click', () => this.showTab(tab.dataset.tab));
        }

        // closing (or reloading) the page frees the user name on the server, like closing the desktop client
        window.addEventListener('pagehide', () => {
            if (this.username) api.logoutOnClose(this.username);
        });

        this.resumeSession();
    }

    // ---------- login ----------

    showLoginError(message) {
        $('loginError').textContent = message;
        $('loginError').hidden = false;
    }

    setLoginBusy(busy) {
        $('usernameField').disabled = busy;
        $('loginBtn').disabled = busy;
        $('loginBtn').textContent = busy ? 'Logging in...' : 'Log In';
    }

    async handleLogin() {
        const name = $('usernameField').value.trim();
        if (!name) {
            this.showLoginError('Please enter a user name.');
            $('usernameField').focus();
            return;
        }
        this.setLoginBusy(true);
        try {
            await this.enter(await api.login(name));
        } catch (err) {
            this.showLoginError(err.message);
        } finally {
            this.setLoginBusy(false);
        }
    }

    // after a page reload the same tab logs back in (the reload logged it out a moment before)
    async resumeSession() {
        const saved = sessionStorage.getItem(SESSION_KEY);
        if (!saved) return this.showLogin();
        for (let attempt = 0; attempt < 3; attempt++) {
            try {
                return await this.enter(await api.login(saved));
            } catch {
                // the logout from the reload may not have reached the server yet
                await new Promise((r) => setTimeout(r, 400));
            }
        }
        sessionStorage.removeItem(SESSION_KEY);
        this.showLogin();
        $('usernameField').value = saved;
        this.showLoginError(`Couldn't log back in as '${saved}'. It may be logged in somewhere else.`);
    }

    showLogin() {
        $('mainScreen').hidden = true;
        $('loginScreen').hidden = false;
        $('usernameField').focus();
    }

    async enter(username) {
        this.username = username;
        sessionStorage.setItem(SESSION_KEY, username);
        document.title = `Guess Market - ${username}`;
        $('headerUser').textContent = username;
        $('loginScreen').hidden = true;
        $('mainScreen').hidden = false;
        this.events.setUser(username, 0, false);

        // version 0 = every event the server has
        const delta = await api.getEventsDelta(0);
        this.version = delta.serverVersion;
        await this.events.setEvents(delta.updatedEvents);
        await this.pullUser();

        this.timers.push(setInterval(() => this.pullEvents(), EVENTS_PULL_MS));
        this.timers.push(setInterval(() => this.pullUser(), USER_PULL_MS));
    }

    async logOut() {
        this.timers.forEach(clearInterval);
        const name = this.username;
        this.username = null;
        sessionStorage.removeItem(SESSION_KEY);
        try {
            await api.logout(name);
        } catch { /* the server is gone; nothing to free */ }
        location.reload();
    }

    // ---------- file upload ----------

    // the logged in user becomes the market maker of the uploaded events
    async handleUploadFile() {
        const file = $('fileInput').files[0];
        $('fileInput').value = ''; // so picking the same file again fires 'change'
        if (!file) return;

        $('loadFileBtn').disabled = true;
        $('filePathField').value = 'Uploading to server...';
        $('uploadProgress').hidden = false;
        try {
            await api.uploadFile(file, this.username);
            $('filePathField').value = `Loaded: ${file.name}`;
            showAlert('success', 'Success', 'File uploaded and loaded successfully!');
            this.refreshSoon();
        } catch (err) {
            $('filePathField').value = 'Upload failed';
            showAlert('error', 'Upload Failed', err.message);
        } finally {
            $('uploadProgress').hidden = true;
            $('loadFileBtn').disabled = false;
        }
    }

    // ---------- tabs ----------

    showTab(name) {
        this.activeTab = name;
        for (const tab of document.querySelectorAll('.tab')) tab.classList.toggle('active', tab.dataset.tab === name);
        for (const section of ['events', 'profile', 'users']) $(`tab-${section}`).hidden = section !== name;
        this.pullUser();
    }

    // ---------- pull (automatic refresh) ----------

    // only the events that changed since our version
    async pullEvents() {
        if (this.pulling.events || !this.username) return;
        this.pulling.events = true;
        try {
            const delta = await api.getEventsDelta(this.version);
            this.version = delta.serverVersion;
            if (delta.updatedEvents.length > 0) {
                await this.events.mergeUpdated(delta.updatedEvents);
            }
        } catch (err) {
            console.warn('Event refresh failed:', err.message);
        } finally {
            this.pulling.events = false;
        }
    }

    // the logged in user's balance/portfolio (always: the "blocked" notice must show on any tab) and the users list
    async pullUser() {
        if (this.pulling.users || !this.username) return;
        this.pulling.users = true;
        try {
            const details = await api.getUserDetails(this.username);
            this.events.setUser(details.name, details.balance, details.blocked);
            this.profile.show(details);
            if (this.activeTab === 'users') {
                this.users.show(await api.getUsers());
            }
        } catch (err) {
            console.warn('User refresh failed:', err.message);
        } finally {
            this.pulling.users = false;
        }
    }

    // after the user's own action, don't wait for the next pull
    refreshSoon() {
        this.pullEvents();
        this.pullUser();
    }
}

new App();
