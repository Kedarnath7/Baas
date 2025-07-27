const BASE_URL = "http://localhost:8080"; // REST API URL

// Navigation - Keep your original navigation working
function showSection(sectionName) {
    // Hide all sections
    document.querySelectorAll('.section').forEach(section => {
        section.classList.add('hidden');
    });

    // Remove active class from all sidebar items
    document.querySelectorAll('.sidebar-item').forEach(item => {
        item.classList.remove('active');
    });

    // Show selected section
    document.getElementById(sectionName + '-section').classList.remove('hidden');

    // Add active class to clicked sidebar item
    event.target.classList.add('active');
}

// Helper to toggle loading state - Keep your original loading logic
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

// Insert Document - Updated to work with your Java backend
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
        // Build URL exactly as your Java backend expects
        const url = `${BASE_URL}/insert?collection=${encodeURIComponent(collection)}`;
        
        const response = await fetch(url, {
            method: "POST",
            headers: { 
                "Content-Type": "application/json"
            },
            body: documentText
        });

        if (response.ok) {
            const responseText = await response.text();
            
            // Try to parse as JSON, if it fails show raw response
            try {
                const data = JSON.parse(responseText);
                resultElement.textContent = JSON.stringify(data, null, 2);
            } catch (e) {
                resultElement.textContent = responseText;
            }
            
            resultElement.className = "text-green-600 font-mono text-sm";
        } else {
            const errorText = await response.text();
            resultElement.textContent = `Error ${response.status}: ${errorText}`;
            resultElement.className = "text-red-600 font-mono text-sm";
        }

    } catch (error) {
        resultElement.textContent = `Network Error: ${error.message}`;
        resultElement.className = "text-red-600 font-mono text-sm";
    } finally {
        setLoading("insertBtn", "insertSpinner", "insertBtnText", false);
    }
}

// Get Document - Updated to work with your Java backend
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
        // Build URL exactly as your Java backend expects
        const url = `${BASE_URL}/get?collection=${encodeURIComponent(collection)}&id=${encodeURIComponent(id)}`;
        
        const response = await fetch(url);

        if (response.ok) {
            const responseText = await response.text();
            
            // Try to parse and format JSON for better display
            try {
                const data = JSON.parse(responseText);
                resultElement.textContent = JSON.stringify(data, null, 2);
            } catch (e) {
                // If not JSON, display as is (might be the document directly)
                resultElement.textContent = responseText;
            }
            
            resultElement.className = "text-green-600 font-mono text-sm whitespace-pre-wrap";
        } else {
            const errorText = await response.text();
            
            // Try to parse error response
            try {
                const errorData = JSON.parse(errorText);
                resultElement.textContent = errorData.error || errorText;
            } catch (e) {
                resultElement.textContent = `Error ${response.status}: ${errorText}`;
            }
            
            resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
        }

    } catch (error) {
        resultElement.textContent = `Network Error: ${error.message}`;
        resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
    } finally {
        setLoading("getBtn", "getSpinner", "getBtnText", false);
    }
}

// Query Documents - Updated to work with your Java backend
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
        // Build URL exactly as your Java backend expects
        const url = `${BASE_URL}/query?collection=${encodeURIComponent(collection)}&field=${encodeURIComponent(field)}&value=${encodeURIComponent(value)}`;
        
        const response = await fetch(url);

        if (response.ok) {
            const responseText = await response.text();
            
            // Try to parse and format the response
            try {
                const data = JSON.parse(responseText);
                if (data.documents) {
                    // Format the documents array nicely
                    resultElement.textContent = JSON.stringify(data.documents, null, 2);
                } else {
                    resultElement.textContent = JSON.stringify(data, null, 2);
                }
            } catch (e) {
                resultElement.textContent = responseText;
            }
            
            resultElement.className = "text-green-600 font-mono text-sm whitespace-pre-wrap";
        } else {
            const errorText = await response.text();
            
            // Try to parse error response
            try {
                const errorData = JSON.parse(errorText);
                resultElement.textContent = errorData.error || errorText;
            } catch (e) {
                resultElement.textContent = `Error ${response.status}: ${errorText}`;
            }
            
            resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
        }

    } catch (error) {
        resultElement.textContent = `Network Error: ${error.message}`;
        resultElement.className = "text-red-600 font-mono text-sm whitespace-pre-wrap";
    } finally {
        setLoading("queryBtn", "querySpinner", "queryBtnText", false);
    }
}