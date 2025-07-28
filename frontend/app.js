const BASE_URL = "http://localhost:8080"; // REST API URL
let authToken = localStorage.getItem("authToken") || "";

// Attach token to headers
function authHeaders() {
    return authToken
        ? { "Content-Type": "application/json", "auth-token": authToken }
        : { "Content-Type": "application/json" };
}

// Check if logged in
function checkAuth() {
    if (!authToken) {
        window.location.href = "login.html";
    }
}

// Logout
function logout() {
    localStorage.removeItem("authToken");
    authToken = "";
    window.location.href = "login.html";
}

// Signup
async function signup() {
    const username = document.getElementById("signupUsername").value.trim();
    const password = document.getElementById("signupPassword").value.trim();
    const resultElement = document.getElementById("signupResult");

    try {
        const response = await fetch(`${BASE_URL}/signup`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        const data = await response.json();
        resultElement.textContent = data.message || JSON.stringify(data);
    } catch (error) {
        resultElement.textContent = `Error: ${error.message}`;
    }
}

// Login
async function login() {
    const username = document.getElementById("loginUsername").value.trim();
    const password = document.getElementById("loginPassword").value.trim();
    const resultElement = document.getElementById("loginResult");

    try {
        const response = await fetch(`${BASE_URL}/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ username, password })
        });
        const data = await response.json();

        if (data.success) {
            authToken = data.token;
            localStorage.setItem("authToken", authToken);
            resultElement.textContent = `Logged in as ${username}`;
            window.location.href = "index.html"; // redirect to dashboard
        } else {
            resultElement.textContent = data.error || "Login failed";
        }
    } catch (error) {
        resultElement.textContent = `Error: ${error.message}`;
    }
}

// Helper for all fetch calls
async function fetchWithAuth(url, options) {
    options.headers = authHeaders();
    const response = await fetch(url, options);

    if (response.status === 401) {
        alert("Session expired. Please log in again.");
        logout();
        return;
    }
    return response;
}

// Insert Document
async function insertDocument() {
    const collection = document.getElementById("insertCollection").value.trim();
    const documentText = document.getElementById("insertDocument").value.trim();
    const resultElement = document.getElementById("insertResult");

    if (!collection || !documentText) {
        resultElement.textContent = "Collection and document are required.";
        resultElement.className = "text-red-600 font-mono text-sm";
        return;
    }

    setLoading("insertBtn", "insertSpinner", "insertBtnText", true);

    try {
        const url = `${BASE_URL}/insert?collection=${encodeURIComponent(collection)}`;
        const response = await fetchWithAuth(url, {
            method: "POST",
            body: documentText
        });

        if (!response) return;
        const text = await response.text();
        try {
            resultElement.textContent = JSON.stringify(JSON.parse(text), null, 2);
        } catch {
            resultElement.textContent = text;
        }
        resultElement.className = response.ok ? "text-green-600 font-mono text-sm" : "text-red-600 font-mono text-sm";
    } catch (error) {
        resultElement.textContent = `Network Error: ${error.message}`;
        resultElement.className = "text-red-600 font-mono text-sm";
    } finally {
        setLoading("insertBtn", "insertSpinner", "insertBtnText", false);
    }
}

// Get Document
async function getDocument() {
    const collection = document.getElementById("getCollection").value.trim();
    const id = document.getElementById("getId").value.trim();
    const resultElement = document.getElementById("getResult");

    if (!collection || !id) {
        resultElement.textContent = "Collection and ID are required.";
        resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
        return;
    }

    setLoading("getBtn", "getSpinner", "getBtnText", true);

    try {
        const url = `${BASE_URL}/get?collection=${encodeURIComponent(collection)}&id=${encodeURIComponent(id)}`;
        const response = await fetchWithAuth(url, { method: "GET" });
        if (!response) return;

        const text = await response.text();
        try {
            resultElement.textContent = JSON.stringify(JSON.parse(text), null, 2);
        } catch {
            resultElement.textContent = text;
        }
        resultElement.className = response.ok ? "text-green-600 font-mono text-sm whitespace-pre-wrap" : "text-red-600 font-mono text-sm whitespace-pre-wrap";
    } catch (error) {
        resultElement.textContent = `Network Error: ${error.message}`;
        resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
    } finally {
        setLoading("getBtn", "getSpinner", "getBtnText", false);
    }
}

// Query Documents
async function queryDocuments() {
    const collection = document.getElementById("queryCollection").value.trim();
    const field = document.getElementById("queryField").value.trim();
    const value = document.getElementById("queryValue").value.trim();
    const resultElement = document.getElementById("queryResult");

    if (!collection || !field || !value) {
        resultElement.textContent = "Collection, field, and value are required.";
        resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
        return;
    }

    setLoading("queryBtn", "querySpinner", "queryBtnText", true);

    try {
        const url = `${BASE_URL}/query?collection=${encodeURIComponent(collection)}&field=${encodeURIComponent(field)}&value=${encodeURIComponent(value)}`;
        const response = await fetchWithAuth(url, { method: "GET" });
        if (!response) return;

        const text = await response.text();
        try {
            const data = JSON.parse(text);
            resultElement.textContent = data.documents ? JSON.stringify(data.documents, null, 2) : JSON.stringify(data, null, 2);
        } catch {
            resultElement.textContent = text;
        }
        resultElement.className = response.ok ? "text-green-600 font-mono text-sm whitespace-pre-wrap" : "text-red-600 font-mono text-sm whitespace-pre-wrap";
    } catch (error) {
        resultElement.textContent = `Network Error: ${error.message}`;
        resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
    } finally {
        setLoading("queryBtn", "querySpinner", "queryBtnText", false);
    }
}

// Navigation
function showSection(sectionName) {
    document.querySelectorAll('.section').forEach(section => section.classList.add('hidden'));
    document.querySelectorAll('.sidebar-item').forEach(item => item.classList.remove('active'));
    document.getElementById(sectionName + '-section').classList.remove('hidden');
    event.target.classList.add('active');
}

// Loading spinner helper
function setLoading(buttonId, spinnerId, textId, isLoading) {
    const button = document.getElementById(buttonId);
    const spinner = document.getElementById(spinnerId);
    const text = document.getElementById(textId);

    if (isLoading) {
        button.disabled = true;
        spinner.classList.remove("hidden");
        text.classList.add("opacity-50");
    } else {
        button.disabled = false;
        spinner.classList.add("hidden");
        text.classList.remove("opacity-50");
    }
}
