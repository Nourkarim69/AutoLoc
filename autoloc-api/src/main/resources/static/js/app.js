/* AutoLoc — front-end (vanilla JS, talks to the Spring Boot REST API with a session cookie) */
"use strict";

// ---------- Constants ----------
const CATEGORIES = { CITADINE: "Citadine", BERLINE: "Berline", SUV: "SUV", UTILITAIRE: "Utilitaire" };
const STATUTS = { DISPONIBLE: "Disponible", LOUE: "Loué", MAINTENANCE: "Maintenance" };
const STATUTS_RESA = { EN_ATTENTE: "En attente", CONFIRMEE: "Confirmée", ANNULEE: "Annulée", TERMINEE: "Terminée" };
const ACTIVES = ["EN_ATTENTE", "CONFIRMEE"];
const MODES_PAIEMENT = { CARTE: "Carte bancaire", ESPECES: "Espèces", VIREMENT: "Virement" };
const ROLES = { AGENT: "Agent", MANAGER: "Manager" };
const CAR_COLORS = { CITADINE: "#10b981", BERLINE: "#0f766e", SUV: "#65a30d", UTILITAIRE: "#d97706" };

const app = document.getElementById("app");
const modal = document.getElementById("modal");
const modalContent = document.getElementById("modal-content");
const state = { user: null };

// ---------- Helpers ----------
const esc = s => String(s ?? "").replace(/[&<>"']/g, c =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const euro = n => Number(n).toLocaleString("fr-FR", { style: "currency", currency: "EUR" });
const options = (map, selected = "", empty = null) =>
    (empty !== null ? `<option value="">${esc(empty)}</option>` : "") +
    Object.entries(map).map(([k, v]) => `<option value="${k}" ${k === selected ? "selected" : ""}>${esc(v)}</option>`).join("");
const pad = n => String(n).padStart(2, "0");
const isoDate = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parseIso = s => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };
const addDays = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
const today = () => isoDate(new Date());
const daysBetween = (a, b) => Math.round((parseIso(b) - parseIso(a)) / 86400000);
const fmtDate = s => s ? parseIso(s).toLocaleDateString("fr-FR", { weekday: "short", day: "numeric", month: "short", year: "numeric" }) : "—";
const initials = u => `${(u.prenom || "?")[0]}${(u.nom || "")[0] || ""}`.toUpperCase();
const plural = (n, word) => `${n} ${word}${n > 1 ? "s" : ""}`;

const api = {
    async req(method, url, body) {
        const res = await fetch(url, {
            method,
            credentials: "same-origin",
            headers: body !== undefined ? { "Content-Type": "application/json" } : {},
            body: body !== undefined ? JSON.stringify(body) : undefined
        });
        if (res.status === 204) return null;
        const data = await res.json().catch(() => null);
        if (!res.ok) {
            if (res.status === 401 && !url.startsWith("/api/auth/")) { state.user = null; renderNav(); }
            const err = new Error(data?.message || `Erreur HTTP ${res.status}`);
            err.status = res.status;
            err.errors = data?.errors || {};
            throw err;
        }
        return data;
    },
    get: url => api.req("GET", url),
    post: (url, b) => api.req("POST", url, b),
    put: (url, b) => api.req("PUT", url, b),
    patch: (url, b) => api.req("PATCH", url, b),
    del: url => api.req("DELETE", url)
};

function toast(message, type = "") {
    const el = document.createElement("div");
    el.className = `toast ${type}`;
    el.textContent = message;
    document.getElementById("toasts").appendChild(el);
    setTimeout(() => el.remove(), 3800);
}

function openModal(html) {
    modalContent.innerHTML = html;
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    modalContent.querySelector("input, select, button:not(.modal-close)")?.focus();
}
function closeModal() {
    modal.hidden = true;
    modalContent.innerHTML = "";
    document.body.style.overflow = "";
}
modal.addEventListener("click", e => { if (e.target.closest("[data-close]")) closeModal(); });
document.addEventListener("keydown", e => { if (e.key === "Escape" && !modal.hidden) closeModal(); });

const loading = (text = "Chargement…") => `<div class="loading"><div class="spinner"></div>${esc(text)}</div>`;
const errorBox = err => `<div class="error-box"><strong>Impossible de joindre le serveur.</strong><br>${esc(err.message)}<br><small>Vérifiez que l'API Spring Boot et MySQL sont démarrés.</small></div>`;

/** Shows API validation errors next to the matching fields of a form. */
function showFormErrors(form, err) {
    form.querySelectorAll(".field-error").forEach(e => e.textContent = "");
    form.querySelectorAll(".invalid").forEach(e => e.classList.remove("invalid"));
    Object.entries(err.errors || {}).forEach(([name, msg]) => {
        const input = form.elements[name];
        if (input) input.classList.add("invalid");
        const slot = form.querySelector(`[data-error-for="${name}"]`);
        if (slot) slot.textContent = msg;
    });
    toast(err.message, "ko");
}
const formData = form => Object.fromEntries(new FormData(form).entries());

function field(name, label, type = "text", value = "", attrs = "", cls = "") {
    return `<div class="field ${cls}">
        <label for="fld-${name}">${esc(label)}</label>
        <input class="input" id="fld-${name}" name="${name}" type="${type}" value="${esc(value)}" ${attrs}>
        <span class="field-error" data-error-for="${name}"></span>
    </div>`;
}
function passwordField(name, label, attrs = "", withStrength = false) {
    return `<div class="field">
        <label for="fld-${name}">${esc(label)}</label>
        <div class="pw-wrap">
            <input class="input" id="fld-${name}" name="${name}" type="password" ${attrs}>
            <button type="button" class="pw-toggle" data-pw="fld-${name}">Afficher</button>
        </div>
        ${withStrength ? `<div class="strength"><span id="strength-bar"></span></div><span class="muted small" id="strength-label"></span>` : ""}
        <span class="field-error" data-error-for="${name}"></span>
    </div>`;
}
function selectField(name, label, map, value = "") {
    return `<div class="field">
        <label for="fld-${name}">${esc(label)}</label>
        <select class="input" id="fld-${name}" name="${name}">${options(map, value)}</select>
        <span class="field-error" data-error-for="${name}"></span>
    </div>`;
}
function bindPasswordToggles(root) {
    root.querySelectorAll("[data-pw]").forEach(b => b.addEventListener("click", () => {
        const input = document.getElementById(b.dataset.pw);
        input.type = input.type === "password" ? "text" : "password";
        b.textContent = input.type === "password" ? "Afficher" : "Masquer";
    }));
}

/** Side-view car illustration, coloured by category. */
function carSvg(categorie) {
    const c = CAR_COLORS[categorie] || "#64748b";
    const tall = categorie === "SUV" || categorie === "UTILITAIRE";
    const body = tall
        ? "M18 92 L22 60 Q26 50 38 48 L70 46 L92 22 Q96 18 104 18 L196 18 Q206 18 210 26 L228 58 Q244 62 248 74 L250 92 Z"
        : "M14 92 L18 70 Q22 60 36 58 L78 54 L106 30 Q112 26 122 26 L176 26 Q186 26 194 34 L216 56 Q244 60 250 74 L252 92 Z";
    const glass = tall
        ? "M98 26 L104 24 L148 24 L148 50 L80 50 Z M156 24 L196 24 Q202 24 205 30 L220 52 L156 52 Z"
        : "M112 34 L118 32 L148 32 L148 54 L92 56 Z M156 32 L180 32 Q186 32 190 38 L206 56 L156 55 Z";
    return `<svg viewBox="0 0 266 120" aria-hidden="true">
        <ellipse cx="133" cy="108" rx="118" ry="7" fill="rgba(5,46,34,.14)"/>
        <path d="${body}" fill="${c}"/>
        <path d="${glass}" fill="#ecfeff" opacity=".95"/>
        <rect x="236" y="70" width="12" height="7" rx="3" fill="#fef08a"/>
        <rect x="16" y="72" width="10" height="6" rx="3" fill="#fca5a5"/>
        <circle cx="66" cy="94" r="17" fill="#1f2937"/><circle cx="66" cy="94" r="7" fill="#d1d5db"/>
        <circle cx="204" cy="94" r="17" fill="#1f2937"/><circle cx="204" cy="94" r="7" fill="#d1d5db"/>
    </svg>`;
}

function vehicleCard(v) {
    return `<article class="vcard" tabindex="0" data-id="${v.idVehicule}">
        <div class="vcard-media media-${esc(v.categorie)}">
            ${carSvg(v.categorie)}
            <span class="badge badge-${esc(v.statut)}">${esc(STATUTS[v.statut] || v.statut)}</span>
        </div>
        <div class="vcard-body">
            <h3 class="vcard-title">${esc(v.marque)} ${esc(v.modele)}</h3>
            <div class="vcard-meta">
                <span class="chip">${esc(CATEGORIES[v.categorie] || v.categorie)}</span>
                <span class="chip plate">${esc(v.immatriculation)}</span>
                ${v.agence ? `<span class="chip">📍 ${esc(v.agence.ville)}</span>` : ""}
            </div>
            <div class="vcard-foot">
                <span class="price">${euro(v.tarifJournalier)} <small>/ jour</small></span>
                <span class="btn btn-light btn-sm">Détails →</span>
            </div>
        </div>
    </article>`;
}
function bindVehicleCards(root, vehicules) {
    root.querySelectorAll(".vcard").forEach(card => {
        const open = () => showVehicleDetail(vehicules.find(v => v.idVehicule === Number(card.dataset.id)));
        card.addEventListener("click", open);
        card.addEventListener("keydown", e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); open(); } });
    });
}

function showVehicleDetail(v) {
    if (!v) return;
    const dispo = v.statut !== "MAINTENANCE";
    openModal(`
        <div class="detail-media media-${esc(v.categorie)}">${carSvg(v.categorie)}</div>
        <span class="badge badge-${esc(v.statut)}">${esc(STATUTS[v.statut])}</span>
        <h2 style="margin-top:10px">${esc(v.marque)} ${esc(v.modele)}</h2>
        <div class="detail-grid">
            <div><span>Catégorie</span><strong>${esc(CATEGORIES[v.categorie])}</strong></div>
            <div><span>Immatriculation</span><strong>${esc(v.immatriculation)}</strong></div>
            <div><span>Tarif journalier</span><strong>${euro(v.tarifJournalier)}</strong></div>
            <div><span>Agence de retrait</span><strong>${v.agence ? esc(v.agence.nom) : "—"}</strong></div>
        </div>
        ${dispo
            ? `<a class="btn btn-primary" style="width:100%" href="#/reserver?vehicule=${v.idVehicule}">Réserver ce véhicule</a>`
            : `<p class="notice notice-ko">Ce véhicule est en maintenance et ne peut pas être réservé pour le moment.</p>`}
    `);
}

// ---------- Navigation / session ----------
function renderNav() {
    const box = document.getElementById("nav-user");
    const u = state.user;
    if (!u) {
        box.innerHTML = `<a href="#/connexion" data-route="connexion" class="nav-cta">Connexion</a>`;
    } else {
        const space = u.role === "ADMIN"
            ? `<a href="#/admin" data-route="admin">Administration</a>`
            : `<a href="#/client" data-route="client">Mon espace</a>`;
        box.innerHTML = `${space}
            <span class="avatar" title="${esc(u.prenom)} ${esc(u.nom)}">${esc(initials(u))}</span>
            <a href="#" id="logout">Déconnexion</a>`;
        document.getElementById("logout").addEventListener("click", async e => {
            e.preventDefault();
            await api.post("/api/auth/logout").catch(() => null);
            state.user = null;
            renderNav();
            toast("Vous êtes déconnecté");
            location.hash = "#/";
        });
    }
    markActive();
}
function markActive() {
    const page = location.hash.replace(/^#\/?/, "").split("?")[0];
    document.querySelectorAll(".nav-links a[data-route]").forEach(a => a.classList.toggle("active", a.dataset.route === page));
}
async function loadUser() {
    try {
        const me = await api.get("/api/auth/me");
        state.user = me && me.role ? me : null;
    } catch { state.user = null; }
    renderNav();
}
function requireLogin(role, next) {
    if (state.user && (!role || state.user.role === role)) return true;
    if (state.user && role && state.user.role !== role) {
        app.innerHTML = `<div class="container"><div class="page-head"><h1>Accès refusé</h1></div>
            <div class="empty">Cette page est réservée ${role === "ADMIN" ? "à l'administration" : "aux clients"}.</div></div>`;
        return false;
    }
    location.hash = `#/connexion?next=${encodeURIComponent(next)}`;
    return false;
}

// ---------- Home ----------
async function pageHome() {
    app.innerHTML = `
    <section class="hero">
        <div class="container hero-inner">
            <div>
                <span class="pill">🌿 Réservation en ligne, prolongation en 1 clic</span>
                <h1>Louez la voiture <span>qu'il vous faut</span>, au juste prix.</h1>
                <p>Choisissez votre véhicule, vos dates de prise et de retour, et prolongez votre location depuis votre espace client.</p>
                <div class="hero-actions">
                    <a href="#/reserver" class="btn btn-primary">Réserver maintenant</a>
                    <a href="#/vehicules" class="btn btn-ghost">Voir les véhicules</a>
                </div>
            </div>
            <div class="hero-art">${carSvg("CITADINE")}</div>
        </div>
    </section>
    <div class="container">
        <form class="search-card" id="quick-book">
            <div class="field"><label for="qb-v">Véhicule</label>
                <select class="input" id="qb-v" name="vehicule"><option value="">Chargement…</option></select></div>
            <div class="field"><label for="qb-d1">Prise du véhicule</label>
                <input class="input" type="date" id="qb-d1" name="debut" value="${isoDate(addDays(new Date(), 1))}" min="${today()}"></div>
            <div class="field"><label for="qb-d2">Retour du véhicule</label>
                <input class="input" type="date" id="qb-d2" name="fin" value="${isoDate(addDays(new Date(), 4))}" min="${isoDate(addDays(new Date(), 1))}"></div>
            <button class="btn btn-primary" type="submit">Continuer →</button>
        </form>
    </div>
    <section class="section"><div class="container" id="home-stats">${loading()}</div></section>
    <section class="section" style="padding-top:0"><div class="container">
        <div class="section-head">
            <div><div class="eyebrow">Disponibles</div><h2>Nos véhicules à la une</h2></div>
            <a href="#/vehicules" class="btn btn-light">Tout le catalogue →</a>
        </div>
        <div id="home-featured">${loading()}</div>
    </div></section>
    <section class="section" style="padding-top:0"><div class="container">
        <div class="section-head"><div><div class="eyebrow">Pourquoi AutoLoc</div><h2>Une location sans mauvaise surprise</h2></div></div>
        <div class="features">
            <div class="feature"><div class="feature-icon">📅</div><h3>Vos dates, votre rythme</h3><p>Choisissez le jour de prise et de retour : le prix est calculé instantanément.</p></div>
            <div class="feature"><div class="feature-icon">⏩</div><h3>Prolongation facile</h3><p>Besoin de garder la voiture plus longtemps ? Prolongez depuis votre espace client.</p></div>
            <div class="feature"><div class="feature-icon">🔒</div><h3>Compte sécurisé</h3><p>Mots de passe chiffrés (BCrypt) et session protégée.</p></div>
        </div>
    </div></section>`;

    try {
        const [vehicules, agences] = await Promise.all([api.get("/api/vehicules"), api.get("/api/agences")]);
        const select = document.getElementById("qb-v");
        select.innerHTML = vehicleOptions(vehicules, "", "Choisir un véhicule");
        document.getElementById("quick-book").addEventListener("submit", e => {
            e.preventDefault();
            const d = formData(e.target);
            location.hash = `#/reserver?${new URLSearchParams(Object.entries(d).filter(([, v]) => v))}`;
        });
        const dispo = vehicules.filter(v => v.statut === "DISPONIBLE");
        const minPrice = vehicules.length ? Math.min(...vehicules.map(v => Number(v.tarifJournalier))) : 0;
        document.getElementById("home-stats").innerHTML = `<div class="stats">
            <div class="stat"><div class="stat-value">${vehicules.length}</div><div class="stat-label">véhicules dans la flotte</div></div>
            <div class="stat"><div class="stat-value">${dispo.length}</div><div class="stat-label">disponibles aujourd'hui</div></div>
            <div class="stat"><div class="stat-value">${agences.length}</div><div class="stat-label">agences en France</div></div>
            <div class="stat"><div class="stat-value">${vehicules.length ? euro(minPrice) : "—"}</div><div class="stat-label">prix minimum / jour</div></div>
        </div>`;
        const featured = document.getElementById("home-featured");
        const list = dispo.slice(0, 3);
        featured.innerHTML = list.length
            ? `<div class="grid">${list.map(vehicleCard).join("")}</div>`
            : `<div class="empty">Aucun véhicule disponible pour le moment.</div>`;
        bindVehicleCards(featured, list);
    } catch (err) {
        document.getElementById("home-stats").innerHTML = errorBox(err);
        document.getElementById("home-featured").innerHTML = "";
    }
}

/** Drop-down options grouped by category; cars in maintenance are disabled. */
function vehicleOptions(vehicules, selected = "", placeholder = "Choisir un véhicule") {
    const groups = Object.keys(CATEGORIES).map(cat => {
        const list = vehicules.filter(v => v.categorie === cat)
            .sort((a, b) => Number(a.tarifJournalier) - Number(b.tarifJournalier));
        if (!list.length) return "";
        return `<optgroup label="${esc(CATEGORIES[cat])}">${list.map(v => {
            const off = v.statut === "MAINTENANCE";
            return `<option value="${v.idVehicule}" ${String(v.idVehicule) === String(selected) ? "selected" : ""} ${off ? "disabled" : ""}>
                ${esc(v.marque)} ${esc(v.modele)} — ${euro(v.tarifJournalier)}/jour${off ? " (en maintenance)" : ""}</option>`;
        }).join("")}</optgroup>`;
    }).join("");
    return `<option value="">${esc(placeholder)}</option>${groups}`;
}

// ---------- Catalogue ----------
async function pageVehicules(params) {
    app.innerHTML = `<div class="container">
        <div class="page-head"><div class="eyebrow">Catalogue</div><h1>Nos véhicules</h1>
            <p class="muted">Cliquez sur un véhicule pour voir le détail et le réserver.</p></div>
        <div class="toolbar">
            <input class="input search" id="f-q" type="search" placeholder="🔎 Marque, modèle ou immatriculation" value="${esc(params.get("q") || "")}">
            <select class="input" id="f-cat">${options(CATEGORIES, params.get("categorie") || "", "Toutes catégories")}</select>
            <select class="input" id="f-st">${options(STATUTS, params.get("statut") || "", "Tous statuts")}</select>
            <select class="input" id="f-sort">
                <option value="prix-asc">Prix croissant</option>
                <option value="prix-desc">Prix décroissant</option>
                <option value="nom">Marque (A→Z)</option>
            </select>
        </div>
        <p class="result-count" id="count"></p>
        <div id="list">${loading()}</div>
    </div>`;

    let vehicules;
    try { vehicules = await api.get("/api/vehicules"); }
    catch (err) { document.getElementById("list").innerHTML = errorBox(err); return; }

    const render = () => {
        const q = document.getElementById("f-q").value.trim().toLowerCase();
        const cat = document.getElementById("f-cat").value;
        const st = document.getElementById("f-st").value;
        const sort = document.getElementById("f-sort").value;
        const list = vehicules
            .filter(v => (!cat || v.categorie === cat) && (!st || v.statut === st)
                && (!q || `${v.marque} ${v.modele} ${v.immatriculation}`.toLowerCase().includes(q)))
            .sort((a, b) => sort === "nom"
                ? `${a.marque} ${a.modele}`.localeCompare(`${b.marque} ${b.modele}`)
                : (Number(a.tarifJournalier) - Number(b.tarifJournalier)) * (sort === "prix-desc" ? -1 : 1));
        document.getElementById("count").textContent = plural(list.length, "véhicule");
        const box = document.getElementById("list");
        box.innerHTML = list.length
            ? `<div class="grid">${list.map(vehicleCard).join("")}</div>`
            : `<div class="empty">Aucun véhicule ne correspond à votre recherche.</div>`;
        bindVehicleCards(box, list);
    };
    ["f-q", "f-cat", "f-st", "f-sort"].forEach(id => document.getElementById(id).addEventListener("input", render));
    render();
}

// ---------- Booking ----------
const DRAFT_KEY = "autoloc-draft";
function saveDraft(d) { try { sessionStorage.setItem(DRAFT_KEY, JSON.stringify(d)); } catch { /* ignore */ } }
function takeDraft() {
    try { const d = JSON.parse(sessionStorage.getItem(DRAFT_KEY) || "null"); sessionStorage.removeItem(DRAFT_KEY); return d; }
    catch { return null; }
}

async function pageReserver(params) {
    const draft = takeDraft() || {};
    const pre = {
        vehicule: params.get("vehicule") || draft.vehicule || "",
        debut: params.get("debut") || draft.debut || isoDate(addDays(new Date(), 1)),
        fin: params.get("fin") || draft.fin || isoDate(addDays(new Date(), 4)),
        options: draft.options || []
    };

    app.innerHTML = `<div class="container">
        <div class="page-head"><div class="eyebrow">Réservation</div><h1>Réserver une voiture</h1>
            <p class="muted">Choisissez votre véhicule et vos dates : le prix s'affiche immédiatement.</p></div>
        <div id="booking">${loading()}</div></div>`;

    let vehicules, equipements;
    try { [vehicules, equipements] = await Promise.all([api.get("/api/vehicules"), api.get("/api/equipements")]); }
    catch (err) { document.getElementById("booking").innerHTML = errorBox(err); return; }

    document.getElementById("booking").innerHTML = `<div class="booking">
        <form class="panel" id="book-form" novalidate>
            <div class="step"><span class="step-num">1</span><h2 style="margin:0">Choisissez votre voiture</h2></div>
            <div class="field">
                <label for="fld-idVehicule">Véhicule</label>
                <select class="input" id="fld-idVehicule" name="idVehicule">${vehicleOptions(vehicules, pre.vehicule)}</select>
                <span class="field-error" data-error-for="idVehicule"></span>
            </div>
            <div class="step" style="margin-top:22px"><span class="step-num">2</span><h2 style="margin:0">Vos dates</h2></div>
            <div class="form-grid">
                ${field("dateDebut", "Date de prise du véhicule", "date", pre.debut, `min="${today()}" required`)}
                ${field("dateFin", "Date de retour du véhicule", "date", pre.fin, `required`)}
            </div>
            <div id="booked"></div>
            <div class="step" style="margin-top:22px"><span class="step-num">3</span><h2 style="margin:0">Options</h2></div>
            <div class="options-grid">${equipements.length ? equipements.map(e => `
                <label class="option">
                    <input type="checkbox" name="opt" value="${e.idEquipement}" ${pre.options.map(String).includes(String(e.idEquipement)) ? "checked" : ""}>
                    <span>${esc(e.libelle)}</span><strong>+ ${euro(e.tarifJournalier)}/jour</strong>
                </label>`).join("") : `<p class="muted">Aucune option disponible.</p>`}</div>
            <div class="step" style="margin-top:22px"><span class="step-num">4</span><h2 style="margin:0">Confirmation</h2></div>
            <div id="login-hint"></div>
            <div class="form-actions" style="justify-content:flex-start">
                <button class="btn btn-primary" type="submit" id="book-btn">Confirmer la réservation</button>
            </div>
        </form>
        <aside class="summary" id="summary"></aside>
    </div>`;

    const form = document.getElementById("book-form");
    const sel = form.elements.idVehicule, d1 = form.elements.dateDebut, d2 = form.elements.dateFin;
    let booked = [];

    const hint = document.getElementById("login-hint");
    if (!state.user) {
        hint.innerHTML = `<p class="notice notice-info">Vous devrez vous connecter (ou créer un compte) pour confirmer. Vos choix seront conservés.</p>`;
    } else if (state.user.role === "ADMIN") {
        hint.innerHTML = `<p class="notice notice-warn">Vous êtes connecté en administrateur : seule une personne connectée avec un compte client peut réserver.</p>`;
    }

    const overlaps = () => booked.filter(b => b.dateDebut < d2.value && b.dateFin > d1.value);
    const chosenOptions = () => [...form.querySelectorAll("input[name=opt]:checked")]
        .map(c => equipements.find(e => String(e.idEquipement) === c.value)).filter(Boolean);

    const renderSummary = () => {
        const v = vehicules.find(x => String(x.idVehicule) === sel.value);
        d2.min = d1.value ? isoDate(addDays(parseIso(d1.value), 1)) : today();
        const days = d1.value && d2.value ? daysBetween(d1.value, d2.value) : 0;
        const box = document.getElementById("summary");
        if (!v) {
            box.innerHTML = `<div class="summary-body" style="padding-top:22px"><h3>Récapitulatif</h3><p class="muted">Choisissez un véhicule dans la liste déroulante.</p></div>`;
            return;
        }
        const conflicts = overlaps();
        const opts = chosenOptions();
        const parJour = Number(v.tarifJournalier) + opts.reduce((t, e) => t + Number(e.tarifJournalier), 0);
        box.innerHTML = `
            <div class="summary-media media-${esc(v.categorie)}">${carSvg(v.categorie)}</div>
            <div class="summary-body">
                <h3 style="margin-bottom:4px">${esc(v.marque)} ${esc(v.modele)}</h3>
                <span class="chip">${esc(CATEGORIES[v.categorie])}</span>
                <div style="margin-top:12px">
                    <div class="summary-line"><span class="muted">Prise</span><strong>${fmtDate(d1.value)}</strong></div>
                    <div class="summary-line"><span class="muted">Retour</span><strong>${fmtDate(d2.value)}</strong></div>
                    <div class="summary-line"><span class="muted">Durée</span><strong>${days > 0 ? plural(days, "jour") : "—"}</strong></div>
                    ${v.agence ? `<div class="summary-line"><span class="muted">Agence</span><strong>${esc(v.agence.nom)}</strong></div>` : ""}
                    <div class="summary-line"><span class="muted">Véhicule</span><strong>${euro(v.tarifJournalier)} / jour</strong></div>
                    ${opts.map(e => `<div class="summary-line"><span class="muted">${esc(e.libelle)}</span><strong>+ ${euro(e.tarifJournalier)} / jour</strong></div>`).join("")}
                </div>
                <div class="summary-total"><span>Total</span><strong>${days > 0 ? euro(days * parJour) : "—"}</strong></div>
                ${days <= 0 && d1.value && d2.value ? `<p class="notice notice-ko">La date de retour doit être après la date de prise.</p>` : ""}
                ${conflicts.length ? `<p class="notice notice-ko">Déjà réservé du ${fmtDate(conflicts[0].dateDebut)} au ${fmtDate(conflicts[0].dateFin)}. Choisissez d'autres dates.</p>` : ""}
            </div>`;
        document.getElementById("book-btn").disabled = days <= 0 || conflicts.length > 0;
    };

    const loadBooked = async () => {
        booked = [];
        document.getElementById("booked").innerHTML = "";
        if (sel.value) {
            try {
                booked = await api.get(`/api/reservations/indisponibilites?vehicule=${sel.value}`);
                if (booked.length) {
                    document.getElementById("booked").innerHTML = `<p class="notice notice-warn">Ce véhicule est déjà réservé :
                        ${booked.map(b => `du ${fmtDate(b.dateDebut)} au ${fmtDate(b.dateFin)}`).join(" · ")}</p>`;
                }
            } catch { /* not blocking: the server checks again on submit */ }
        }
        renderSummary();
    };

    sel.addEventListener("change", loadBooked);
    d1.addEventListener("input", () => {
        if (d2.value && d1.value && d2.value <= d1.value) d2.value = isoDate(addDays(parseIso(d1.value), 1));
        renderSummary();
    });
    d2.addEventListener("input", renderSummary);
    form.querySelectorAll("input[name=opt]").forEach(c => c.addEventListener("change", renderSummary));
    await loadBooked();

    form.addEventListener("submit", async e => {
        e.preventDefault();
        const options = chosenOptions().map(e => e.idEquipement);
        const payload = { idVehicule: sel.value ? Number(sel.value) : null, dateDebut: d1.value || null, dateFin: d2.value || null, idEquipements: options };
        if (!state.user) {
            saveDraft({ vehicule: sel.value, debut: d1.value, fin: d2.value, options });
            location.hash = "#/connexion?next=reserver";
            return;
        }
        const btn = document.getElementById("book-btn");
        btn.disabled = true;
        try {
            const r = await api.post("/api/reservations", payload);
            toast(`Réservation n°${r.idReservation} enregistrée — ${euro(r.montantTotal)}`, "ok");
            location.hash = "#/client";
        } catch (err) {
            if (err.status === 401) { saveDraft({ vehicule: sel.value, debut: d1.value, fin: d2.value, options }); location.hash = "#/connexion?next=reserver"; return; }
            showFormErrors(form, err);
            btn.disabled = false;
            loadBooked();
        }
    });
}

// ---------- Agencies ----------
async function pageAgences() {
    app.innerHTML = `<div class="container">
        <div class="page-head"><div class="eyebrow">Réseau</div><h1>Nos agences</h1>
            <p class="muted">Retirez et rendez votre véhicule dans l'agence de votre choix.</p></div>
        <div id="list">${loading()}</div></div>`;
    try {
        const agences = await api.get("/api/agences");
        document.getElementById("list").innerHTML = agences.length
            ? `<div class="grid">${agences.map(a => `
                <article class="agency">
                    <span class="chip agency-city">📍 ${esc(a.ville)}</span>
                    <h3>${esc(a.nom)}</h3>
                    <div class="line">🏢 <span>${esc(a.adresse)}</span></div>
                    ${a.telephone ? `<div class="line">📞 <a href="tel:${esc(a.telephone.replace(/\s/g, ""))}">${esc(a.telephone)}</a></div>` : ""}
                    <a class="btn btn-light btn-sm" style="align-self:flex-start;margin-top:6px" target="_blank" rel="noopener"
                       href="https://www.openstreetmap.org/search?query=${encodeURIComponent(a.adresse)}">Voir sur la carte ↗</a>
                </article>`).join("")}</div>`
            : `<div class="empty">Aucune agence pour le moment.</div>`;
    } catch (err) {
        document.getElementById("list").innerHTML = errorBox(err);
    }
}

// ---------- Login / sign-up ----------
function pageConnexion(params) {
    const next = params.get("next") || "";
    if (state.user) { location.hash = state.user.role === "ADMIN" ? "#/admin" : `#/${next || "client"}`; return; }
    let mode = params.get("mode") === "inscription" ? "inscription" : "connexion";

    const render = () => {
        app.innerHTML = `<div class="container"><div class="auth">
            <div class="page-head" style="text-align:center"><div class="eyebrow">Mon compte</div>
                <h1>${mode === "connexion" ? "Connexion" : "Créer un compte"}</h1></div>
            <div class="tabs">
                <button class="tab ${mode === "connexion" ? "active" : ""}" data-mode="connexion">J'ai un compte</button>
                <button class="tab ${mode === "inscription" ? "active" : ""}" data-mode="inscription">Je m'inscris</button>
            </div>
            ${mode === "connexion" ? `
            <form class="panel" id="auth-form" novalidate>
                ${field("email", "Email", "email", "", `autocomplete="username" required`)}
                <div style="height:12px"></div>
                ${passwordField("motDePasse", "Mot de passe", `autocomplete="current-password" required`)}
                <div class="form-actions"><button class="btn btn-primary" type="submit" style="width:100%">Se connecter</button></div>
                <p class="secure-note">🔒 Connexion sécurisée — votre mot de passe n'est jamais stocké en clair.</p>
            </form>` : `
            <form class="panel" id="auth-form" novalidate>
                <div class="form-grid">
                    ${field("prenom", "Prénom", "text", "", `autocomplete="given-name" required`)}
                    ${field("nom", "Nom", "text", "", `autocomplete="family-name" required`)}
                    ${field("email", "Email", "email", "", `autocomplete="email" required`, "full")}
                    ${field("telephone", "Téléphone (facultatif)", "tel", "", `autocomplete="tel"`)}
                    ${field("numPermis", "N° de permis de conduire", "text", "", "required")}
                    <div class="full">${passwordField("motDePasse", "Mot de passe (8 caractères min., lettres et chiffres)", `autocomplete="new-password" required`, true)}</div>
                    <div class="full">${passwordField("confirmation", "Confirmer le mot de passe", `autocomplete="new-password" required`)}</div>
                </div>
                <div class="form-actions"><button class="btn btn-primary" type="submit" style="width:100%">Créer mon compte</button></div>
                <p class="secure-note">🔒 Votre mot de passe est chiffré avec BCrypt avant d'être enregistré.</p>
            </form>`}
        </div></div>`;

        document.querySelectorAll("[data-mode]").forEach(t => t.addEventListener("click", () => { mode = t.dataset.mode; render(); }));
        const form = document.getElementById("auth-form");
        bindPasswordToggles(form);

        if (mode === "inscription") {
            const pw = form.elements.motDePasse;
            pw.addEventListener("input", () => {
                const v = pw.value;
                const score = [v.length >= 8, /[a-z]/.test(v) && /[A-Z]/.test(v), /\d/.test(v), /[^A-Za-z0-9]/.test(v), v.length >= 12]
                    .filter(Boolean).length;
                const levels = [["", "0%"], ["Très faible", "20%", "#dc2626"], ["Faible", "40%", "#ea580c"], ["Moyen", "60%", "#ca8a04"], ["Bon", "80%", "#16a34a"], ["Excellent", "100%", "#047857"]];
                const [label, width, color] = levels[score];
                const bar = document.getElementById("strength-bar");
                bar.style.width = v ? width : "0%";
                bar.style.background = color || "transparent";
                document.getElementById("strength-label").textContent = v ? `Sécurité : ${label}` : "";
            });
        }

        form.addEventListener("submit", async e => {
            e.preventDefault();
            const d = formData(form);
            if (mode === "inscription" && d.motDePasse !== d.confirmation) {
                showFormErrors(form, { message: "Les mots de passe ne correspondent pas", errors: { confirmation: "Les mots de passe ne correspondent pas" } });
                return;
            }
            delete d.confirmation;
            const btn = form.querySelector("button[type=submit]");
            btn.disabled = true;
            try {
                state.user = await api.post(mode === "connexion" ? "/api/auth/login" : "/api/auth/register", d);
                renderNav();
                toast(`Bienvenue ${state.user.prenom} !`, "ok");
                location.hash = state.user.role === "ADMIN" ? "#/admin" : `#/${next || "client"}`;
            } catch (err) {
                showFormErrors(form, err);
                btn.disabled = false;
            }
        });
    };
    render();
}

// ---------- Client space ----------
let clientTab = "reservations";

async function pageClient() {
    if (!requireLogin("CLIENT", "client")) return;
    const u = state.user;
    app.innerHTML = `<div class="container">
        <div class="page-head"><div class="eyebrow">Espace client</div><h1>Mon espace</h1></div>
        <div class="profile">
            <span class="avatar">${esc(initials(u))}</span>
            <div class="profile-info"><strong style="font-size:1.1rem">${esc(u.prenom)} ${esc(u.nom)}</strong><br><span class="muted">${esc(u.email)}</span></div>
            <a href="#/reserver" class="btn btn-primary">+ Nouvelle réservation</a>
        </div>
        <div class="tabs" id="ctabs"></div>
        <div id="cbody">${loading()}</div></div>`;
    renderClientTabs();
}

function renderClientTabs() {
    const tabs = [["reservations", "Mes réservations"], ["contrats", "Contrats & paiements"], ["compte", "Mon compte"]];
    document.getElementById("ctabs").innerHTML = tabs.map(([k, l]) =>
        `<button class="tab ${clientTab === k ? "active" : ""}" data-tab="${k}">${l}</button>`).join("");
    document.querySelectorAll("#ctabs .tab").forEach(t => t.addEventListener("click", () => { clientTab = t.dataset.tab; renderClientTabs(); }));
    ({ reservations: renderClientResas, contrats: renderClientContrats, compte: renderCompte })[clientTab]();
}

const optionsText = r => r.equipements?.length ? r.equipements.map(e => esc(e.libelle)).join(", ") : "";
const parJourResa = r => Number(r.vehicule.tarifJournalier) + (r.equipements || []).reduce((t, e) => t + Number(e.tarifJournalier), 0);

async function renderClientResas() {
    const box = document.getElementById("cbody");
    if (!box) return;
    box.innerHTML = loading();
    let resas;
    try { resas = await api.get("/api/reservations/mine"); }
    catch (err) {
        if (err.status === 401) { location.hash = "#/connexion?next=client"; return; }
        box.innerHTML = errorBox(err); return;
    }
    if (!resas.length) {
        box.innerHTML = `<div class="empty">Vous n'avez pas encore de réservation.<br><br><a href="#/reserver" class="btn btn-primary">Réserver une voiture</a></div>`;
        return;
    }
    box.innerHTML = `<div class="resa-list">${resas.map(r => {
        const v = r.vehicule, active = ACTIVES.includes(r.statut);
        const canCancel = active && r.dateDebut > today();
        return `<article class="resa">
            <div class="resa-media media-${esc(v.categorie)}">${carSvg(v.categorie)}</div>
            <div>
                <span class="badge badge-${esc(r.statut)}">${esc(STATUTS_RESA[r.statut])}</span>
                <h3 style="margin-top:8px">${esc(v.marque)} ${esc(v.modele)} <span class="muted small">· réservation n°${r.idReservation}</span></h3>
                <div class="resa-dates">
                    <span>Prise : <strong>${fmtDate(r.dateDebut)}</strong></span>
                    <span>Retour : <strong>${fmtDate(r.dateFin)}</strong></span>
                    <span>${plural(daysBetween(r.dateDebut, r.dateFin), "jour")}</span>
                    ${v.agence ? `<span>📍 ${esc(v.agence.nom)}</span>` : ""}
                    ${r.nbProlongations ? `<span>⏩ prolongée ${r.nbProlongations} fois</span>` : ""}
                </div>
                ${optionsText(r) ? `<div class="muted small" style="margin-top:6px">Options : ${optionsText(r)}</div>` : ""}
            </div>
            <div class="resa-side">
                <span class="price">${euro(r.montantTotal)}</span>
                <div class="resa-actions">
                    ${active ? `<button class="btn btn-primary btn-sm" data-prolong="${r.idReservation}">Prolonger</button>` : ""}
                    ${canCancel ? `<button class="btn btn-danger btn-sm" data-cancel="${r.idReservation}">Annuler</button>` : ""}
                </div>
            </div>
        </article>`;
    }).join("")}</div>`;

    box.querySelectorAll("[data-prolong]").forEach(b => b.addEventListener("click", () =>
        prolongForm(resas.find(r => r.idReservation === Number(b.dataset.prolong)), renderClientResas)));
    box.querySelectorAll("[data-cancel]").forEach(b => b.addEventListener("click", async () => {
        const r = resas.find(x => x.idReservation === Number(b.dataset.cancel));
        if (!confirm(`Annuler la réservation n°${r.idReservation} (${r.vehicule.marque} ${r.vehicule.modele}) ?`)) return;
        try { await api.patch(`/api/reservations/${r.idReservation}/annuler`); toast("Réservation annulée", "ok"); renderClientResas(); }
        catch (err) { toast(err.message, "ko"); }
    }));
}

async function renderClientContrats() {
    const box = document.getElementById("cbody");
    if (!box) return;
    box.innerHTML = loading();
    let contrats;
    try { contrats = await api.get("/api/contrats/mine"); }
    catch (err) { box.innerHTML = errorBox(err); return; }
    if (!contrats.length) {
        box.innerHTML = `<div class="empty">Aucun contrat pour le moment.<br><small>Un contrat est créé quand l'agence confirme votre réservation.</small></div>`;
        return;
    }
    box.innerHTML = `<div class="table-wrap"><table>
        <thead><tr><th>Contrat</th><th>Véhicule</th><th>Période</th><th>Montant</th><th>Payé</th><th>Reste</th><th>État</th><th></th></tr></thead>
        <tbody>${contrats.map(({ contrat: c, totalPaye, resteAPayer }) => `<tr>
            <td>#${c.idContrat}<br><span class="muted small">signé le ${fmtDate(c.dateSignature)}</span></td>
            <td>${esc(c.reservation.vehicule.marque)} ${esc(c.reservation.vehicule.modele)}</td>
            <td>${fmtDate(c.reservation.dateDebut)} → ${fmtDate(c.reservation.dateFin)}</td>
            <td><strong>${euro(c.montantTotal)}</strong></td>
            <td>${euro(totalPaye)}</td>
            <td>${Number(resteAPayer) > 0 ? `<strong style="color:var(--warn)">${euro(resteAPayer)}</strong>` : `<span class="badge badge-CONFIRMEE">Soldé</span>`}</td>
            <td>${c.valide ? `<span class="badge badge-CONFIRMEE">Valide</span>` : `<span class="badge badge-ANNULEE">Annulé</span>`}</td>
            <td><div class="actions"><button class="btn btn-light btn-sm" data-pay="${c.idContrat}">Paiements</button></div></td>
        </tr>`).join("")}</tbody></table></div>
        <p class="muted small" style="margin-top:10px">Les paiements sont enregistrés par l'agence lors du retrait du véhicule.</p>`;
    box.querySelectorAll("[data-pay]").forEach(b => b.addEventListener("click", () => showPaiements(Number(b.dataset.pay))));
}

async function showPaiements(idContrat, canAdd = false, onDone = null, reste = 0) {
    openModal(`<h2>Paiements du contrat #${idContrat}</h2><div id="pay-list">${loading()}</div>
        ${canAdd ? `<form id="payform" novalidate style="margin-top:18px">
            <h3 style="font-size:1rem">Enregistrer un paiement</h3>
            <div class="form-grid">
                ${field("montant", `Montant (reste ${euro(reste)})`, "number", reste > 0 ? reste.toFixed(2) : "", `step="0.01" min="0.01" required`)}
                ${selectField("modePaiement", "Mode de paiement", MODES_PAIEMENT, "CARTE")}
            </div>
            <div class="form-actions"><button class="btn btn-primary" type="submit">Enregistrer</button></div>
        </form>` : ""}`);
    try {
        const list = await api.get(`/api/contrats/${idContrat}/paiements`);
        document.getElementById("pay-list").innerHTML = list.length
            ? list.map(p => `<div class="agency-mini"><span>${fmtDate(p.datePaiement)} · ${esc(MODES_PAIEMENT[p.modePaiement])}</span><strong>${euro(p.montant)}</strong></div>`).join("")
            : `<p class="muted">Aucun paiement enregistré.</p>`;
    } catch (err) { document.getElementById("pay-list").innerHTML = `<p class="muted">${esc(err.message)}</p>`; }
    const form = document.getElementById("payform");
    if (form) form.addEventListener("submit", async e => {
        e.preventDefault();
        const d = formData(form);
        try {
            await api.post(`/api/contrats/${idContrat}/paiements`, { montant: d.montant === "" ? null : Number(d.montant), modePaiement: d.modePaiement });
            closeModal();
            toast("Paiement enregistré", "ok");
            onDone && onDone();
        } catch (err) { showFormErrors(form, err); }
    });
}

/** Change my password (client or admin). */
function renderCompte(targetId = "cbody") {
    const box = document.getElementById(targetId);
    if (!box) return;
    box.innerHTML = `<form class="panel" id="pwform" novalidate style="max-width:520px">
        <h2 style="font-size:1.15rem">Changer mon mot de passe</h2>
        ${passwordField("ancienMotDePasse", "Mot de passe actuel", `autocomplete="current-password" required`)}
        <div style="height:10px"></div>
        ${passwordField("nouveauMotDePasse", "Nouveau mot de passe (8 caractères min., lettres et chiffres)", `autocomplete="new-password" required`)}
        <div style="height:10px"></div>
        ${passwordField("confirmation", "Confirmer le nouveau mot de passe", `autocomplete="new-password" required`)}
        <div class="form-actions"><button class="btn btn-primary" type="submit">Mettre à jour</button></div>
        <p class="secure-note">🔒 Le nouveau mot de passe est chiffré avec BCrypt.</p>
    </form>`;
    const form = document.getElementById("pwform");
    bindPasswordToggles(form);
    form.addEventListener("submit", async e => {
        e.preventDefault();
        const d = formData(form);
        if (d.nouveauMotDePasse !== d.confirmation) {
            showFormErrors(form, { message: "Les mots de passe ne correspondent pas", errors: { confirmation: "Les mots de passe ne correspondent pas" } });
            return;
        }
        try {
            await api.patch("/api/auth/password", { ancienMotDePasse: d.ancienMotDePasse, nouveauMotDePasse: d.nouveauMotDePasse });
            form.reset();
            toast("Mot de passe modifié", "ok");
        } catch (err) { showFormErrors(form, err); }
    });
}

/** Modal to extend a rental: pick the new return day, see the extra cost (car + options). */
function prolongForm(r, onDone) {
    const v = r.vehicule, parJour = parJourResa(r);
    const minFin = isoDate(addDays(parseIso(r.dateFin), 1));
    openModal(`<h2>Prolonger la location</h2>
        <p class="muted">${esc(v.marque)} ${esc(v.modele)} · retour prévu le <strong>${fmtDate(r.dateFin)}</strong></p>
        <form id="pform" novalidate>
            ${field("nouvelleDateFin", "Nouvelle date de retour", "date", isoDate(addDays(parseIso(r.dateFin), 2)), `min="${minFin}" required`)}
            <div class="estimate" style="margin-top:14px">
                <div class="summary-line"><span class="muted">Jours supplémentaires</span><strong id="p-days">—</strong></div>
                <div class="summary-line"><span class="muted">Supplément</span><strong id="p-extra">—</strong></div>
                <div class="summary-total"><span>Nouveau total</span><strong id="p-total">—</strong></div>
            </div>
            <div class="form-actions">
                <button type="button" class="btn btn-light" data-close>Annuler</button>
                <button type="submit" class="btn btn-primary">Confirmer la prolongation</button>
            </div>
        </form>`);
    const form = document.getElementById("pform");
    const input = form.elements.nouvelleDateFin;
    const update = () => {
        const extra = input.value ? daysBetween(r.dateFin, input.value) : 0;
        const ok = extra > 0;
        document.getElementById("p-days").textContent = ok ? `+ ${plural(extra, "jour")}` : "—";
        document.getElementById("p-extra").textContent = ok ? `+ ${euro(extra * parJour)}` : "—";
        document.getElementById("p-total").textContent = ok ? euro(daysBetween(r.dateDebut, input.value) * parJour) : "—";
        form.querySelector("button[type=submit]").disabled = !ok;
    };
    input.addEventListener("input", update);
    update();
    form.addEventListener("submit", async e => {
        e.preventDefault();
        try {
            const res = await api.patch(`/api/reservations/${r.idReservation}/prolonger`, { nouvelleDateFin: input.value });
            closeModal();
            toast(`Location prolongée jusqu'au ${fmtDate(res.dateFin)}`, "ok");
            onDone();
        } catch (err) { showFormErrors(form, err); }
    });
}

// ---------- Admin ----------
let adminTab = "reservations";
const pages = { reservations: 0, clients: 0 };
const ADMIN_TABS = [
    ["reservations", "Réservations"], ["contrats", "Contrats"], ["vehicules", "Véhicules"], ["maintenances", "Maintenances"],
    ["agences", "Agences"], ["equipements", "Équipements"], ["employes", "Employés"], ["clients", "Clients"], ["compte", "Mon compte"]
];

async function pageAdmin() {
    if (!requireLogin("ADMIN", "admin")) return;
    app.innerHTML = `<div class="container">
        <div class="page-head"><div class="eyebrow">Back-office</div><h1>Administration</h1>
            <p class="muted">Connecté en tant que ${esc(state.user.prenom)} ${esc(state.user.nom)} · toutes les modifications sont enregistrées dans MySQL.</p></div>
        <div id="kpis"></div>
        <div class="tabs" id="tabs"></div>
        <div id="admin-body">${loading()}</div></div>`;
    await renderAdmin();
}

async function renderAdmin() {
    const body = document.getElementById("admin-body");
    if (!body) return;
    document.getElementById("tabs").innerHTML = ADMIN_TABS.map(([k, l]) =>
        `<button class="tab ${adminTab === k ? "active" : ""}" data-tab="${k}">${l}</button>`).join("");
    document.querySelectorAll("#tabs .tab").forEach(t => t.addEventListener("click", () => { adminTab = t.dataset.tab; renderAdmin(); }));

    try {
        const s = await api.get("/api/admin/stats");
        document.getElementById("kpis").innerHTML = `<div class="admin-kpis">
            <div class="stat"><div class="stat-value">${s.reservationsEnCours}</div><div class="stat-label">réservations en cours</div></div>
            <div class="stat"><div class="stat-value">${s.reservationsAConfirmer}</div><div class="stat-label">à confirmer</div></div>
            <div class="stat"><div class="stat-value">${s.clients}</div><div class="stat-label">clients inscrits</div></div>
            <div class="stat"><div class="stat-value">${euro(s.chiffreAffaires)}</div><div class="stat-label">chiffre d'affaires réservé</div></div>
        </div>`;
        if (adminTab === "compte") { renderCompte("admin-body"); return; }
        body.innerHTML = loading();
        await ({
            reservations: adminResas, contrats: adminContrats, vehicules: adminVehicules, maintenances: adminMaintenances,
            agences: adminAgences, equipements: adminEquipements, employes: adminEmployes, clients: adminClients
        })[adminTab](body);
    } catch (err) {
        if (err.status === 401 || err.status === 403) { location.hash = "#/connexion?next=admin"; return; }
        body.innerHTML = errorBox(err);
    }
}

function pager(key, p) {
    if (p.totalPages <= 1) return "";
    return `<div class="pager">
        <button class="btn btn-light btn-sm" data-page="${p.page - 1}" ${p.page === 0 ? "disabled" : ""}>← Précédent</button>
        <span class="muted small">Page ${p.page + 1} / ${p.totalPages} · ${p.totalElements} élément(s)</span>
        <button class="btn btn-light btn-sm" data-page="${p.page + 1}" ${p.page + 1 >= p.totalPages ? "disabled" : ""}>Suivant →</button>
    </div>`;
}
function bindPager(body, key) {
    body.querySelectorAll("[data-page]").forEach(b => b.addEventListener("click", () => { pages[key] = Number(b.dataset.page); renderAdmin(); }));
}
const emptyRow = (cols, text) => `<tr><td colspan="${cols}" class="muted" style="text-align:center">${esc(text)}</td></tr>`;

async function adminResas(body) {
    const p = await api.get(`/api/reservations?page=${pages.reservations}&size=15`);
    const resas = p.content;
    body.innerHTML = `
        <div class="admin-head"><strong>${plural(p.totalElements, "réservation")}</strong><span class="muted small">Confirmer une réservation crée automatiquement son contrat.</span></div>
        <div class="table-wrap"><table>
            <thead><tr><th>N°</th><th>Client</th><th>Véhicule</th><th>Prise</th><th>Retour</th><th>Montant</th><th>Statut</th><th></th></tr></thead>
            <tbody>${resas.length ? resas.map(r => `<tr>
                <td>#${r.idReservation}</td>
                <td><strong>${esc(r.client.prenom)} ${esc(r.client.nom)}</strong><br><span class="muted small">${esc(r.client.email)}</span></td>
                <td>${esc(r.vehicule.marque)} ${esc(r.vehicule.modele)}<br><span class="muted small">${esc(r.vehicule.immatriculation)}${optionsText(r) ? " · " + optionsText(r) : ""}</span></td>
                <td>${fmtDate(r.dateDebut)}</td>
                <td>${fmtDate(r.dateFin)}${r.nbProlongations ? `<br><span class="muted small">⏩ prolongée ×${r.nbProlongations}</span>` : ""}</td>
                <td><strong>${euro(r.montantTotal)}</strong></td>
                <td><select class="input" data-rstatut="${r.idReservation}">${options(STATUTS_RESA, r.statut)}</select></td>
                <td><div class="actions">${ACTIVES.includes(r.statut) ? `<button class="btn btn-light btn-sm" data-prolong="${r.idReservation}">Prolonger</button>` : ""}</div></td>
            </tr>`).join("") : emptyRow(8, "Aucune réservation pour le moment")}</tbody>
        </table></div>${pager("reservations", p)}`;
    bindPager(body, "reservations");
    body.querySelectorAll("[data-rstatut]").forEach(s => s.addEventListener("change", async () => {
        try {
            await api.patch(`/api/reservations/${s.dataset.rstatut}/statut`, { statut: s.value });
            toast(`Réservation : ${STATUTS_RESA[s.value]}${s.value === "CONFIRMEE" ? " — contrat créé" : ""}`, "ok");
        } catch (err) { toast(err.message, "ko"); }
        renderAdmin();
    }));
    body.querySelectorAll("[data-prolong]").forEach(b => b.addEventListener("click", () =>
        prolongForm(resas.find(r => r.idReservation === Number(b.dataset.prolong)), renderAdmin)));
}

async function adminContrats(body) {
    const contrats = await api.get("/api/contrats");
    body.innerHTML = `
        <div class="admin-head"><strong>${plural(contrats.length, "contrat")}</strong></div>
        <div class="table-wrap"><table>
            <thead><tr><th>Contrat</th><th>Client</th><th>Véhicule</th><th>Montant</th><th>Payé</th><th>Reste</th><th>État</th><th></th></tr></thead>
            <tbody>${contrats.length ? contrats.map(({ contrat: c, totalPaye, resteAPayer }) => `<tr>
                <td>#${c.idContrat}<br><span class="muted small">résa #${c.reservation.idReservation} · ${fmtDate(c.dateSignature)}</span></td>
                <td>${esc(c.reservation.client.prenom)} ${esc(c.reservation.client.nom)}</td>
                <td>${esc(c.reservation.vehicule.marque)} ${esc(c.reservation.vehicule.modele)}</td>
                <td><strong>${euro(c.montantTotal)}</strong></td>
                <td>${euro(totalPaye)}</td>
                <td>${Number(resteAPayer) > 0 ? euro(resteAPayer) : `<span class="badge badge-CONFIRMEE">Soldé</span>`}</td>
                <td>${c.valide ? `<span class="badge badge-CONFIRMEE">Valide</span>` : `<span class="badge badge-ANNULEE">Invalide</span>`}</td>
                <td><div class="actions">
                    <button class="btn btn-light btn-sm" data-pay="${c.idContrat}" data-reste="${resteAPayer}" data-valide="${c.valide}">Paiements</button>
                    ${c.valide ? `<button class="btn btn-danger btn-sm" data-inval="${c.idContrat}">Invalider</button>` : ""}
                </div></td>
            </tr>`).join("") : emptyRow(8, "Aucun contrat : confirmez une réservation pour en créer un")}</tbody>
        </table></div>`;
    body.querySelectorAll("[data-pay]").forEach(b => b.addEventListener("click", () =>
        showPaiements(Number(b.dataset.pay), b.dataset.valide === "true" && Number(b.dataset.reste) > 0, renderAdmin, Number(b.dataset.reste))));
    body.querySelectorAll("[data-inval]").forEach(b => b.addEventListener("click", async () => {
        if (!confirm(`Invalider le contrat #${b.dataset.inval} ?`)) return;
        try { await api.patch(`/api/contrats/${b.dataset.inval}/invalider`); toast("Contrat invalidé", "ok"); renderAdmin(); }
        catch (err) { toast(err.message, "ko"); }
    }));
}

async function adminVehicules(body) {
    const [vehicules, agences] = await Promise.all([api.get("/api/vehicules"), api.get("/api/agences")]);
    body.innerHTML = `
        <div class="admin-head"><strong>${plural(vehicules.length, "véhicule")}</strong>
            <button class="btn btn-primary" id="add">+ Ajouter un véhicule</button></div>
        <div class="table-wrap"><table>
            <thead><tr><th>Véhicule</th><th>Immatriculation</th><th>Catégorie</th><th>Agence</th><th>Tarif / jour</th><th>Statut</th><th></th></tr></thead>
            <tbody>${vehicules.length ? vehicules.map(v => `<tr>
                <td><strong>${esc(v.marque)} ${esc(v.modele)}</strong></td>
                <td><span class="chip plate">${esc(v.immatriculation)}</span></td>
                <td>${esc(CATEGORIES[v.categorie])}</td>
                <td>${v.agence ? esc(v.agence.ville) : "—"}</td>
                <td>${euro(v.tarifJournalier)}</td>
                <td><select class="input" data-statut="${v.idVehicule}">${options(STATUTS, v.statut)}</select></td>
                <td><div class="actions">
                    <button class="btn btn-light btn-sm" data-edit="${v.idVehicule}">Modifier</button>
                    <button class="btn btn-danger btn-sm" data-del="${v.idVehicule}">Supprimer</button>
                </div></td></tr>`).join("") : emptyRow(7, "Aucun véhicule")}</tbody>
        </table></div>`;

    const agenceMap = Object.fromEntries(agences.map(a => [String(a.idAgence), `${a.nom} (${a.ville})`]));
    const form = (v = null) => {
        openModal(`<h2>${v ? "Modifier le véhicule" : "Ajouter un véhicule"}</h2>
            <form id="vform" novalidate><div class="form-grid">
                ${field("marque", "Marque", "text", v?.marque, "required")}
                ${field("modele", "Modèle", "text", v?.modele, "required")}
                ${field("immatriculation", "Immatriculation", "text", v?.immatriculation, `placeholder="AB-123-CD" required`)}
                ${field("tarifJournalier", "Tarif journalier (€)", "number", v?.tarifJournalier, `step="0.01" min="0.01" required`)}
                ${selectField("categorie", "Catégorie", CATEGORIES, v?.categorie || "CITADINE")}
                ${selectField("statut", "Statut", STATUTS, v?.statut || "DISPONIBLE")}
                <div class="field full"><label for="fld-idAgence">Agence</label>
                    <select class="input" id="fld-idAgence" name="idAgence">${options(agenceMap, v?.agence ? String(v.agence.idAgence) : "", "Aucune agence")}</select></div>
            </div>
            <div class="form-actions">
                <button type="button" class="btn btn-light" data-close>Annuler</button>
                <button type="submit" class="btn btn-primary">${v ? "Enregistrer" : "Ajouter"}</button>
            </div></form>`);
        const f = document.getElementById("vform");
        f.addEventListener("submit", async e => {
            e.preventDefault();
            const d = formData(f);
            d.tarifJournalier = d.tarifJournalier === "" ? null : Number(d.tarifJournalier);
            d.idAgence = d.idAgence ? Number(d.idAgence) : null;
            try {
                if (v) await api.put(`/api/vehicules/${v.idVehicule}`, d); else await api.post("/api/vehicules", d);
                closeModal(); toast(v ? "Véhicule modifié" : "Véhicule ajouté", "ok"); renderAdmin();
            } catch (err) { showFormErrors(f, err); }
        });
    };
    body.querySelector("#add").addEventListener("click", () => form());
    body.querySelectorAll("[data-edit]").forEach(b => b.addEventListener("click", () => form(vehicules.find(v => v.idVehicule === Number(b.dataset.edit)))));
    body.querySelectorAll("[data-statut]").forEach(s => s.addEventListener("change", async () => {
        try { await api.patch(`/api/vehicules/${s.dataset.statut}/statut`, { statut: s.value }); toast(`Statut changé : ${STATUTS[s.value]}`, "ok"); }
        catch (err) { toast(err.message, "ko"); renderAdmin(); }
    }));
    body.querySelectorAll("[data-del]").forEach(b => b.addEventListener("click", async () => {
        const v = vehicules.find(x => x.idVehicule === Number(b.dataset.del));
        if (!confirm(`Supprimer définitivement ${v.marque} ${v.modele} (${v.immatriculation}) et son historique de maintenance ?`)) return;
        try { await api.del(`/api/vehicules/${v.idVehicule}`); toast("Véhicule supprimé", "ok"); renderAdmin(); }
        catch (err) { toast(err.message, "ko"); }
    }));
}

async function adminMaintenances(body) {
    const [maintenances, vehicules] = await Promise.all([api.get("/api/maintenances"), api.get("/api/vehicules")]);
    body.innerHTML = `
        <div class="admin-head"><strong>${plural(maintenances.length, "maintenance")}</strong>
            <button class="btn btn-primary" id="add">+ Mettre un véhicule en maintenance</button></div>
        <div class="table-wrap"><table>
            <thead><tr><th>Véhicule</th><th>Début</th><th>Fin</th><th>Description</th><th>État</th><th></th></tr></thead>
            <tbody>${maintenances.length ? maintenances.map(m => `<tr>
                <td><strong>${esc(m.vehicule.marque)} ${esc(m.vehicule.modele)}</strong><br><span class="muted small">${esc(m.vehicule.immatriculation)}</span></td>
                <td>${fmtDate(m.dateDebut)}</td><td>${fmtDate(m.dateFin)}</td>
                <td style="white-space:normal;min-width:200px">${esc(m.description || "—")}</td>
                <td>${m.dateFin ? `<span class="badge badge-TERMINEE">Terminée</span>` : `<span class="badge badge-MAINTENANCE">En cours</span>`}</td>
                <td><div class="actions">${m.dateFin
                    ? `<button class="btn btn-danger btn-sm" data-del="${m.idMaintenance}">Supprimer</button>`
                    : `<button class="btn btn-primary btn-sm" data-end="${m.idMaintenance}">Terminer</button>`}</div></td>
            </tr>`).join("") : emptyRow(6, "Aucune maintenance")}</tbody>
        </table></div>`;
    body.querySelector("#add").addEventListener("click", () => {
        const map = Object.fromEntries(vehicules.filter(v => v.statut !== "MAINTENANCE").map(v => [String(v.idVehicule), `${v.marque} ${v.modele} (${v.immatriculation})`]));
        openModal(`<h2>Mettre un véhicule en maintenance</h2>
            <form id="mform" novalidate><div class="form-grid">
                <div class="field full"><label for="fld-idVehicule">Véhicule</label>
                    <select class="input" id="fld-idVehicule" name="idVehicule">${options(map, "", "Choisir un véhicule")}</select>
                    <span class="field-error" data-error-for="idVehicule"></span></div>
                ${field("dateDebut", "Début", "date", today())}
                <div class="field full"><label for="fld-description">Description</label>
                    <textarea class="input" id="fld-description" name="description" rows="3" maxlength="500" placeholder="Révision, pneus, carrosserie…"></textarea></div>
            </div>
            <div class="form-actions"><button type="button" class="btn btn-light" data-close>Annuler</button>
                <button type="submit" class="btn btn-primary">Enregistrer</button></div></form>`);
        const f = document.getElementById("mform");
        f.addEventListener("submit", async e => {
            e.preventDefault();
            const d = formData(f);
            try {
                await api.post("/api/maintenances", { idVehicule: d.idVehicule ? Number(d.idVehicule) : null, dateDebut: d.dateDebut || null, description: d.description });
                closeModal(); toast("Véhicule passé en maintenance", "ok"); renderAdmin();
            } catch (err) { showFormErrors(f, err); }
        });
    });
    body.querySelectorAll("[data-end]").forEach(b => b.addEventListener("click", async () => {
        try { await api.patch(`/api/maintenances/${b.dataset.end}/terminer`, { dateFin: today() }); toast("Maintenance terminée : véhicule disponible", "ok"); renderAdmin(); }
        catch (err) { toast(err.message, "ko"); }
    }));
    body.querySelectorAll("[data-del]").forEach(b => b.addEventListener("click", async () => {
        if (!confirm("Supprimer cette maintenance de l'historique ?")) return;
        try { await api.del(`/api/maintenances/${b.dataset.del}`); toast("Maintenance supprimée", "ok"); renderAdmin(); }
        catch (err) { toast(err.message, "ko"); }
    }));
}

/** Generic table + modal form for simple entities (agencies, equipment, employees). */
function crudTable(body, { title, items, idKey, columns, fields, url, label, toPayload = d => d, deleteLabel }) {
    body.innerHTML = `
        <div class="admin-head"><strong>${plural(items.length, title)}</strong>
            <button class="btn btn-primary" id="add">+ Ajouter</button></div>
        <div class="table-wrap"><table>
            <thead><tr>${columns.map(c => `<th>${c[0]}</th>`).join("")}<th></th></tr></thead>
            <tbody>${items.length ? items.map(it => `<tr>${columns.map(c => `<td>${c[1](it)}</td>`).join("")}
                <td><div class="actions">
                    <button class="btn btn-light btn-sm" data-edit="${it[idKey]}">Modifier</button>
                    <button class="btn btn-danger btn-sm" data-del="${it[idKey]}">Supprimer</button>
                </div></td></tr>`).join("") : emptyRow(columns.length + 1, `Aucun(e) ${title}`)}</tbody>
        </table></div>`;
    const form = (it = null) => {
        openModal(`<h2>${it ? "Modifier" : "Ajouter"} : ${esc(label)}</h2>
            <form id="cform" novalidate><div class="form-grid">${fields(it)}</div>
            <div class="form-actions"><button type="button" class="btn btn-light" data-close>Annuler</button>
                <button type="submit" class="btn btn-primary">${it ? "Enregistrer" : "Ajouter"}</button></div></form>`);
        const f = document.getElementById("cform");
        bindPasswordToggles(f);
        f.addEventListener("submit", async e => {
            e.preventDefault();
            try {
                const d = toPayload(formData(f));
                if (it) await api.put(`${url}/${it[idKey]}`, d); else await api.post(url, d);
                closeModal(); toast(it ? "Modifié" : "Ajouté", "ok"); renderAdmin();
            } catch (err) { showFormErrors(f, err); }
        });
    };
    body.querySelector("#add").addEventListener("click", () => form());
    body.querySelectorAll("[data-edit]").forEach(b => b.addEventListener("click", () => form(items.find(i => String(i[idKey]) === b.dataset.edit))));
    body.querySelectorAll("[data-del]").forEach(b => b.addEventListener("click", async () => {
        const it = items.find(i => String(i[idKey]) === b.dataset.del);
        if (!confirm(`Supprimer définitivement ${deleteLabel(it)} ?`)) return;
        try { await api.del(`${url}/${it[idKey]}`); toast("Supprimé", "ok"); renderAdmin(); }
        catch (err) { toast(err.message, "ko"); }
    }));
}

async function adminAgences(body) {
    crudTable(body, {
        title: "agence", label: "agence", items: await api.get("/api/agences"), idKey: "idAgence", url: "/api/agences",
        columns: [["Nom", a => `<strong>${esc(a.nom)}</strong>`], ["Ville", a => esc(a.ville)], ["Adresse", a => esc(a.adresse)], ["Téléphone", a => esc(a.telephone || "—")]],
        fields: a => `${field("nom", "Nom", "text", a?.nom, "required", "full")}${field("ville", "Ville", "text", a?.ville, "required")}
            ${field("telephone", "Téléphone", "tel", a?.telephone)}${field("adresse", "Adresse", "text", a?.adresse, "required", "full")}`,
        deleteLabel: a => `l'agence « ${a.nom} »`
    });
}

async function adminEquipements(body) {
    crudTable(body, {
        title: "équipement", label: "équipement", items: await api.get("/api/equipements"), idKey: "idEquipement", url: "/api/equipements",
        columns: [["Libellé", e => `<strong>${esc(e.libelle)}</strong>`], ["Tarif / jour", e => euro(e.tarifJournalier)]],
        fields: e => `${field("libelle", "Libellé", "text", e?.libelle, "required", "full")}
            ${field("tarifJournalier", "Tarif journalier (€)", "number", e?.tarifJournalier, `step="0.01" min="0" required`)}`,
        toPayload: d => ({ libelle: d.libelle, tarifJournalier: d.tarifJournalier === "" ? null : Number(d.tarifJournalier) }),
        deleteLabel: e => `l'équipement « ${e.libelle} »`
    });
}

async function adminEmployes(body) {
    const [employes, agences] = await Promise.all([api.get("/api/employes"), api.get("/api/agences")]);
    const agenceMap = Object.fromEntries(agences.map(a => [String(a.idAgence), `${a.nom} (${a.ville})`]));
    crudTable(body, {
        title: "employé", label: "employé", items: employes, idKey: "idEmploye", url: "/api/employes",
        columns: [["Employé", e => `<strong>${esc(e.prenom)} ${esc(e.nom)}</strong>`], ["Email", e => esc(e.email)],
            ["Rôle", e => esc(ROLES[e.role])], ["Agence", e => e.agence ? esc(e.agence.ville) : "—"]],
        fields: e => `${field("prenom", "Prénom", "text", e?.prenom, "required")}${field("nom", "Nom", "text", e?.nom, "required")}
            ${field("email", "Email (identifiant)", "email", e?.email, "required", "full")}
            ${selectField("role", "Rôle", ROLES, e?.role || "AGENT")}
            <div class="field"><label for="fld-idAgence">Agence</label>
                <select class="input" id="fld-idAgence" name="idAgence">${options(agenceMap, e?.agence ? String(e.agence.idAgence) : "", "Aucune agence")}</select></div>
            <div class="full">${passwordField("motDePasse", e ? "Nouveau mot de passe (laisser vide pour ne pas changer)" : "Mot de passe", `autocomplete="new-password"`)}</div>`,
        toPayload: d => ({ ...d, idAgence: d.idAgence ? Number(d.idAgence) : null }),
        deleteLabel: e => `l'employé ${e.prenom} ${e.nom}`
    });
}

async function adminClients(body) {
    const p = await api.get(`/api/clients?page=${pages.clients}&size=15`);
    const clients = p.content;
    body.innerHTML = `
        <div class="admin-head"><strong>${plural(p.totalElements, "client")}</strong></div>
        <div class="table-wrap"><table>
            <thead><tr><th>Client</th><th>Email</th><th>Téléphone</th><th>N° permis</th><th>Inscrit le</th><th></th></tr></thead>
            <tbody>${clients.length ? clients.map(c => `<tr>
                <td><strong>${esc(c.prenom)} ${esc(c.nom)}</strong></td><td>${esc(c.email)}</td>
                <td>${esc(c.telephone || "—")}</td><td>${esc(c.numPermis)}</td>
                <td>${fmtDate(c.dateInscription)}</td>
                <td><div class="actions">
                    <button class="btn btn-light btn-sm" data-reset="${c.idClient}">Réinitialiser le mot de passe</button>
                    <button class="btn btn-danger btn-sm" data-del="${c.idClient}">Supprimer</button>
                </div></td></tr>`).join("") : emptyRow(6, "Aucun client inscrit")}</tbody>
        </table></div>${pager("clients", p)}`;
    bindPager(body, "clients");
    body.querySelectorAll("[data-reset]").forEach(b => b.addEventListener("click", async () => {
        const c = clients.find(x => x.idClient === Number(b.dataset.reset));
        if (!confirm(`Générer un mot de passe temporaire pour ${c.prenom} ${c.nom} ? L'ancien ne fonctionnera plus.`)) return;
        try {
            const r = await api.post(`/api/clients/${c.idClient}/reinitialiser-mot-de-passe`);
            openModal(`<h2>Mot de passe temporaire</h2>
                <p>Communiquez ce mot de passe à <strong>${esc(c.prenom)} ${esc(c.nom)}</strong> (il ne sera plus affiché) :</p>
                <p class="estimate" style="font-family:ui-monospace,Consolas,monospace;font-size:1.4rem;text-align:center;user-select:all">${esc(r.motDePasseTemporaire)}</p>
                <p class="muted small">Le client pourra le changer dans « Mon espace → Mon compte ».</p>
                <div class="form-actions"><button class="btn btn-primary" data-close>J'ai noté le mot de passe</button></div>`);
        } catch (err) { toast(err.message, "ko"); }
    }));
    body.querySelectorAll("[data-del]").forEach(b => b.addEventListener("click", async () => {
        const c = clients.find(x => x.idClient === Number(b.dataset.del));
        if (!confirm(`Supprimer définitivement le client ${c.prenom} ${c.nom} ?`)) return;
        try { await api.del(`/api/clients/${c.idClient}`); toast("Client supprimé", "ok"); renderAdmin(); }
        catch (err) { toast(err.message, "ko"); }
    }));
}

// ---------- Router ----------
const routes = {
    "": pageHome, vehicules: pageVehicules, reserver: pageReserver, agences: pageAgences,
    connexion: pageConnexion, client: pageClient, admin: pageAdmin
};

function route() {
    const [path, query] = location.hash.replace(/^#\/?/, "").split("?");
    const page = routes[path] ? path : "";
    closeModal();
    markActive();
    document.querySelector(".nav-links").classList.remove("open");
    window.scrollTo(0, 0);
    routes[page](new URLSearchParams(query || ""));
}

document.querySelector(".nav-toggle").addEventListener("click", e => {
    const open = document.querySelector(".nav-links").classList.toggle("open");
    e.currentTarget.setAttribute("aria-expanded", open);
});
window.addEventListener("hashchange", route);
loadUser().then(route);
