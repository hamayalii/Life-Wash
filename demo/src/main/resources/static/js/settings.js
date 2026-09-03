const API_BASE = "/api/v1/services/pricing";

// ---- Admin Authentication Logic ----
var adminMenu = document.getElementById("adminMenu");
var adminToggle = document.getElementById("adminToggle");
var adminDropdown = document.querySelector(".admin-dropdown");
var logoutBtn = document.getElementById("logoutBtn");

function setAuthenticatedState(isAuthenticated) {
    if (isAuthenticated) {
        if(adminMenu) adminMenu.classList.remove("hidden");
    } else {
        if(adminMenu) adminMenu.classList.add("hidden");
    }
}

// Check auth state on load
fetch("/api/v1/auth/check", { method: "GET" })
    .then(function(res) {
        if(res.ok) {
            setAuthenticatedState(true);
        }
    })
    .catch(console.error);

if (adminToggle) {
    adminToggle.addEventListener("click", function (e) {
        e.stopPropagation();
        var isHidden = adminDropdown.classList.toggle("hidden");

        if (!isHidden) {
            // Position dropdown at toggle button's location
            var rect = adminToggle.getBoundingClientRect();
            adminDropdown.style.top = (rect.bottom + 4) + "px";
            adminDropdown.style.right = (window.innerWidth - rect.right) + "px";
        }
    });
}

// Close dropdown when clicking outside
document.addEventListener("click", function (e) {
    if (adminMenu && !e.target.closest("#adminMenu") && !adminDropdown.classList.contains("hidden")) {
        adminDropdown.classList.add("hidden");
    }
});

if (logoutBtn) {
    logoutBtn.addEventListener("click", function () {
        fetch("/api/v1/auth/logout", { method: "POST" })
            .then(function() {
                setAuthenticatedState(false);
                adminDropdown.classList.add("hidden");
            });
    });
}

// Toast Notification System
function showToast(message, type = 'success') {
    // Remove existing toast if any
    const existingToast = document.querySelector('.toast-notification');
    if (existingToast) {
        existingToast.remove();
    }
    
    // Create toast element
    const toast = document.createElement('div');
    toast.className = `toast-notification ${type}`;
    
    const icon = type === 'success' ? 'fa-check-circle' : 'fa-exclamation-circle';
    
    // Create close button
    const closeBtn = document.createElement('button');
    closeBtn.className = 'toast-close';
    closeBtn.textContent = '×';
    
    // Create icon element
    const iconElement = document.createElement('i');
    iconElement.className = `fa-solid ${icon}`;
    
    // Create message span
    const messageSpan = document.createElement('span');
    messageSpan.className = 'toast-message';
    messageSpan.textContent = message;
    
    toast.appendChild(closeBtn);
    toast.appendChild(iconElement);
    toast.appendChild(messageSpan);
    
    // Append to body
    document.body.appendChild(toast);
    
    // Trigger animation
    requestAnimationFrame(() => {
        toast.classList.add('show');
    });
    
    // Close button functionality
    closeBtn.addEventListener('click', () => {
        hideToast(toast);
    });
    
    // Auto-hide after 3 seconds
    setTimeout(() => {
        hideToast(toast);
    }, 3000);
}

function hideToast(toast) {
    toast.classList.remove('show');
    setTimeout(() => {
        if (toast.parentNode) {
            toast.remove();
        }
    }, 300);
}

// Ensure DOM is fully loaded before executing
document.addEventListener('DOMContentLoaded', function() {
    loadPricing();
    initializeTabs();
    initializeBackupManagement();
    // initializeMarketingQR() removed - will be called lazily when Marketing tab is displayed
    reinitializeQRCodeOnTabSwitch();
});

function initializeTabs() {
    const tabs = document.querySelectorAll('.settings-tab');
    const tabContents = document.querySelectorAll('.tab-content');
    
    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const targetTab = this.dataset.tab;
            
            // Remove active class from all tabs and contents
            tabs.forEach(t => t.classList.remove('active'));
            tabContents.forEach(c => c.classList.remove('active'));
            
            // Add active class to clicked tab and corresponding content
            this.classList.add('active');
            const targetContent = document.getElementById(`tab-${targetTab}`);
            if (targetContent) {
                targetContent.classList.add('active');
            }
        });
    });
    
    // Initialize new service form
    initializeNewServiceForm();
}

function initializeNewServiceForm() {
    const pricingUnitSelect = document.getElementById('pricingUnit');
    const sofaSetSizeGroup = document.getElementById('sofaSetSizeGroup');
    const newServiceForm = document.getElementById('newServiceForm');
    
    if (pricingUnitSelect && sofaSetSizeGroup) {
        pricingUnitSelect.addEventListener('change', function() {
            if (this.value === 'PER_PERSON') {
                sofaSetSizeGroup.style.display = 'flex';
            } else {
                sofaSetSizeGroup.style.display = 'none';
            }
        });
    }
    
    // Custom pricing checkbox - disable/enable base price input
    const isCustomPricedCheckbox = document.getElementById('isCustomPriced');
    const basePriceInput = document.getElementById('basePrice');
    
    if (isCustomPricedCheckbox && basePriceInput) {
        isCustomPricedCheckbox.addEventListener('change', function() {
            if (this.checked) {
                basePriceInput.disabled = true;
                basePriceInput.value = '0';
            } else {
                basePriceInput.disabled = false;
                basePriceInput.value = '';
            }
        });
    }
    
    if (newServiceForm) {
        newServiceForm.addEventListener('submit', async function(e) {
            e.preventDefault();
            
            const kurdishName = document.getElementById('serviceNameKurdish').value;
            const englishName = document.getElementById('serviceNameEnglish').value;
            const basePrice = document.getElementById('basePrice').value;
            const pricingUnit = document.getElementById('pricingUnit').value;
            const sofaSetSize = document.getElementById('sofaStandardSetSize').value;
            const isCustomPriced = document.getElementById('isCustomPriced').checked;
            
            // Validate inputs
            if (!kurdishName || !englishName || !basePrice || !pricingUnit) {
                showToast('تکایە هەموو زانیارییەکان پڕ بکەرەوە', 'error');
                return;
            }
            
            const request = {
                kurdishName: kurdishName,
                englishName: englishName,
                basePrice: parseFloat(basePrice),
                pricingUnit: pricingUnit,
                sofaStandardSetSize: pricingUnit === 'PER_PERSON' ? (parseInt(sofaSetSize) || 10) : null,
                isCustomPriced: isCustomPriced
            };
            
            try {
                const res = await fetch('/api/v1/services/new', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(request)
                });
                
                if (res.ok) {
                    showToast('خزمەتگوزاری تازە زیادکرا', 'success');
                    newServiceForm.reset();
                    sofaSetSizeGroup.style.display = 'none';
                    // Reload pricing to show the new service
                    loadPricing();
                } else {
                    const errorText = await res.text();
                    showToast('هەڵە: ' + errorText, 'error');
                }
            } catch (err) {
                console.error("Error creating service:", err);
                showToast('هەڵەیەک ڕوویدا لە دروستکردنی خزمەتگوزاری', 'error');
            }
        });
    }
}

async function loadPricing() {
    const container = document.getElementById("pricing-container");
    
    // Show loading state
    container.textContent = '';
    const loadingDiv = document.createElement('div');
    loadingDiv.style.textAlign = 'center';
    loadingDiv.style.padding = '40px';
    loadingDiv.style.color = '#64748b';
    
    const loadingIcon = document.createElement('i');
    loadingIcon.className = 'fa-solid fa-spinner fa-spin';
    loadingIcon.style.fontSize = '32px';
    loadingIcon.style.marginBottom = '16px';
    
    const loadingText = document.createElement('p');
    loadingText.textContent = 'بارکردنی نرخەکان...';
    
    loadingDiv.appendChild(loadingIcon);
    loadingDiv.appendChild(loadingText);
    container.appendChild(loadingDiv);
    
    try {
        const res = await fetch(`${API_BASE}/all`);
        if (!res.ok) {
            throw new Error(`HTTP ${res.status}: ${res.statusText}`);
        }
        
        const pricingList = await res.json();
        
        // Validate response data
        if (!Array.isArray(pricingList)) {
            throw new Error('Invalid response format: expected array');
        }
        
        // Filter out custom-priced services (they shouldn't appear in settings grid)
        const fixedPricedServices = pricingList.filter(item => !item.isCustomPriced);
        
        if (fixedPricedServices.length === 0) {
            container.textContent = '';
            const emptyDiv = document.createElement('div');
            emptyDiv.style.textAlign = 'center';
            emptyDiv.style.padding = '40px';
            emptyDiv.style.color = '#64748b';
            
            const emptyIcon = document.createElement('i');
            emptyIcon.className = 'fa-solid fa-inbox';
            emptyIcon.style.fontSize = '32px';
            emptyIcon.style.marginBottom = '16px';
            
            const emptyText = document.createElement('p');
            emptyText.textContent = 'هیچ نرخێک نەدۆزراوەتەوە';
            
            emptyDiv.appendChild(emptyIcon);
            emptyDiv.appendChild(emptyText);
            container.appendChild(emptyDiv);
            return;
        }
        
        renderPricingCards(fixedPricedServices);
    } catch (err) {
        console.error("Error loading pricing:", err);
        container.textContent = '';
        const errorDiv = document.createElement('div');
        errorDiv.style.textAlign = 'center';
        errorDiv.style.padding = '40px';
        errorDiv.style.color = '#ef4444';
        
        const errorIcon = document.createElement('i');
        errorIcon.className = 'fa-solid fa-triangle-exclamation';
        errorIcon.style.fontSize = '32px';
        errorIcon.style.marginBottom = '16px';
        
        const errorText = document.createElement('p');
        errorText.textContent = 'هەڵەیەک ڕوویدا لە بارکردنی نرخەکان';
        
        const errorDetail = document.createElement('p');
        errorDetail.style.fontSize = '14px';
        errorDetail.style.color = '#64748b';
        errorDetail.style.marginTop = '8px';
        errorDetail.textContent = err.message;
        
        const retryBtn = document.createElement('button');
        retryBtn.textContent = 'دووبارە هەوڵدانەوە';
        retryBtn.style.marginTop = '16px';
        retryBtn.style.padding = '8px 16px';
        retryBtn.style.background = '#10b981';
        retryBtn.style.color = 'white';
        retryBtn.style.border = 'none';
        retryBtn.style.borderRadius = '6px';
        retryBtn.style.cursor = 'pointer';
        retryBtn.onclick = loadPricing;
        
        errorDiv.appendChild(errorIcon);
        errorDiv.appendChild(errorText);
        errorDiv.appendChild(errorDetail);
        errorDiv.appendChild(retryBtn);
        container.appendChild(errorDiv);
    }
}

function renderPricingCards(pricingList) {
    const container = document.getElementById("pricing-container");
    container.textContent = '';
    
    pricingList.forEach(pricing => {
        const card = createPricingCard(pricing);
        container.appendChild(card);
    });
    
    document.querySelectorAll('.discount-toggle').forEach(toggle => {
        toggle.addEventListener('change', function() {
            const card = this.closest('.pricing-card');
            const inputs = card.querySelectorAll('.discount-price, .discount-percentage, .discount-start, .discount-end');
            inputs.forEach(input => input.disabled = !this.checked);
        });
    });
    
    document.querySelectorAll('.sofa-total').forEach(input => {
        input.addEventListener('input', function() {
            const card = this.closest('.pricing-card');
            const perPersonDisplay = card.querySelector('.per-person-rate');
            const total = parseFloat(this.value) || 0;
            const setSize = parseInt(card.dataset.sofaSetSize) || 10;
            const perPerson = total / setSize;
            perPersonDisplay.textContent = perPerson.toLocaleString() + ' (نەفەر)دینار / کەس';
        });
    });
    
    // Reactive discount calculation: auto-calculate percentage from discount price
    document.querySelectorAll('.discount-price').forEach(input => {
        input.addEventListener('input', function() {
            const card = this.closest('.pricing-card');
            const discountPrice = parseFloat(this.value) || 0;
            const basePriceInput = card.querySelector('.base-price');
            const percentageInput = card.querySelector('.discount-percentage');
            
            if (basePriceInput && percentageInput && !basePriceInput.disabled) {
                const basePrice = parseFloat(basePriceInput.value) || 0;
                
                // Division by zero safety
                if (basePrice > 0) {
                    const discountPercentage = ((basePrice - discountPrice) / basePrice) * 100;
                    // Round to 2 decimal places
                    percentageInput.value = Math.max(0, Math.round(discountPercentage * 100) / 100);
                } else {
                    percentageInput.value = '';
                }
            }
        });
    });
    
    // Event delegation for delete service buttons
    const pricingContainer = document.getElementById('pricing-container');
    if (pricingContainer) {
        pricingContainer.addEventListener('click', async function(e) {
            const deleteBtn = e.target.closest('.delete-service-btn');
            if (deleteBtn) {
                const serviceId = deleteBtn.dataset.id;
                if (confirm('دڵنیایت لە سڕینەوەی ئەم خزمەتگوزارییە؟')) {
                    await deleteService(serviceId);
                }
            }
        });
    }
}

function createPricingCard(pricing) {
    const card = document.createElement('div');
    card.className = 'pricing-card';
    card.dataset.id = pricing.id;
    card.dataset.version = pricing.version || 0;
    
    // Header
    const header = document.createElement('div');
    header.className = 'pricing-header';
    
    const headerLeft = document.createElement('div');
    
    const serviceName = document.createElement('span');
    serviceName.className = 'service-name';
    serviceName.textContent = pricing.serviceTypeKurdish;
    
    const serviceNameEn = document.createElement('span');
    serviceNameEn.className = 'service-name-en';
    serviceNameEn.textContent = '(' + pricing.serviceType + ')';
    
    headerLeft.appendChild(serviceName);
    headerLeft.appendChild(serviceNameEn);
    
    const versionInfo = document.createElement('div');
    versionInfo.className = 'version-info';
    
    const versionSpan = document.createElement('span');
    versionSpan.style.fontSize = '11px';
    versionSpan.style.color = '#64748b';
    versionSpan.textContent = pricing.lastModified ? 'دواین دەستکاری: ' + formatTimestamp(pricing.lastModified) : '';
    
    versionInfo.appendChild(versionSpan);
    
    const deleteIcon = document.createElement('i');
    deleteIcon.className = 'fas fa-trash delete-service-btn';
    deleteIcon.style.color = '#ff4444';
    deleteIcon.style.cursor = 'pointer';
    deleteIcon.title = 'سڕینەوە';
    deleteIcon.dataset.id = pricing.id;
    
    header.appendChild(headerLeft);
    header.appendChild(versionInfo);
    header.appendChild(deleteIcon);
    
    // Price input group
    const priceInputGroup = document.createElement('div');
    priceInputGroup.className = 'price-input-group';
    
    const inputWrapper1 = document.createElement('div');
    inputWrapper1.className = 'input-wrapper';
    
    const label1 = document.createElement('label');
    label1.className = 'input-label';
    label1.textContent = 'نرخی شووشتنی خزمەتگوزاری (IQD)';
    
    const basePriceInput = document.createElement('input');
    basePriceInput.type = 'number';
    basePriceInput.className = 'price-input base-price';
    basePriceInput.value = pricing.basePrice;
    basePriceInput.dataset.serviceType = pricing.serviceType;
    
    inputWrapper1.appendChild(label1);
    inputWrapper1.appendChild(basePriceInput);
    
    const inputWrapper2 = document.createElement('div');
    inputWrapper2.className = 'input-wrapper';
    
    const label2 = document.createElement('label');
    label2.className = 'input-label';
    label2.textContent = 'یەکەی بڕ';
    
    const unitInput = document.createElement('input');
    unitInput.type = 'text';
    unitInput.className = 'price-input';
    unitInput.value = getUnitLabel(pricing.pricingUnit);
    unitInput.disabled = true;
    
    inputWrapper2.appendChild(label2);
    inputWrapper2.appendChild(unitInput);
    
    priceInputGroup.appendChild(inputWrapper1);
    priceInputGroup.appendChild(inputWrapper2);
    
    card.appendChild(header);
    card.appendChild(priceInputGroup);
    
    // SOFA section
    if (pricing.serviceType === 'SOFA') {
        const sofaPreview = document.createElement('div');
        sofaPreview.className = 'sofa-preview';
        
        const sofaLabel = document.createElement('label');
        sofaLabel.className = 'input-label';
        sofaLabel.textContent = 'قەنەفە - ستاندارد ١٠ کەس/نەفەر';
        
        const sofaPriceGroup = document.createElement('div');
        sofaPriceGroup.className = 'price-input-group';
        
        const sofaWrapper1 = document.createElement('div');
        sofaWrapper1.className = 'input-wrapper';
        
        const sofaLabel1 = document.createElement('label');
        sofaLabel1.className = 'input-label';
        sofaLabel1.textContent = 'نرخی کۆی گشتی (IQD)';
        
        const sofaTotalInput = document.createElement('input');
        sofaTotalInput.type = 'number';
        sofaTotalInput.className = 'price-input sofa-total';
        sofaTotalInput.value = pricing.basePrice;
        
        sofaWrapper1.appendChild(sofaLabel1);
        sofaWrapper1.appendChild(sofaTotalInput);
        
        const sofaWrapper2 = document.createElement('div');
        sofaWrapper2.className = 'input-wrapper';
        
        const sofaLabel2 = document.createElement('label');
        sofaLabel2.className = 'input-label';
        sofaLabel2.textContent = 'نرخ بۆ هەر کەسێک/نەفەرێک (IQD)';
        
        const perPersonRate = document.createElement('div');
        perPersonRate.className = 'per-person-rate';
        perPersonRate.textContent = (pricing.perPersonPrice ? pricing.perPersonPrice.toLocaleString() : '0') + ' دینار / کەس';
        
        sofaWrapper2.appendChild(sofaLabel2);
        sofaWrapper2.appendChild(perPersonRate);
        
        sofaPriceGroup.appendChild(sofaWrapper1);
        sofaPriceGroup.appendChild(sofaWrapper2);
        
        sofaPreview.appendChild(sofaLabel);
        sofaPreview.appendChild(sofaPriceGroup);
        
        card.appendChild(sofaPreview);
    }
    
    // Discount section
    const discountSection = document.createElement('div');
    discountSection.className = 'discount-section';
    
    const discountHeader = document.createElement('div');
    discountHeader.className = 'discount-header';
    
    const discountToggle = document.createElement('input');
    discountToggle.type = 'checkbox';
    discountToggle.className = 'discount-toggle';
    if (pricing.isDiscountActive) {
        discountToggle.checked = true;
    }
    
    const discountLabel = document.createElement('span');
    discountLabel.className = 'discount-label';
    discountLabel.textContent = 'جێبەجێکردنی داشکاندن';
    
    discountHeader.appendChild(discountToggle);
    discountHeader.appendChild(discountLabel);
    
    const discountPriceGroup = document.createElement('div');
    discountPriceGroup.className = 'price-input-group';
    
    const discountWrapper1 = document.createElement('div');
    discountWrapper1.className = 'input-wrapper';
    
    const discountLabel1 = document.createElement('label');
    discountLabel1.className = 'input-label';
    discountLabel1.textContent = 'نرخی داشکاندن (IQD)';
    
    const discountPriceInput = document.createElement('input');
    discountPriceInput.type = 'number';
    discountPriceInput.className = 'price-input discount-price';
    discountPriceInput.value = pricing.discountPrice || '';
    if (!pricing.isDiscountActive) {
        discountPriceInput.disabled = true;
    }
    
    discountWrapper1.appendChild(discountLabel1);
    discountWrapper1.appendChild(discountPriceInput);
    
    const discountWrapper2 = document.createElement('div');
    discountWrapper2.className = 'input-wrapper';
    
    const discountLabel2 = document.createElement('label');
    discountLabel2.className = 'input-label';
    discountLabel2.textContent = 'ڕێژەی داشکاندن (%)';
    
    const discountPercentageInput = document.createElement('input');
    discountPercentageInput.type = 'number';
    discountPercentageInput.className = 'price-input discount-percentage';
    discountPercentageInput.value = pricing.discountPercentage || '';
    if (!pricing.isDiscountActive) {
        discountPercentageInput.disabled = true;
    }
    
    discountWrapper2.appendChild(discountLabel2);
    discountWrapper2.appendChild(discountPercentageInput);
    
    discountPriceGroup.appendChild(discountWrapper1);
    discountPriceGroup.appendChild(discountWrapper2);
    
    const dateInputs = document.createElement('div');
    dateInputs.className = 'date-inputs';
    
    const dateWrapper1 = document.createElement('div');
    dateWrapper1.className = 'input-wrapper';
    
    const dateLabel1 = document.createElement('label');
    dateLabel1.className = 'input-label';
    dateLabel1.textContent = 'بەرواری دەستپێکردن';
    
    const discountStartInput = document.createElement('input');
    discountStartInput.type = 'datetime-local';
    discountStartInput.className = 'date-input discount-start';
    discountStartInput.value = formatDateTimeForInput(pricing.discountStartDate);
    if (!pricing.isDiscountActive) {
        discountStartInput.disabled = true;
    }
    
    dateWrapper1.appendChild(dateLabel1);
    dateWrapper1.appendChild(discountStartInput);
    
    const dateWrapper2 = document.createElement('div');
    dateWrapper2.className = 'input-wrapper';
    
    const dateLabel2 = document.createElement('label');
    dateLabel2.className = 'input-label';
    dateLabel2.textContent = 'بەرواری کۆتاییهاتن';
    
    const discountEndInput = document.createElement('input');
    discountEndInput.type = 'datetime-local';
    discountEndInput.className = 'date-input discount-end';
    discountEndInput.value = formatDateTimeForInput(pricing.discountEndDate);
    if (!pricing.isDiscountActive) {
        discountEndInput.disabled = true;
    }
    
    dateWrapper2.appendChild(dateLabel2);
    dateWrapper2.appendChild(discountEndInput);
    
    dateInputs.appendChild(dateWrapper1);
    dateInputs.appendChild(dateWrapper2);
    
    discountSection.appendChild(discountHeader);
    discountSection.appendChild(discountPriceGroup);
    discountSection.appendChild(dateInputs);
    
    card.appendChild(discountSection);
    
    // Save button
    const saveBtn = document.createElement('button');
    saveBtn.className = 'save-btn';
    saveBtn.textContent = 'خەزنکردن';
    saveBtn.onclick = function() { savePricing(pricing.id); };
    
    card.appendChild(saveBtn);
    
    return card;
}

function getUnitLabel(unit) {
    const labels = {
        'PER_METER': 'مەتر',
        'PER_PIECE': 'دانە',
        'PER_PERSON': 'کەس/نەفەر'
    };
    return labels[unit] || unit;
}

function formatDateTimeForInput(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    const offset = date.getTimezoneOffset() * 60000;
    const localISOTime = (new Date(date - offset)).toISOString().slice(0, 16);
    return localISOTime;
}

function formatTimestamp(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('ku-IQ', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

async function savePricing(id) {
    const card = document.querySelector(`.pricing-card[data-id="${id}"]`);
    const basePrice = parseFloat(card.querySelector('.base-price').value);
    const discountToggle = card.querySelector('.discount-toggle').checked;
    const currentVersion = parseInt(card.dataset.version) || 0;
    
    const request = {
        basePrice: basePrice,
        isDiscountActive: discountToggle,
        discountPrice: discountToggle ? parseFloat(card.querySelector('.discount-price').value) || null : null,
        discountPercentage: discountToggle ? parseFloat(card.querySelector('.discount-percentage').value) || null : null,
        discountStartDate: discountToggle ? card.querySelector('.discount-start').value || null : null,
        discountEndDate: discountToggle ? card.querySelector('.discount-end').value || null : null,
        version: currentVersion
    };
    
    const sofaTotal = card.querySelector('.sofa-total');
    if (sofaTotal) {
        request.basePrice = parseFloat(sofaTotal.value);
        request.sofaStandardSetSize = 10;
    }
    
    try {
        const res = await fetch(`${API_BASE}/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(request)
        });
        
        if (res.status === 409) {
            // Conflict - Optimistic locking error
            showSettingsConflictModal(() => savePricing(id));
            return;
        }
        
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        
        showToast('نرخەکان بە سەرکەوتوویی خەزنکرا', 'success');
        loadPricing();
    } catch (err) {
        console.error("Error saving pricing:", err);
        showToast("هەڵەیەک ڕوویدا لە خەزنکردنی نرخەکان", 'error');
    }
}

async function deleteService(id) {
    try {
        const res = await fetch(`/api/v1/services/${id}`, {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' }
        });
        
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        
        showToast('خزمەتگوزاری بە سەرکەوتوویی سڕایەوە', 'success');
        loadPricing();
    } catch (err) {
        console.error("Error deleting service:", err);
        showToast("هەڵەیەک ڕوویدا لە سڕینەوەی خزمەتگوزاری", 'error');
    }
}

// ── CONFLICT MODAL HANDLING FOR SETTINGS ───────────────────────────────────────
let currentSettingsRetryFunction = null;

function showSettingsConflictModal(retryFunction) {
    currentSettingsRetryFunction = retryFunction;
    const conflictModal = document.getElementById('conflict-modal');
    const actionsContainer = document.getElementById('conflict-modal-actions');
    
    if (!conflictModal || !actionsContainer) return;
    
    // Clear previous buttons
    actionsContainer.textContent = '';
    
    // Add retry button
    const retryBtn = document.createElement('button');
    retryBtn.className = 'btn btn-primary';
    retryBtn.textContent = 'دووبارە هەوڵدانەوە';
    retryBtn.onclick = function () {
        hideSettingsConflictModal();
        if (currentSettingsRetryFunction) {
            currentSettingsRetryFunction();
        }
    };
    
    // Add cancel button
    const cancelBtn = document.createElement('button');
    cancelBtn.className = 'btn btn-secondary';
    cancelBtn.textContent = 'پاشگەزبوونەوە';
    cancelBtn.onclick = function () {
        hideSettingsConflictModal();
        // Reload to get latest data
        loadPricing();
    };
    
    actionsContainer.appendChild(retryBtn);
    actionsContainer.appendChild(cancelBtn);
    
    // Show modal
    conflictModal.classList.add('active');
}

function hideSettingsConflictModal() {
    const conflictModal = document.getElementById('conflict-modal');
    if (conflictModal) {
        conflictModal.classList.remove('active');
    }
    currentSettingsRetryFunction = null;
}

// Wire up conflict modal close button
document.addEventListener('DOMContentLoaded', function () {
    const conflictCloseBtn = document.getElementById('conflict-modal-close');
    if (conflictCloseBtn) {
        conflictCloseBtn.addEventListener('click', hideSettingsConflictModal);
    }
    
    // Close modal on backdrop click
    const conflictModal = document.getElementById('conflict-modal');
    if (conflictModal) {
        conflictModal.addEventListener('click', function (e) {
            if (e.target === conflictModal || e.target.classList.contains('pos-modal-backdrop')) {
                hideSettingsConflictModal();
                // Reload to get latest data
                loadPricing();
            }
        });
    }
});

/* ==========================================================================
 * BACKUP MANAGEMENT FUNCTIONS
 * ======================================================================== */

function initializeBackupManagement() {
    const downloadBtn = document.getElementById('downloadBackupBtn');
    const restoreBtn = document.getElementById('restoreBackupBtn');
    const restoreFileInput = document.getElementById('restoreFileInput');
    
    if (downloadBtn) {
        downloadBtn.addEventListener('click', downloadBackup);
    }
    
    if (restoreBtn && restoreFileInput) {
        restoreBtn.addEventListener('click', restoreBackup);
    }
}

async function downloadBackup() {
    const downloadBtn = document.getElementById('downloadBackupBtn');
    
    // Store original content by cloning children
    const originalChildren = Array.from(downloadBtn.childNodes);
    
    try {
        downloadBtn.disabled = true;
        downloadBtn.textContent = '';
        
        const spinnerIcon = document.createElement('i');
        spinnerIcon.className = 'fa-solid fa-spinner fa-spin';
        
        const loadingText = document.createTextNode(' داگرتن...');
        
        downloadBtn.appendChild(spinnerIcon);
        downloadBtn.appendChild(loadingText);
        
        const response = await fetch('/api/v1/admin/backups/download');
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        
        // Get filename from Content-Disposition header
        const contentDisposition = response.headers.get('Content-Disposition');
        let filename = 'ghasl_backup.sql';
        if (contentDisposition) {
            const filenameMatch = contentDisposition.match(/filename="(.+)"/);
            if (filenameMatch) {
                filename = filenameMatch[1];
            }
        }
        
        // Create blob and download
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        
        showToast('باسکئەپ بە سەرکەوتوویی داگیرا', 'success');
    } catch (error) {
        console.error('Error downloading backup:', error);
        showToast('هەڵە لە داگرتنی باکئەپ: ' + error.message, 'error');
    } finally {
        downloadBtn.disabled = false;
        downloadBtn.textContent = '';
        originalChildren.forEach(child => downloadBtn.appendChild(child));
    }
}

async function restoreBackup() {
    const restoreFileInput = document.getElementById('restoreFileInput');
    const restoreBtn = document.getElementById('restoreBackupBtn');
    const restoreStatus = document.getElementById('restoreStatus');
    
    const file = restoreFileInput.files[0];
    
    if (!file) {
        showToast('تکایە فایلی باکئەپ هەڵبژێرە', 'error');
        return;
    }
    
    if (!confirm('ئاگاداربە: ئەمە داتای ئێستا دەسڕێتەوە و جێگەی دەگرێتەوە. دڵنیایت؟')) {
        return;
    }
    
    // Store original content by cloning children
    const originalChildren = Array.from(restoreBtn.childNodes);
    const formData = new FormData();
    formData.append('file', file);
    
    try {
        restoreBtn.disabled = true;
        restoreBtn.textContent = '';
        
        const spinnerIcon = document.createElement('i');
        spinnerIcon.className = 'fa-solid fa-spinner fa-spin';
        
        const loadingText = document.createTextNode(' گەڕاندنەوە...');
        
        restoreBtn.appendChild(spinnerIcon);
        restoreBtn.appendChild(loadingText);
        
        if (restoreStatus) {
            restoreStatus.classList.remove('hidden');
            restoreStatus.className = 'restore-status restore-status-loading';
            restoreStatus.textContent = 'گەڕاندنەوەی داتابەیس... تکایە چاوەڕێک بکە';
        }
        
        const response = await fetch('/api/v1/admin/backups/restore', {
            method: 'POST',
            body: formData
        });
        
        if (!response.ok) {
            const errorData = await response.json();
            throw new Error(errorData.error || 'Restore failed');
        }
        
        const result = await response.json();
        
        if (restoreStatus) {
            restoreStatus.className = 'restore-status restore-status-success';
            restoreStatus.textContent = 'باسکئەپ بە سەرکەوتوویی گەڕێندراەوە! پەڕە دەبارە دەکرێتەوە...';
        }
        
        showToast('باسکئەپ بە سەرکەوتوویی گەڕێندراەوە', 'success');
        
        // Reload page after 3 seconds to refresh all data
        setTimeout(() => {
            window.location.reload();
        }, 3000);
        
    } catch (error) {
        console.error('Error restoring backup:', error);
        
        if (restoreStatus) {
            restoreStatus.className = 'restore-status restore-status-error';
            restoreStatus.textContent = 'هەڵە لە گەڕاندنەوە: ' + error.message;
        }
        
        showToast('هەڵە لە گەڕاندنەوەی باکئەپ: ' + error.message, 'error');
    } finally {
        restoreBtn.disabled = false;
        restoreBtn.textContent = '';
        originalChildren.forEach(child => restoreBtn.appendChild(child));
    }
}

/* ==========================================================================
 * MARKETING QR CODE GENERATION (Client-Side Only - Zero Server Load)
 * ======================================================================== */

let qrInstance = null;

function initializeMarketingQR() {
    const urlInput = document.getElementById('marketingUrlInput');
    const canvas = document.getElementById('marketingQrCanvas');
    const downloadBtn = document.getElementById('downloadQrBtn');
    
    if (!urlInput || !canvas || !downloadBtn) {
        console.warn('Marketing QR elements not found');
        return;
    }
    
    // Initialize with current origin (points to index.html order form)
    const initialUrl = window.location.origin + '/index.html';
    urlInput.value = initialUrl;
    
    // Initialize QR Code instance with robust error handling
    try {
        // Check if QRious library is available
        if (typeof QRious === 'undefined') {
            console.error('QRious library not loaded');
            showToast('هەڵە: کتێبخانەی کیو ئاڕ کۆد بارنەکراوە', 'error');
            return;
        }
        
        qrInstance = new QRious({
            element: canvas,
            value: initialUrl,
            size: 250,
            level: 'H',
            background: '#ffffff',
            foreground: '#000000'
        });
        
        // Verify QR code was rendered
        if (canvas.toDataURL() === 'data:,') {
            console.error('QR code canvas is blank after initialization');
            showToast('هەڵە لە ڕێندەری کیو ئاڕ ک�ود', 'error');
            return;
        }
        
        console.log('QR code initialized successfully');
    } catch (err) {
        console.error('Failed to initialize QR code:', err);
        showToast('هەڵە لە دەستپێکردنی کیو ئاڕ کۆد', 'error');
        return;
    }
    
    // Dynamic update on URL input change
    urlInput.addEventListener('input', function() {
        const url = this.value.trim();
        if (url && qrInstance) {
            qrInstance.value = url;
        }
    });
    
    // Download button handler
    downloadBtn.addEventListener('click', function() {
        try {
            // Verify canvas has content before download
            if (canvas.toDataURL() === 'data:,') {
                showToast('هەڵە: کیو ئاڕ کۆدی بەتاڵە', 'error');
                return;
            }
            
            // Convert canvas to PNG data URL
            const dataUrl = canvas.toDataURL('image/png');
            
            // Create temporary anchor element for download
            const link = document.createElement('a');
            link.href = dataUrl;
            link.download = 'qr-code-ghasl.png';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            
            showToast('کیو ئاڕ کۆد داگیرا', 'success');
        } catch (err) {
            console.error('Error downloading QR code:', err);
            showToast('هەڵە لە داگرتنی کیو ئاڕ کۆد', 'error');
        }
    });
}

// Re-initialize QR code when Marketing tab is activated (handles hidden canvas issue)
function reinitializeQRCodeOnTabSwitch() {
    // CORRECTED SELECTOR: Use the actual class name from settings.html
    const tabs = document.querySelectorAll('.settings-tab'); 
    
    if (tabs.length === 0) {
        console.error("CRITICAL DOM ERROR: No elements found with class '.settings-tab'. Verify HTML classes.");
        return;
    }

    tabs.forEach(tab => {
        tab.addEventListener('click', function() {
            const target = this.getAttribute('data-tab');
            if (target === 'marketing') {
                // Force the browser to compute the CSS layout before drawing
                requestAnimationFrame(() => {
                    setTimeout(() => {
                        const canvas = document.getElementById('marketingQrCanvas');
                        if (canvas) {
                            // Reset canvas dimensions to force a clean state
                            const ctx = canvas.getContext('2d');
                            ctx.clearRect(0, 0, canvas.width, canvas.height);
                        }
                        // Now safely draw the matrix
                        initializeMarketingQR();
                    }, 100); // 100ms ensures the display:block render is complete
                });
            }
        });
    });
}
