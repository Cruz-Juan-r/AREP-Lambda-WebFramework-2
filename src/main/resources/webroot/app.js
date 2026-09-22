"use strict";

const LAMBDA_ROUTES = ["/hello", "/pi", "/api/sum", "/api/info", "/shutdown"];
const MAX_BODY_PREVIEW = 300;

/**
 * Sends a GET request with fetch(), writes it to the request log and returns
 * the status and body so callers can show the result.
 */
async function request(url) {
    const started = performance.now();
    try {
        const response = await fetch(url);
        const body = await response.text();
        logRequest(url, response.status, body, Math.round(performance.now() - started));
        return { status: response.status, body };
    } catch (error) {
        logRequest(url, 0, "Network error: " + error.message, 0);
        return { status: 0, body: "" };
    }
}

function logRequest(url, status, body, millis) {
    const path = url.split("?")[0];
    const item = document.createElement("li");
    if (status === 0 || status >= 400) {
        item.classList.add("fail");
    } else if (!LAMBDA_ROUTES.includes(path)) {
        item.classList.add("static");
    }

    const line = document.createElement("div");
    line.className = "line";
    line.textContent = "GET " + url;

    const statusEl = document.createElement("span");
    statusEl.className = "status";
    statusEl.textContent = (status || "ERR") + "  " + millis + " ms";
    line.appendChild(statusEl);

    const pre = document.createElement("pre");
    pre.textContent = body.length > MAX_BODY_PREVIEW ? body.slice(0, MAX_BODY_PREVIEW) + "…" : body;

    item.append(line, pre);
    document.getElementById("log").prepend(item);
    document.getElementById("log-empty").hidden = true;
}

async function greet() {
    const name = document.getElementById("name").value;
    const { body } = await request("/hello?name=" + encodeURIComponent(name));
    document.getElementById("result").textContent = body;
}

async function addNumbers() {
    const a = document.getElementById("a").value;
    const b = document.getElementById("b").value;
    const { status, body } = await request(
        "/api/sum?a=" + encodeURIComponent(a) + "&b=" + encodeURIComponent(b));
    const output = document.getElementById("sum-result");
    try {
        const data = JSON.parse(body);
        output.textContent = status === 200 ? a + " + " + b + " = " + data.sum : data.error;
    } catch {
        output.textContent = body;
    }
}

document.getElementById("greet-form").addEventListener("submit", (event) => {
    event.preventDefault();
    greet();
});

document.getElementById("sum-form").addEventListener("submit", (event) => {
    event.preventDefault();
    addNumbers();
});

document.querySelectorAll("button[data-path]").forEach((button) => {
    button.addEventListener("click", () => request(button.dataset.path));
});

document.getElementById("clear-log").addEventListener("click", () => {
    document.getElementById("log").replaceChildren();
    document.getElementById("log-empty").hidden = false;
});
