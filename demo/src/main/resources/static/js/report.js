/**
 * Marketing ROI Report - Frontend Implementation
 *
 * Architecture Compliance:
 * - Global State Object pattern (Zero Trust Architecture)
 * - Event Delegation for user interactions
 * - Safe DOM Manipulation (createElement, textContent, createElementNS)
 * - NO innerHTML with dynamic data
 * - No hardcoded constants - all data from API
 * - All calculations performed in backend
 */

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

// Global State Object (Single Source of Truth from API)
window.MarketingReportState = {
    // API data (Single Source of Truth from backend)
    metrics: {
        monthlyAdSpend: 0,
        newCustomers: 0,
        cac: 0,
        clv: 0,
        roiRatio: 0,
        currency: 'IQD',
        period: null
    },
    
    // Canceled orders data
    canceledReasons: [],
    
    // Expenses data
    expenses: {
        categories: [],
        currentMonthExpenses: [],
        total: 0,
        loading: false,
        error: null,
        pagination: {
            currentPage: 0,
            totalPages: 0,
            totalElements: 0
        }
    },
    
    // Pareto analysis data
    paretoData: {
        services: [],
        loading: false,
        error: null,
        selectedPeriod: 'month'
    },
    
    // Report generation data
    reportData: {
        currentReportType: null,
        currentReportText: null,
        loading: false,
        error: null
    },
    
    // UI state
    loading: false,
    error: null,
    selectedPeriod: 'week'
};

// SVG Arc Math Helpers (from new chart/script.js)
function polarToCartesian(centerX, centerY, radius, angleInDegrees) {
    const angleInRadians = (angleInDegrees - 180) * Math.PI / 180.0;
    return {
        x: centerX + (radius * Math.cos(angleInRadians)),
        y: centerY + (radius * Math.sin(angleInRadians))
    };
}

function describeArc(x, y, radius, startAngle, endAngle) {
    const start = polarToCartesian(x, y, radius, endAngle);
    const end = polarToCartesian(x, y, radius, startAngle);
    const largeArcFlag = endAngle - startAngle <= 180 ? "0" : "1";
    return [
        "M", start.x, start.y, 
        "A", radius, radius, 0, largeArcFlag, 0, end.x, end.y
    ].join(" ");
}

// Calculate needle angle based on ROI ratio
function calculateNeedleAngle(ratio) {
    const clampedRatio = Math.min(Math.max(ratio, 0), 6);
    let needleAngle = 0;
    
    if (clampedRatio <= 1) {
        needleAngle = (clampedRatio / 1) * 30; // 0 to 30 degrees
    } else if (clampedRatio <= 3) {
        needleAngle = 30 + ((clampedRatio - 1) / 2) * 60; // 30 to 90 degrees
    } else {
        needleAngle = 90 + ((clampedRatio - 3) / 3) * 90; // 90 to 180 degrees
    }
    
    return needleAngle;
}

// Format currency for display
function formatCurrency(amount, currency) {
    return new Intl.NumberFormat('ku-IQ', {
        style: 'currency',
        currency: currency,
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    }).format(amount);
}

// Render Gauge Chart (State-driven - completely rebuilds from state)
function renderGaugeChart() {
    const state = window.MarketingReportState.metrics;
    const container = document.getElementById('gauge-chart-container');
    
    if (!container) return;
    
    // Clear container completely
    container.textContent = '';
    
    // Create SVG element using createElementNS (safe DOM manipulation)
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', '320');
    svg.setAttribute('height', '160');
    svg.setAttribute('viewBox', '0 0 320 160');
    svg.classList.add('gauge-svg');
    
    // Gauge zones configuration
    const zones = [
        { start: 0, end: 30, color: '#EF4444' },    // Ratio < 1
        { start: 30, end: 90, color: '#F59E0B' },   // Ratio 1 to 3
        { start: 90, end: 180, color: '#10B981' }   // Ratio > 3
    ];
    
    const cx = 160;
    const cy = 140;
    const radius = 90;
    const strokeWidth = 24;
    
    // Draw gauge zones
    const zonesGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    zonesGroup.id = 'gauge-zones';
    
    zones.forEach(zone => {
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', describeArc(cx, cy, radius, zone.start, zone.end));
        path.setAttribute('fill', 'none');
        path.setAttribute('stroke', zone.color);
        path.setAttribute('stroke-width', strokeWidth);
        path.setAttribute('stroke-linecap', 'butt');
        zonesGroup.appendChild(path);
    });
    
    svg.appendChild(zonesGroup);
    
    // Draw annotations
    const annotationsGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    annotationsGroup.classList.add('annotations');
    
    // Ratio < 1 annotation
    const path1 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path1.setAttribute('d', 'M 50 100 L 75 110');
    path1.setAttribute('stroke', '#94a3b8');
    path1.setAttribute('stroke-width', '1');
    path1.setAttribute('stroke-dasharray', '2 2');
    path1.setAttribute('fill', 'none');
    
    const text1 = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    text1.setAttribute('x', '45');
    text1.setAttribute('y', '98');
    text1.setAttribute('text-anchor', 'middle');
    text1.textContent = 'Ratio < 1';
    
    annotationsGroup.appendChild(path1);
    annotationsGroup.appendChild(text1);
    
    // Ratio 1 to 3 annotation
    const path2 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path2.setAttribute('d', 'M 160 30 L 160 55');
    path2.setAttribute('stroke', '#94a3b8');
    path2.setAttribute('stroke-width', '1');
    path2.setAttribute('stroke-dasharray', '2 2');
    path2.setAttribute('fill', 'none');
    
    const text2 = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    text2.setAttribute('x', '160');
    text2.setAttribute('y', '22');
    text2.setAttribute('text-anchor', 'middle');
    text2.textContent = 'Ratio 1 to 3';
    
    annotationsGroup.appendChild(path2);
    annotationsGroup.appendChild(text2);
    
    // Ratio > 3 annotation
    const path3 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path3.setAttribute('d', 'M 270 100 L 245 110');
    path3.setAttribute('stroke', '#94a3b8');
    path3.setAttribute('stroke-width', '1');
    path3.setAttribute('stroke-dasharray', '2 2');
    path3.setAttribute('fill', 'none');
    
    const text3 = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    text3.setAttribute('x', '275');
    text3.setAttribute('y', '98');
    text3.setAttribute('text-anchor', 'middle');
    text3.textContent = 'Ratio > 3';
    
    annotationsGroup.appendChild(path3);
    annotationsGroup.appendChild(text3);
    
    svg.appendChild(annotationsGroup);
    
    // Draw needle (position based on state.roiRatio)
    const needleGroup = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    const needleAngle = calculateNeedleAngle(state.roiRatio);
    needleGroup.setAttribute('transform', `translate(${cx}, ${cy}) rotate(${needleAngle - 90})`);
    needleGroup.id = 'needle';
    
    // Needle path
    const needlePath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    needlePath.setAttribute('d', 'M -4 0 L 0 -80 L 4 0 Z');
    needlePath.setAttribute('fill', '#1e293b');
    
    // Needle center circle (outer)
    const needleCircleOuter = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    needleCircleOuter.setAttribute('cx', '0');
    needleCircleOuter.setAttribute('cy', '0');
    needleCircleOuter.setAttribute('r', '8');
    needleCircleOuter.setAttribute('fill', '#1e293b');
    
    // Needle center circle (inner)
    const needleCircleInner = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    needleCircleInner.setAttribute('cx', '0');
    needleCircleInner.setAttribute('cy', '0');
    needleCircleInner.setAttribute('r', '3');
    needleCircleInner.setAttribute('fill', '#ffffff');
    
    needleGroup.appendChild(needlePath);
    needleGroup.appendChild(needleCircleOuter);
    needleGroup.appendChild(needleCircleInner);
    svg.appendChild(needleGroup);
    
    container.appendChild(svg);
}

// Render Metrics Display (State-driven)
function renderMetrics() {
    const state = window.MarketingReportState.metrics;
    
    // Update Ad Spend input field with current value (raw numeric, no formatting)
    const adSpendInput = document.getElementById('input-ad-spend');
    if (adSpendInput) {
        // Ensure we set the raw numeric value, not a formatted string
        // This prevents string concatenation bugs and strips BigDecimal trailing zeros
        const rawValue = parseFloat(state.monthlyAdSpend) || 0;
        adSpendInput.value = rawValue.toString();
    }
    
    const newCustomersEl = document.getElementById('val-new-customers');
    if (newCustomersEl) {
        newCustomersEl.textContent = state.newCustomers;
    }
    
    const cacEl = document.getElementById('val-cac');
    if (cacEl) {
        cacEl.textContent = formatCurrency(state.cac, state.currency);
    }
    
    const clvEl = document.getElementById('val-clv');
    if (clvEl) {
        clvEl.textContent = formatCurrency(state.clv, state.currency);
    }
    
    const ratioEl = document.getElementById('val-ratio');
    if (ratioEl) {
        ratioEl.textContent = state.roiRatio.toFixed(1);
    }
    
    // Update insight text based on ratio
    const insightText = document.getElementById('insight-text');
    const infoIcon = document.getElementById('info-icon');
    
    if (insightText && infoIcon) {
        if (state.roiRatio < 1) {
            insightText.textContent = "ڕێکلامەکانت زۆر سوودی نیە و لە زەرەردای! پێشنیار دەکرێت دەستبەجێ ئەم ڕێکلامانە بوەستێنیت و پێداچوونەوە بە تێچووەکانتدا بکەیت.";
            infoIcon.style.color = "#ef4444";
        } else if (state.roiRatio <= 3) {
            insightText.textContent = "ڕێکلامەکان تەنها تێچووی خۆیان دەرکردووەتەوە پێویستە چاودێری بکرێن و هەوڵ بدرێت ستایلی ڕێکلامەکان بگۆڕدرێت.";
            infoIcon.style.color = "#d97706";
        } else {
            insightText.textContent = "ڕێکلامەکانت قازانجێکی زۆر باشیان هەیە پێشنیار دەکرێت بودجەی ڕێکلام زیاد بکەیت بۆ بەدەستهێنانی کڕیاری زیاتر.";
            infoIcon.style.color = "#059669";
        }
    }
}

// Update loading state
function updateLoadingState() {
    const loadingState = document.getElementById('loading-state');
    const widgetCard = document.querySelector('.report-widget-card');
    
    if (window.MarketingReportState.loading) {
        if (loadingState) loadingState.classList.remove('hidden');
        if (widgetCard) widgetCard.classList.add('hidden');
    } else {
        if (loadingState) loadingState.classList.add('hidden');
        if (widgetCard) widgetCard.classList.remove('hidden');
    }
}

// Render error state
function renderErrorState() {
    const errorState = document.getElementById('error-state');
    const errorMessage = document.getElementById('error-message');
    const widgetCard = document.querySelector('.report-widget-card');
    
    if (window.MarketingReportState.error) {
        if (errorState) errorState.classList.remove('hidden');
        if (widgetCard) widgetCard.classList.add('hidden');
        if (errorMessage) {
            errorMessage.textContent = window.MarketingReportState.error;
        }
    } else {
        if (errorState) errorState.classList.add('hidden');
        if (widgetCard) widgetCard.classList.remove('hidden');
    }
}

// Debounce flag to prevent race conditions
let isUpdatingAdSpend = false;

// Update Ad Spend via API (Zero Trust - backend handles all calculations)
async function updateAdSpend() {
    const adSpendInput = document.getElementById('input-ad-spend');
    const saveBtn = document.getElementById('btn-save-ad-spend');
    
    if (!adSpendInput || !saveBtn) return;
    
    // Debounce: Prevent multiple simultaneous requests
    if (isUpdatingAdSpend) {
        console.warn('Ad spend update already in progress, ignoring duplicate request');
        return;
    }
    
    // Strict numeric parsing - clean the input value first
    const inputValue = adSpendInput.value.trim();
    const newAmount = parseFloat(inputValue);
    
    // Validate input
    if (isNaN(newAmount) || newAmount < 0) {
        alert('تکایە بڕێکی دروست بنووسە');
        return;
    }
    
    // Log for debugging
    console.log('Updating ad spend - Input value:', inputValue, 'Parsed amount:', newAmount);
    
    // Set debounce flag and disable save button
    isUpdatingAdSpend = true;
    saveBtn.disabled = true;
    
    try {
        const response = await fetch('/api/v1/admin/reports/marketing-spend', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                amount: newAmount,
                period: window.MarketingReportState.selectedPeriod || 'month' // Default to 'month' if not set
            })
        });
        
        if (!response.ok) {
            // Parse error response from GlobalExceptionHandler
            let errorMessage = `Failed to update ad spend: ${response.status}`;
            try {
                const errorData = await response.json();
                if (errorData.message) {
                    errorMessage = errorData.message;
                }
            } catch (e) {
                // If response is not JSON, use status code
                console.error('Failed to parse error response:', e);
            }
            throw new Error(errorMessage);
        }
        
        // On success, fetch fresh metrics from backend
        // Backend recalculates CAC and ROI ratio with new ad spend
        await fetchMarketingROI();
        
    } catch (error) {
        console.error('Error updating ad spend:', error);
        alert('هەڵە لە نوێکردنەوەی خەرجی بازاریگەری: ' + error.message);
    } finally {
        // Clear debounce flag and re-enable save button
        isUpdatingAdSpend = false;
        saveBtn.disabled = false;
    }
}

// API Integration - Fetch Marketing ROI data
async function fetchMarketingROI() {
    const state = window.MarketingReportState;
    state.loading = true;
    state.error = null;
    
    updateLoadingState();
    renderErrorState();
    
    try {
        const response = await fetch(`/api/v1/admin/reports/marketing-roi?period=${state.selectedPeriod}`);
        
        if (!response.ok) {
            throw new Error(`Failed to fetch marketing ROI: ${response.status}`);
        }
        
        const data = await response.json();
        
        // Update global state (Single Source of Truth)
        state.metrics = {
            monthlyAdSpend: data.monthlyAdSpend,
            newCustomers: data.newCustomers,
            cac: data.customerAcquisitionCost,
            clv: data.averageCustomerLifetimeValue,
            roiRatio: data.roiRatio,
            currency: data.currency,
            period: data.period
        };
        
        // Trigger UI updates from state
        renderMetrics();
        renderGaugeChart();
        
    } catch (error) {
        console.error('Error fetching marketing ROI:', error);
        state.error = error.message || 'هەڵە لە بارکردنی داتا';
        renderErrorState();
    } finally {
        state.loading = false;
        updateLoadingState();
    }
}

// Event Delegation for period selector and Ad Spend input
function setupEventDelegation() {
    const periodSelector = document.getElementById('period-selector');
    
    if (!periodSelector) return;
    
    // Single listener on parent container (Event Delegation pattern)
    periodSelector.addEventListener('click', (e) => {
        const button = e.target.closest('.period-button');
        if (!button) return;
        
        const period = button.dataset.period;
        if (!period) return;
        
        // Update global state
        window.MarketingReportState.selectedPeriod = period;
        
        // Update active state in UI
        document.querySelectorAll('.period-button').forEach(btn => {
            btn.classList.remove('active');
        });
        button.classList.add('active');
        
        // Fetch new data from API
        fetchMarketingROI();
    });
    
    // Ad Spend Save button event delegation
    const saveBtn = document.getElementById('btn-save-ad-spend');
    if (saveBtn) {
        saveBtn.addEventListener('click', (e) => {
            e.preventDefault();
            updateAdSpend();
        });
    }
    
    // Ad Spend input field - trigger on Enter key
    const adSpendInput = document.getElementById('input-ad-spend');
    if (adSpendInput) {
        adSpendInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                updateAdSpend();
            }
        });
    }
    
    // Retry button event delegation
    const retryBtn = document.getElementById('retry-btn');
    if (retryBtn) {
        retryBtn.addEventListener('click', () => {
            fetchMarketingROI();
        });
    }
}

// Initialize on DOMContentLoaded
document.addEventListener('DOMContentLoaded', () => {
    // Setup event delegation
    setupEventDelegation();
    
    // Initial data fetch
    fetchMarketingROI();
    fetchCanceledReasons();
});

// Color mapping for heatmap ranking
const CANCELED_COLORS = [
  'bg-rose-500',  // Rank 1
  'bg-rose-400',  // Rank 2
  'bg-rose-300',  // Rank 3
  'bg-rose-200'   // Rank 4
];

// Fetch canceled orders reasons from API
async function fetchCanceledReasons() {
  try {
    const response = await fetch('/api/v1/admin/reports/canceled-reasons');
    
    if (!response.ok) {
      throw new Error(`Failed to fetch canceled reasons: ${response.status}`);
    }
    
    const data = await response.json();
    
    // Update global state
    window.MarketingReportState.canceledReasons = data;
    
    // Render chart
    renderCanceledChart();
    
  } catch (error) {
    console.error('Error fetching canceled reasons:', error);
    const errorDiv = document.getElementById('canceled-error');
    if (errorDiv) {
      errorDiv.classList.remove('hidden');
    }
  }
}

// Render canceled orders horizontal bar chart
function renderCanceledChart() {
  const container = document.getElementById('canceled-chart-container');
  const reasons = window.MarketingReportState.canceledReasons;
  
  if (!container || reasons.length === 0) {
    if (container) {
      container.textContent = '';
      const emptyP = document.createElement('p');
      emptyP.className = 'text-muted';
      emptyP.textContent = 'هیچ داواکارییەکی هەڵوەشاوە نییە';
      container.appendChild(emptyP);
    }
    return;
  }
  
  // Clear container
  container.textContent = '';
  
  // Find max count for percentage calculation
  const maxCount = Math.max(...reasons.map(r => r.count));
  
  // Render each bar
  reasons.forEach((item, index) => {
    const percentage = (item.count / maxCount) * 100;
    const colorClass = CANCELED_COLORS[Math.min(index, CANCELED_COLORS.length - 1)];
    
    // Create bar row using safe DOM manipulation
    const barRow = document.createElement('div');
    barRow.className = 'canceled-bar-row';
    
    // Create header
    const header = document.createElement('div');
    header.className = 'canceled-bar-header';
    
    const label = document.createElement('span');
    label.className = 'canceled-bar-label';
    label.textContent = item.kurdishLabel; // Safe: textContent
    
    const count = document.createElement('span');
    count.className = 'canceled-bar-count';
    count.textContent = item.count; // Safe: textContent
    
    header.appendChild(label);
    header.appendChild(count);
    
    // Create track
    const track = document.createElement('div');
    track.className = 'canceled-bar-track';
    
    // Create fill
    const fill = document.createElement('div');
    fill.className = `canceled-bar-fill ${colorClass}`;
    fill.setAttribute('role', 'progressbar');
    fill.setAttribute('aria-valuenow', item.count);
    fill.setAttribute('aria-valuemin', '0');
    fill.setAttribute('aria-valuemax', maxCount);
    
    track.appendChild(fill);
    
    barRow.appendChild(header);
    barRow.appendChild(track);
    
    container.appendChild(barRow);
    
    // Trigger animation
    setTimeout(() => {
      fill.style.width = `${percentage}%`;
    }, 50);
  });
}

// Fetch expense categories
async function fetchExpenseCategories() {
    try {
        const response = await fetch('/api/v1/expenses/categories');
        if (!response.ok) {
            throw new Error(`Failed to fetch categories: ${response.status}`);
        }
        
        const categories = await response.json();
        window.MarketingReportState.expenses.categories = categories;
        
        // Populate select dropdown
        const select = document.getElementById('expense-category');
        if (select) {
            select.textContent = '';
            const defaultOption = document.createElement('option');
            defaultOption.value = '';
            defaultOption.textContent = 'هۆکاری خەرجییەکە هەڵبژێرە';
            select.appendChild(defaultOption);
            categories.forEach(cat => {
                const option = document.createElement('option');
                option.value = cat.value;
                option.textContent = cat.kurdishLabel;
                select.appendChild(option);
            });
        }
    } catch (error) {
        console.error('Error fetching expense categories:', error);
    }
}

// Fetch current month expenses with pagination
async function fetchCurrentMonthExpenses() {
    window.MarketingReportState.expenses.loading = true;
    window.MarketingReportState.expenses.error = null;
    
    try {
        const page = window.MarketingReportState.expenses.pagination.currentPage;
        const response = await fetch(`/api/v1/expenses/current-month/paginated?page=${page}&size=10`);
        if (!response.ok) {
            throw new Error(`Failed to fetch expenses: ${response.status}`);
        }
        
        const pageData = await response.json();
        const expenses = pageData.content || [];
        
        window.MarketingReportState.expenses.currentMonthExpenses = expenses;
        window.MarketingReportState.expenses.pagination.currentPage = pageData.number || 0;
        window.MarketingReportState.expenses.pagination.totalPages = pageData.totalPages || 0;
        window.MarketingReportState.expenses.pagination.totalElements = pageData.totalElements || 0;
        
        // Fetch total
        const totalResponse = await fetch('/api/v1/expenses/current-month/total');
        if (totalResponse.ok) {
            const total = await totalResponse.json();
            window.MarketingReportState.expenses.total = total;
        }
        
        renderExpensesList();
    } catch (error) {
        console.error('Error fetching expenses:', error);
        window.MarketingReportState.expenses.error = error.message;
    } finally {
        window.MarketingReportState.expenses.loading = false;
    }
}

// Render expenses list (Granular DOM Updates - data-id reconciliation)
function renderExpensesList() {
    const container = document.getElementById('expenses-list');
    const totalElement = document.getElementById('expenses-total-amount');
    
    if (!container) return;
    
    const expenses = window.MarketingReportState.expenses.currentMonthExpenses;
    
    // Add unique DOM identifiers to expenses if not present
    expenses.forEach(expense => {
        if (!expense.domId) {
            expense.domId = `expense-${expense.id}`;
        }
    });
    
    // Get existing DOM elements with their data-expense-id
    const existingItems = new Map();
    container.querySelectorAll('[data-expense-id]').forEach(el => {
        existingItems.set(el.dataset.expenseId, el);
    });
    
    // Get current state IDs
    const currentStateIds = new Set(expenses.map(e => String(e.id)));
    
    // Remove items not in current state
    existingItems.forEach((el, id) => {
        if (!currentStateIds.has(id)) {
            el.remove();
        }
    });
    
    if (expenses.length === 0) {
        container.textContent = '';
        const emptyP = document.createElement('p');
        emptyP.className = 'text-muted';
        emptyP.style.textAlign = 'center';
        emptyP.style.padding = '20px';
        emptyP.textContent = 'هیچ خەرجییەک تۆمار نەکراوە';
        container.appendChild(emptyP);
        totalElement.textContent = '0 IQD';
        return;
    }
    
    // Add or update items
    expenses.forEach(expense => {
        let itemEl = existingItems.get(String(expense.id));
        
        if (!itemEl) {
            // Create new element
            itemEl = createExpenseElement(expense);
            container.appendChild(itemEl);
        }
        // Expenses are read-only, no update needed
    });
    
    totalElement.textContent = window.MarketingReportState.expenses.total.toFixed(2) + ' IQD';
    
    // Render pagination controls
    renderExpensesPagination();
}

// Create expense element with data-expense-id
function createExpenseElement(expense) {
    const item = document.createElement('div');
    item.className = 'expense-item';
    item.setAttribute('data-expense-id', String(expense.id));
    
    const date = new Date(expense.createdAt).toLocaleDateString('ku-IQ');
    
    // Safe DOM manipulation
    const infoDiv = document.createElement('div');
    infoDiv.className = 'expense-item-info';
    
    const categorySpan = document.createElement('span');
    categorySpan.className = 'expense-item-category';
    categorySpan.textContent = expense.categoryLabel;
    
    const dateSpan = document.createElement('span');
    dateSpan.className = 'expense-item-date';
    dateSpan.textContent = date;
    
    infoDiv.appendChild(categorySpan);
    infoDiv.appendChild(dateSpan);
    
    const amountSpan = document.createElement('span');
    amountSpan.className = 'expense-item-amount';
    amountSpan.textContent = expense.amount.toFixed(2) + ' IQD';
    
    // Delete button
    const deleteBtn = document.createElement('button');
    deleteBtn.className = 'expense-delete-btn';
    deleteBtn.setAttribute('data-expense-id', String(expense.id));
    deleteBtn.setAttribute('aria-label', 'Delete expense');
    
    const deleteIcon = document.createElement('i');
    deleteIcon.className = 'fa-solid fa-trash';
    deleteBtn.appendChild(deleteIcon);
    
    item.appendChild(infoDiv);
    item.appendChild(amountSpan);
    item.appendChild(deleteBtn);
    
    return item;
}

// Add new expense
async function addExpense() {
    const amountInput = document.getElementById('expense-amount');
    const categorySelect = document.getElementById('expense-category');
    const addButton = document.getElementById('btn-add-expense');
    
    if (!amountInput || !categorySelect || !addButton) return;
    
    const amount = parseFloat(amountInput.value);
    const categoryValue = categorySelect.value;
    
    if (isNaN(amount) || amount <= 0) {
        alert('تکایە بڕێکی دروست بنووسە');
        return;
    }
    
    if (!categoryValue) {
        alert('جۆری خەرجییەکە هەڵبژێرە');
        return;
    }
    
    addButton.disabled = true;
    
    try {
        const response = await fetch('/api/v1/expenses', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                amount: amount,
                category: categoryValue
            })
        });
        
        if (!response.ok) {
            throw new Error(`Failed to add expense: ${response.status}`);
        }
        
        // Clear form
        amountInput.value = '';
        categorySelect.value = '';
        
        // Reset to page 0 and refresh expenses list
        window.MarketingReportState.expenses.pagination.currentPage = 0;
        await fetchCurrentMonthExpenses();
        
    } catch (error) {
        console.error('Error adding expense:', error);
        alert('هەڵە لە زیادکردنی خەرجی: ' + error.message);
    } finally {
        addButton.disabled = false;
    }
}

// Render pagination controls for expenses
function renderExpensesPagination() {
    const container = document.getElementById('expenses-list');
    
    // Remove existing pagination controls
    const existingControls = container.querySelector('.expenses-pagination');
    if (existingControls) {
        existingControls.remove();
    }
    
    const pagination = window.MarketingReportState.expenses.pagination;
    
    // Don't render if no pages
    if (pagination.totalPages <= 1) {
        return;
    }
    
    // Create pagination container
    const paginationDiv = document.createElement('div');
    paginationDiv.className = 'expenses-pagination';
    
    // Prev button
    const prevBtn = document.createElement('button');
    prevBtn.className = 'pagination-btn';
    prevBtn.textContent = 'پێشوو';
    prevBtn.disabled = pagination.currentPage === 0;
    prevBtn.onclick = () => {
        if (pagination.currentPage > 0) {
            window.MarketingReportState.expenses.pagination.currentPage--;
            fetchCurrentMonthExpenses();
        }
    };
    
    // Page info
    const pageInfo = document.createElement('span');
    pageInfo.className = 'pagination-info';
    pageInfo.textContent = `پەڕە ${pagination.currentPage + 1} لە ${pagination.totalPages}`;
    
    // Next button
    const nextBtn = document.createElement('button');
    nextBtn.className = 'pagination-btn';
    nextBtn.textContent = 'دواتر';
    nextBtn.disabled = pagination.currentPage >= pagination.totalPages - 1;
    nextBtn.onclick = () => {
        if (pagination.currentPage < pagination.totalPages - 1) {
            window.MarketingReportState.expenses.pagination.currentPage++;
            fetchCurrentMonthExpenses();
        }
    };
    
    paginationDiv.appendChild(prevBtn);
    paginationDiv.appendChild(pageInfo);
    paginationDiv.appendChild(nextBtn);
    
    container.appendChild(paginationDiv);
}

// Setup event delegation for expense list (delete buttons)
function setupExpenseEventDelegation() {
    const expensesList = document.getElementById('expenses-list');
    if (!expensesList) return;
    
    expensesList.addEventListener('click', async (e) => {
        const deleteBtn = e.target.closest('.expense-delete-btn');
        if (!deleteBtn) return;
        
        const expenseId = deleteBtn.dataset.expenseId;
        if (!expenseId) return;
        
        // Confirm deletion
        if (!confirm('ئایا دڵنیای لە سڕینەوەی ئەم خەرجییە؟')) {
            return;
        }
        
        try {
            const response = await fetch(`/api/v1/expenses/${expenseId}`, {
                method: 'DELETE'
            });
            
            if (response.status === 204) {
                // Success - refresh expenses list
                await fetchCurrentMonthExpenses();
            } else if (response.status === 404) {
                alert('خەرجییەکە نەدۆزرایەوە');
                await fetchCurrentMonthExpenses();
            } else if (response.status === 409) {
                alert('هەڵە لە سڕینەوە: نەتوانرا سڕایە');
            } else {
                alert('هەڵە لە سڕینەوە: ' + response.status);
            }
        } catch (error) {
            console.error('Error deleting expense:', error);
            alert('هەڵە لە پەیوەندی کردن بە سێرڤەرەوە');
        }
    });
}

// ── Pareto Analysis Functions ─────────────────────────────────────────────

async function fetchParetoAnalysis() {
    const state = window.MarketingReportState.paretoData;
    state.loading = true;
    state.error = null;
    
    console.log('Fetching data for period:', state.selectedPeriod);
    
    try {
        const response = await fetch(
            `/api/v1/admin/reports/pareto-analysis?period=${state.selectedPeriod}`
        );
        
        if (!response.ok) {
            throw new Error(`Failed to fetch Pareto analysis: ${response.status}`);
        }
        
        const data = await response.json();
        state.services = data.services;
        console.log('Pareto data received:', state.services.length, 'services');
        
        renderParetoChart();
    } catch (error) {
        console.error('Error fetching Pareto analysis:', error);
        state.error = error.message;
    } finally {
        state.loading = false;
    }
}

function renderParetoChart() {
    const container = document.getElementById('pareto-chart-container');
    const data = window.MarketingReportState.paretoData.services;
    
    if (!container) {
        console.error('Pareto chart container not found');
        return;
    }
    
    // Clear container completely
    container.textContent = '';
    
    // Handle empty data or undefined data
    if (!data || data.length === 0) {
        const emptyP = document.createElement('p');
        emptyP.className = 'text-muted';
        emptyP.style.textAlign = 'center';
        emptyP.style.padding = '40px';
        emptyP.textContent = 'هیچ داتایەک نییە';
        container.appendChild(emptyP);
        return;
    }
    
    // Handle edge case: maxProfit is 0 or NaN
    const maxProfit = Math.max(...data.map(d => d.absoluteProfit || 0));
    if (maxProfit === 0 || isNaN(maxProfit)) {
        const emptyP = document.createElement('p');
        emptyP.className = 'text-muted';
        emptyP.style.textAlign = 'center';
        emptyP.style.padding = '40px';
        emptyP.textContent = 'هیچ قازانجێک تۆمار نەکراوە';
        container.appendChild(emptyP);
        return;
    }
    
    try {
        // Defensive dimension fallbacks
        const width = container.clientWidth || container.parentElement.clientWidth || 800;
        const height = container.clientHeight || 400;
        const margin = { top: 20, right: 60, bottom: 40, left: 60 };
        const chartWidth = width - margin.left - margin.right;
        const chartHeight = height - margin.top - margin.bottom;
        
        const scaledMaxProfit = maxProfit * 1.1;
        
        const svgNS = "http://www.w3.org/2000/svg";
        const svg = document.createElementNS(svgNS, "svg");
        svg.setAttribute("width", String(width));
        svg.setAttribute("height", String(height));
        
        const g = document.createElementNS(svgNS, "g");
        g.setAttribute("transform", `translate(${margin.left},${margin.top})`);
        svg.appendChild(g);
        
        // Grid lines
        for (let i = 0; i <= 5; i++) {
            const y = chartHeight - (i / 5) * chartHeight;
            const line = document.createElementNS(svgNS, "line");
            line.setAttribute("x1", "0");
            line.setAttribute("x2", String(chartWidth));
            line.setAttribute("y1", String(y));
            line.setAttribute("y2", String(y));
            line.setAttribute("stroke", "#f1f5f9");
            line.setAttribute("stroke-dasharray", "3 3");
            g.appendChild(line);
        }
        
        const barWidth = 60;
        const step = chartWidth / data.length;
        
        // Draw Bars (slate-800: #1e293b)
        data.forEach((d, i) => {
            const x = (i * step) + (step / 2);
            const barHeight = (d.absoluteProfit / scaledMaxProfit) * chartHeight;
            const y = chartHeight - barHeight;
        
            const rect = document.createElementNS(svgNS, "rect");
            rect.setAttribute("x", String(x - (barWidth / 2)));
            rect.setAttribute("y", String(y));
            rect.setAttribute("width", String(barWidth));
            rect.setAttribute("height", String(barHeight));
            rect.setAttribute("fill", "#1e293b");
            rect.setAttribute("rx", "4");
        
            rect.addEventListener("mouseover", (e) => showParetoTooltip(e, d, x + margin.left, y + margin.top));
            rect.addEventListener("mouseout", hideParetoTooltip);
            g.appendChild(rect);
        
            // X-Axis Label (Kurdish name)
            const text = document.createElementNS(svgNS, "text");
            text.setAttribute("x", String(x));
            text.setAttribute("y", String(chartHeight + 20));
            text.setAttribute("text-anchor", "middle");
            text.textContent = d.serviceKurdishName;
            g.appendChild(text);
        });
    
        // Y-Axis Right (Profit)
        for (let i = 0; i <= 5; i++) {
            const val = (scaledMaxProfit / 5) * i;
            const y = chartHeight - (i / 5) * chartHeight;
            const text = document.createElementNS(svgNS, "text");
            text.setAttribute("x", String(chartWidth + 10));
            text.setAttribute("y", String(y + 4));
            text.setAttribute("text-anchor", "start");
            text.textContent = `${Math.round(val / 1000)}k`;
            g.appendChild(text);
        }
    
        // Y-Axis Left (Percentage)
        for (let i = 0; i <= 5; i++) {
            const val = 20 * i;
            const y = chartHeight - (i / 5) * chartHeight;
            const text = document.createElementNS(svgNS, "text");
            text.setAttribute("x", "-10");
            text.setAttribute("y", String(y + 4));
            text.setAttribute("text-anchor", "end");
            text.textContent = `${val}%`;
            g.appendChild(text);
        }
    
        // 80% Threshold Line (red dashed: #ef4444)
        const thresholdY = chartHeight - (80 / 100) * chartHeight;
        const tLine = document.createElementNS(svgNS, "line");
        tLine.setAttribute("x1", "0");
        tLine.setAttribute("x2", String(chartWidth));
        tLine.setAttribute("y1", String(thresholdY));
        tLine.setAttribute("y2", String(thresholdY));
        tLine.setAttribute("stroke", "#ef4444");
        tLine.setAttribute("stroke-dasharray", "5 5");
        tLine.setAttribute("stroke-width", "2");
        g.appendChild(tLine);
    
        const tText = document.createElementNS(svgNS, "text");
        tText.setAttribute("x", "10");
        tText.setAttribute("y", String(thresholdY - 8));
        tText.setAttribute("fill", "#ef4444");
        tText.setAttribute("font-weight", "600");
        tText.textContent = "٨٠٪ Threshold";
        g.appendChild(tText);
    
        // Cumulative Line (rose-500: #f43f5e)
        let pathD = "";
        data.forEach((d, i) => {
            const x = (i * step) + (step / 2);
            const y = chartHeight - (d.cumulativePercentage / 100) * chartHeight;
            pathD += `${i === 0 ? 'M' : 'L'} ${x} ${y} `;
        });
    
        const path = document.createElementNS(svgNS, "path");
        path.setAttribute("d", pathD.trim());
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", "#f43f5e");
        path.setAttribute("stroke-width", "3");
        g.appendChild(path);
    
        // Dots on line
        data.forEach((d, i) => {
            const x = (i * step) + (step / 2);
            const y = chartHeight - (d.cumulativePercentage / 100) * chartHeight;
            const circle = document.createElementNS(svgNS, "circle");
            circle.setAttribute("cx", String(x));
            circle.setAttribute("cy", String(y));
            circle.setAttribute("r", "6");
            circle.setAttribute("fill", "#f43f5e");
            circle.setAttribute("stroke", "#ffffff");
            circle.setAttribute("stroke-width", "2");
            g.appendChild(circle);
        });
    
        container.appendChild(svg);
    } catch (error) {
        console.error('Error rendering Pareto chart:', error);
        container.textContent = '';
        const errorP = document.createElement('p');
        errorP.className = 'text-muted';
        errorP.style.textAlign = 'center';
        errorP.style.padding = '40px';
        errorP.textContent = 'هەڵە لە ڕێندەری چارت';
        container.appendChild(errorP);
    }
}

function showParetoTooltip(e, d, x, y) {
    const tooltip = document.getElementById('pareto-tooltip');
    const formatIQD = (val) => new Intl.NumberFormat('ar-IQ', {
        style: 'currency', currency: 'IQD', maximumFractionDigits: 0
    }).format(val);
    
    tooltip.textContent = '';
    
    const titleDiv = document.createElement('div');
    titleDiv.className = 'tooltip-title';
    titleDiv.textContent = d.serviceKurdishName;
    
    const profitDiv = document.createElement('div');
    profitDiv.className = 'tooltip-row tooltip-profit';
    profitDiv.textContent = 'قازانج: ';
    
    const profitSpan = document.createElement('span');
    profitSpan.className = 'tooltip-val';
    profitSpan.textContent = formatIQD(d.absoluteProfit);
    
    profitDiv.appendChild(profitSpan);
    
    const percentDiv = document.createElement('div');
    percentDiv.className = 'tooltip-row tooltip-percent';
    percentDiv.textContent = 'ڕێژەی کەڵەکەبوو: ';
    
    const percentSpan = document.createElement('span');
    percentSpan.className = 'tooltip-val';
    percentSpan.textContent = d.cumulativePercentage.toFixed(1) + '%';
    
    percentDiv.appendChild(percentSpan);
    
    tooltip.appendChild(titleDiv);
    tooltip.appendChild(profitDiv);
    tooltip.appendChild(percentDiv);
    
    tooltip.style.left = `${x}px`;
    tooltip.style.top = `${y}px`;
    tooltip.style.opacity = '1';
}

function hideParetoTooltip() {
    const tooltip = document.getElementById('pareto-tooltip');
    tooltip.style.opacity = '0';
}

// Add event listener for add expense button
document.addEventListener('DOMContentLoaded', () => {
    const addButton = document.getElementById('btn-add-expense');
    if (addButton) {
        addButton.addEventListener('click', addExpense);
    }
    
    // Setup event delegation for expense delete buttons
    setupExpenseEventDelegation();
    
    // Initialize expenses data
    try {
        fetchExpenseCategories();
        fetchCurrentMonthExpenses();
    } catch (e) { console.error('Error initializing Expenses:', e); }
    
    // Initialize marketing report (correct function name: fetchMarketingROI)
    try {
        if (typeof fetchMarketingROI === 'function') {
            fetchMarketingROI();
            setupEventDelegation();
        } else {
            console.error('CRITICAL: fetchMarketingROI is missing!');
        }
    } catch (e) { console.error('Error initializing Marketing Report:', e); }
    
    // Initialize Pareto Chart
    try {
        if (typeof fetchParetoAnalysis === 'function') {
            fetchParetoAnalysis();
        }
    } catch (e) { console.error('Error initializing Pareto Chart:', e); }
    
    // Attach event delegation for Pareto Buttons
    const paretoPeriodSelector = document.getElementById('pareto-period-selector');
    if (paretoPeriodSelector) {
        paretoPeriodSelector.addEventListener('click', (e) => {
            const button = e.target.closest('.period-button');
            if (!button) return;
            const period = button.dataset.period;
            if (!period) return;
            
            window.MarketingReportState.paretoData.selectedPeriod = period;
            document.querySelectorAll('#pareto-period-selector .period-button').forEach(btn => btn.classList.remove('active'));
            button.classList.add('active');
            
            console.log('Fetching data for period:', period);
            fetchParetoAnalysis();
        });
    }
    
    // Initialize report generation functionality
    initializeReportGeneration();
});

/* ==========================================================================
 * REPORT GENERATION FUNCTIONS
 * ======================================================================== */

function initializeReportGeneration() {
    // Event delegation for report generation buttons
    const reportsButtonsContainer = document.querySelector('.reports-buttons-container');
    if (reportsButtonsContainer) {
        reportsButtonsContainer.addEventListener('click', function(e) {
            const button = e.target.closest('.report-button');
            if (!button) return;
            
            const reportType = button.dataset.reportType;
            if (!reportType) return;
            
            generateReport(reportType);
        });
    }
    
    // Modal close button
    const reportModalClose = document.getElementById('report-modal-close');
    if (reportModalClose) {
        reportModalClose.addEventListener('click', hideReportModal);
    }
    
    // Close modal on backdrop click
    const reportModal = document.getElementById('report-modal');
    if (reportModal) {
        reportModal.addEventListener('click', function(e) {
            if (e.target === reportModal || e.target.classList.contains('report-modal-backdrop')) {
                hideReportModal();
            }
        });
    }
    
    // Export buttons
    const exportCsvBtn = document.getElementById('export-csv-btn');
    if (exportCsvBtn) {
        exportCsvBtn.addEventListener('click', exportToCsv);
    }
    
    const exportPdfBtn = document.getElementById('export-pdf-btn');
    if (exportPdfBtn) {
        exportPdfBtn.addEventListener('click', exportToPdf);
    }
}

async function generateReport(reportType) {
    const state = window.MarketingReportState.reportData;
    state.currentReportType = reportType;
    state.loading = true;
    state.error = null;
    
    // Show modal with loading state
    showReportModal();
    showReportLoading();
    
    try {
        const response = await fetch('/api/v1/admin/reports/generate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reportType: reportType })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        
        const data = await response.json();
        
        // Store report text in state
        state.currentReportText = data.reportText;
        
        // Display report using safe DOM manipulation
        displayReportText(data.reportText);
        
    } catch (error) {
        console.error('Error generating report:', error);
        state.error = error.message;
        showReportError(error.message);
    } finally {
        state.loading = false;
    }
}

function showReportModal() {
    const modal = document.getElementById('report-modal');
    if (modal) {
        modal.classList.remove('hidden');
    }
}

function hideReportModal() {
    const modal = document.getElementById('report-modal');
    if (modal) {
        modal.classList.add('hidden');
    }
    
    // Reset modal state
    const reportLoading = document.getElementById('report-loading');
    const reportContent = document.getElementById('report-content');
    const reportError = document.getElementById('report-error');
    
    if (reportLoading) reportLoading.classList.add('hidden');
    if (reportContent) reportContent.classList.add('hidden');
    if (reportError) reportError.classList.add('hidden');
}

function showReportLoading() {
    const reportLoading = document.getElementById('report-loading');
    const reportContent = document.getElementById('report-content');
    const reportError = document.getElementById('report-error');
    
    if (reportLoading) reportLoading.classList.remove('hidden');
    if (reportContent) reportContent.classList.add('hidden');
    if (reportError) reportError.classList.add('hidden');
}

function displayReportText(text) {
    const reportLoading = document.getElementById('report-loading');
    const reportContent = document.getElementById('report-content');
    const reportError = document.getElementById('report-error');
    const reportTextElement = document.getElementById('report-text');
    
    // Hide loading and error
    if (reportLoading) reportLoading.classList.add('hidden');
    if (reportError) reportError.classList.add('hidden');
    
    // Show content
    if (reportContent) reportContent.classList.remove('hidden');
    
    // SAFE DOM MANIPULATION: Use textContent instead of innerHTML
    if (reportTextElement) {
        reportTextElement.textContent = text;
    }
}

function showReportError(message) {
    const reportLoading = document.getElementById('report-loading');
    const reportContent = document.getElementById('report-content');
    const reportError = document.getElementById('report-error');
    const reportErrorMessage = document.getElementById('report-error-message');
    
    // Hide loading and content
    if (reportLoading) reportLoading.classList.add('hidden');
    if (reportContent) reportContent.classList.add('hidden');
    
    // Show error
    if (reportError) reportError.classList.remove('hidden');
    
    // SAFE DOM MANIPULATION: Use textContent for error message
    if (reportErrorMessage) {
        reportErrorMessage.textContent = 'هەڵە: ' + message;
    }
}

async function exportToCsv() {
    const state = window.MarketingReportState.reportData;
    if (!state.currentReportType) return;
    
    try {
        const response = await fetch('/api/v1/admin/reports/export/csv', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reportType: state.currentReportType })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        
        // Get filename from Content-Disposition header
        const contentDisposition = response.headers.get('Content-Disposition');
        let filename = 'ghasl_report.csv';
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
        
    } catch (error) {
        console.error('Error exporting to CSV:', error);
        alert('هەڵە لە داگرتنی CSV: ' + error.message);
    }
}

async function exportToPdf() {
    const state = window.MarketingReportState.reportData;
    if (!state.currentReportType) return;
    
    try {
        const response = await fetch('/api/v1/admin/reports/export/pdf', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reportType: state.currentReportType })
        });
        
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        
        // Get filename from Content-Disposition header
        const contentDisposition = response.headers.get('Content-Disposition');
        let filename = 'ghasl_report.txt';
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
        
    } catch (error) {
        console.error('Error exporting to PDF:', error);
        alert('هەڵە لە داگرتنی PDF: ' + error.message);
    }
}
