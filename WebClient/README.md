# Guess Market – Web Client (Exercise 4, bonus)

A browser client for the Guess Market server from Exercise 3. It talks to the **same server and the same
API** as the JavaFX client, so users of both clients see the same events, trades and balances in real time.

## Requirements

- **Node.js** (any current version). Nothing else: no `npm install`, no internet connection.
- The **Guess Market server** running as usual: `guess-market.war` deployed on Tomcat at
  `http://localhost:8080/guess-market` (exactly as in Exercise 3 – nothing about the server changes).

## How to run

1. Start Tomcat with `guess-market.war` deployed, as in Exercise 3.
2. Double-click **`run.bat`** in this folder. It starts a small local web server and opens the browser.
3. If the browser doesn't open by itself, go to: **<http://localhost:3000>**
4. Keep the `run.bat` window open while using the client (close it, or press Ctrl+C, to stop).

To try several users at once, open <http://localhost:3000> in more browser tabs/windows – each tab logs in
as its own user. JavaFX clients can be used at the same time.

## What it does

| Screen | Contents |
|---|---|
| **Login** | Enter a user name. A name that is currently logged in (from any client) is refused; names are case-insensitive. A name that logged out before gets its account back. |
| **Events** | Like the JavaFX Events tab: search and filters (method / status / commission), event tiles (status, method, options with prices, commission, the event's account), and for the selected event its details, option prices (Bid/Ask/Mid/Spread for order books), Buy/Sell, the price-history graph, the order book queue / trade history and the participants with their holdings' value. The event's market maker gets *Start Event* / *Close Event*. |
| **Profile** | Balance, *Add Funds* / *Withdraw*, the full balance history (every change: trades, commissions, payouts, deposits…), the portfolio, and an overview of a picked event. A notice appears when the balance goes negative (the account is then blocked until a deposit). |
| **Users** | Every user with their balance and whether they are a market maker; picking a user shows the events they are market maker of. |

As the exercise defines, the web client **cannot upload files** (events are uploaded from the JavaFX client).
The JavaFX client's *Settings* tab (themes/animations) is not part of the web client.

**Automatic updates (pull):** the events are refreshed every second with delta fetching (only events that
changed since the last pull), and the user's details every 2 seconds – the same endpoints the JavaFX client uses.

**Logging out:** closing the tab (or the *Log Out* button) frees the user name, like closing the JavaFX client.
Reloading the page keeps the same user logged in.

## How it works

- `public/` – the page: `index.html`, `styles.css` and plain JavaScript modules in `public/js/`
  (`api.js` – every server call; `events.js`, `profile.js`, `users.js` – the screens; `chart.js` – the graph;
  `ui.js` – shared helpers and dialogs; `app.js` – login, tabs and the automatic refresh).
  No framework and no build step.
- `server.js` – a small web server written with Node's built-in modules only. It serves `public/` and forwards
  every `/guess-market/...` request to Tomcat at `localhost:8080`. This way the page and the API share one
  address, so the browser allows the calls without any change to the Guess Market server (the servlets
  don't send CORS headers, and the exercise doesn't allow changing how the server is set up).
- The port can be changed with the `PORT` environment variable (default 3000).

## Troubleshooting

| Problem | Fix |
|---|---|
| `'node' is not recognized...` | Node.js isn't installed or isn't on the PATH. |
| "Port 3000 is already in use" | Another copy of the web client is running – close it, or set `PORT` to another port. |
| Login says it can't reach the Guess Market server | Tomcat isn't running, or `guess-market.war` isn't deployed at `localhost:8080`. |
| "The user name '…' is already logged in" | That name is in use by another tab or client. Pick another name, or log out there first. |

## Working with AI tools

> **TODO – these are personal questions; replace the placeholders with your own answers.**

**Which tool did we use, and why? Did we try others?**
Claude Code (Anthropic's AI coding assistant, running in the terminal next to our project). It could read our
existing JavaFX client and servlets, so it built the web client against our real API and copied the look and
behaviour of our JavaFX screens instead of guessing. *TODO: why you chose it / other tools you tried.*

**Were there areas where the AI didn't "deliver" and we had to step in as programmers?**
*TODO.* (A fact you can use: the browser can't call our servlets from another address because they send no
CORS headers. The AI solved it with a local Node proxy instead of changing the servlets, since the exercise
forbids changing how the server is set up.)

**Did we run into a bug or requirement the AI couldn't solve?**
*TODO.*

**How much was done by the AI alone (only prompts) vs. with our involvement?**
*TODO: estimate, e.g. "about X% of the code was written by the AI from prompts; we reviewed / changed …".*

**How long did it take? How long would it have taken without AI?**
*TODO.*

**Did we know front-end development before? Did it help?**
*TODO.*

**Compared to the rest of the course, was this AI-driven way of working fun?**
*TODO.*
