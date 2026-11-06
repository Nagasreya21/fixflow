const el = (id) => document.getElementById(id);
const ticketUrl = "/api/tickets";
const notificationsUrl = `http://${location.hostname}:8081/api/notifications`;
let selectedId;
let lastRefresh = 0;

async function api(url, method = "GET", data) {
    const response = await fetch(url, {
        method,
        headers: data ? { "Content-Type": "application/json" } : undefined,
        body: data ? JSON.stringify(data) : undefined
    });
    if (!response.ok) {
        const error = response.status === 409
            ? "This ticket has already changed. Refresh updates and try again."
            : `Request failed (HTTP ${response.status}).`;
        throw new Error(error);
    }
    return response.json();
}

function addItem(list, content) {
    const item = document.createElement("li");
    item.append(content);
    list.append(item);
}

function label(value) {
    return value.replaceAll("_", " ").toLowerCase();
}

async function refresh() {
    const refreshId = ++lastRefresh;
    const id = selectedId;
    const tickets = await api(ticketUrl);
    if (refreshId !== lastRefresh) return;
    el("tickets").replaceChildren();
    if (!tickets.length) addItem(el("tickets"), "No requests yet.");
    for (const ticket of tickets) {
        const button = document.createElement("button");
        button.textContent = `${ticket.title} · ${label(ticket.priority)} · ${label(ticket.status)}`;
        button.setAttribute("aria-pressed", String(ticket.id === id));
        button.onclick = () => {
            selectedId = ticket.id;
            el("tech").value = "";
            el("detail").hidden = true;
            run(refresh);
        };
        addItem(el("tickets"), button);
    }

    const ticket = tickets.find((entry) => entry.id === id);
    if (!ticket) return;
    const events = await api(`${ticketUrl}/${id}/timeline`);
    if (refreshId !== lastRefresh) return;

    el("detail").hidden = false;
    el("ticket-title").textContent = ticket.title;
    el("ticket-info").textContent =
        `${label(ticket.priority)} · ${label(ticket.status)} · Ticket ${id}`
        + (ticket.technician ? ` · Technician: ${ticket.technician}` : "");
    el("assign").hidden = ticket.status !== "OPEN";
    const advance = el("advance");
    const next = { ASSIGNED: "IN_PROGRESS", IN_PROGRESS: "RESOLVED" }[ticket.status];
    advance.hidden = !next;
    advance.textContent = next === "IN_PROGRESS" ? "Start repair" : "Resolve request";
    advance.onclick = () => run(async () => {
        await api(`${ticketUrl}/${id}/status`, "PUT", { status: next });
        await refresh();
    }, advance);

    el("timeline").replaceChildren();
    for (const event of events) addItem(el("timeline"), event.message);
    el("notification-info").textContent = "Checking for notifications…";
    el("notifications").replaceChildren();
    try {
        const notes = await api(`${notificationsUrl}/${id}`);
        if (refreshId !== lastRefresh) return;
        el("notification-info").textContent = notes.length ? "" : "No notifications received yet.";
        for (const note of notes) addItem(el("notifications"), note.message);
    } catch {
        if (refreshId === lastRefresh) {
            el("notification-info").textContent =
                "Notifications are temporarily unavailable. The ticket and its timeline are still available.";
        }
    }
}

async function run(action, button) {
    if (button) button.disabled = true;
    el("feedback").textContent = "";
    try {
        await action();
    } catch (error) {
        el("feedback").textContent = error.message;
    } finally {
        if (button) button.disabled = false;
    }
}

el("report").onsubmit = (event) => {
    event.preventDefault();
    run(async () => {
        const ticket = await api(ticketUrl, "POST", {
            title: el("title").value.trim(),
            category: el("category").value
        });
        selectedId = ticket.id;
        el("report").reset();
        el("tech").value = "";
        await refresh();
    }, event.submitter);
};
el("assign").onsubmit = (event) => {
    event.preventDefault();
    run(async () => {
        await api(`${ticketUrl}/${selectedId}/assignment`, "PUT", {
            technician: el("tech").value.trim()
        });
        el("assign").reset();
        await refresh();
    }, event.submitter);
};
el("refresh").onclick = () => run(refresh, el("refresh"));
run(refresh);
