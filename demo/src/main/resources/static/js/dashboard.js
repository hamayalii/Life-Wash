"use strict";

/* ==========================================================================
 * API LAYER
 * ======================================================================== */

const API_BASE = "/api/v1";
const AUTO_REFRESH_INTERVAL = 60000; // 60 seconds

/** GET /api/v1/admin/dashboard/summary */
async function fetchDashboardStats() {
  const res = await fetch(`${API_BASE}/admin/dashboard/summary?period=month`);
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      window.location.href = "/";
      throw new Error("Session expired");
    }
    throw new Error(`HTTP ${res.status}`);
  }
  const data = await res.json();
  return [
    { id: "customers", label: "کڕیارەکان", value: data.customers.toLocaleString(), delta: data.weeklyGrowthPercent.toFixed(2) + "%", trend: data.weeklyGrowthPercent >= 0 ? "up" : "down", caption: "بەراورد بە مانگی ڕابردوو" },
    { id: "orders", label: "داواکاریەکان", value: data.orders.toLocaleString(), delta: (data.ordersChangePercentage !== null && data.ordersChangePercentage !== undefined && !isNaN(data.ordersChangePercentage)) ? data.ordersChangePercentage.toFixed(2) + "%" : "N/A", trend: (data.isOrdersTrendPositive === true) ? "up" : (data.isOrdersTrendPositive === false) ? "down" : "up", caption: "بەراورد بە مانگی ڕابردوو" },
    { id: "earnings", label: "داهات", value: data.profit.toLocaleString() + " دینار", delta: data.weeklyGrowthPercent.toFixed(2) + "%", trend: data.weeklyGrowthPercent >= 0 ? "up" : "down", caption: "بەراورد بە مانگی ڕابردوو" },
    { id: "growth", label: "گەشەسەندن", value: (data.monthlyGrowthPercent >= 0 ? "+" : "") + data.monthlyGrowthPercent.toFixed(2) + "%", delta: "N/A", trend: data.monthlyGrowthPercent >= 0 ? "up" : "down", caption: "بەراورد بە مانگی ڕابردوو" },
  ];
}

/** GET /api/v1/admin/dashboard/revenue-trend */
async function fetchRevenue() {
  const res = await fetch(`${API_BASE}/admin/dashboard/revenue-trend?period=week`);
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      window.location.href = "/";
      throw new Error("Session expired");
    }
    throw new Error(`HTTP ${res.status}`);
  }
  const data = await res.json();

  // Map English day names to Kurdish
  const kurdishDayMap = {
    "Mon": "دووشەممە",
    "Tue": "سێشەممە",
    "Wed": "چوارشەممە",
    "Thu": "پێنجشەممە",
    "Fri": "هەینی",
    "Sat": "شەممە",
    "Sun": "یەکشەممە"
  };

  const labels = data.map(d => kurdishDayMap[d.label] || d.label);
  const amounts = data.map(d => d.amountIQD);
  const total = amounts.reduce((sum, val) => sum + (val || 0), 0);

  return {
    labels: labels,
    current: amounts,
    previous: data.map(() => 0), // TODO: implement previous period comparison in backend
    total: total
  };
}

// Global variable to store "Others" services for drill-down modal
let othersServicesData = [];

/** GET /api/v1/admin/dashboard/top-services */
async function fetchSalesChannels() {
  const cacheBuster = new Date().getTime();
  const res = await fetch(`${API_BASE}/admin/dashboard/top-services?period=month&_=${cacheBuster}`);
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      window.location.href = "/";
      throw new Error("Session expired");
    }
    throw new Error(`HTTP ${res.status}`);
  }
  const data = await res.json();
  // Sort by count descending to ensure strict ordering
  const sortedData = data.sort((a, b) => b.count - a.count);
  // Combine beyond top 4 into "ئەوانی تر" (Others) with gray color
  const top4 = sortedData.slice(0, 4);
  const others = sortedData.slice(4);
  const othersCount = others.reduce((sum, item) => sum + item.count, 0);

  // Store others data for drill-down modal
  othersServicesData = others;

  const result = top4.map(item => ({
    id: item.id || item.label, // Use label as fallback for id
    label: item.label, // Kurdish name from backend
    amount: item.count,
    color: getServiceColor(item.label)
  }));

  if (othersCount > 0) {
    result.push({
      id: "others",
      label: "ئەوانی تر",
      amount: othersCount,
      color: "#9CA3AF" // gray
    });
  }

  return result;
}

/** GET /api/v1/admin/dashboard/requests?page=0&size=10 */
async function fetchOrders(page = 0, size = 10) {
  const res = await fetch(`${API_BASE}/admin/dashboard/requests?page=${page}&size=${size}`);
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) {
      window.location.href = "/";
      throw new Error("Session expired");
    }
    throw new Error(`HTTP ${res.status}`);
  }
  const data = await res.json();
  console.log("RAW API Response from fetchOrders:", data);
  console.log("Response type:", typeof data);
  console.log("Has content property:", 'content' in data);
  console.log("Has page property:", 'page' in data);
  
  // Validate response structure
  if (!data || typeof data !== 'object') {
    console.error("Invalid response: data is not an object");
    throw new Error("Invalid API response structure");
  }
  
  if (!('content' in data) || !('page' in data)) {
    console.error("Invalid response: missing content or page property", data);
    throw new Error("API response does not match expected schema");
  }
  
  // Extract pagination metadata from nested page object
  const pageMetadata = data.page;
  console.log("Page metadata:", pageMetadata);
  
  // Flatten the response for compatibility with renderPagination
  // Create a normalized object matching Spring Boot Page<T> structure
  const normalizedResponse = {
    content: data.content,
    number: pageMetadata.number,
    totalPages: pageMetadata.totalPages,
    totalElements: pageMetadata.totalElements,
    size: pageMetadata.size,
    isFirst: pageMetadata.number === 0,
    isLast: pageMetadata.number === pageMetadata.totalPages - 1
  };
  
  console.log("Normalized response for pagination:", normalizedResponse);
  return normalizedResponse;
}

function getServiceColor(serviceName) {
  // Kurdish service name color mapping
  const colors = {
    "فەرش": "#FF6384",
    "کومبار": "#36A2EB",
    "پەردە": "#FFCE56",
    "بەتانی": "#4BC0C0",
    "قەنەفە": "#9966FF",
    "تەنکی سەربان": "#FF9F40",
    "پاککردنەوەی ماڵ/شوقە/باخ": "#C9CBCF",
    // Legacy English rugType fallbacks
    "rug": "#3B82F6",
    "carpet": "#8B5CF6",
    "shag": "#F59E0B",
    "silk": "#10B981",
    "synthetic": "#EF4444",
    "wool": "#F97316",
    "persian": "#6B7280",
    "antique": "#8B5CF6"
  };

  // Return mapped color if exists
  if (colors[serviceName]) {
    return colors[serviceName];
  }

  // Fallback: generate consistent color from string hash
  return generateColorFromString(serviceName);
}

// Generate a consistent hex color from any string using hash
function generateColorFromString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash);
  }

  // Convert hash to HSL for beautiful, distinct colors
  const hue = Math.abs(hash % 360);
  const saturation = 70 + (Math.abs(hash) % 20); // 70-90% for vibrancy
  const lightness = 45 + (Math.abs(hash) % 15);  // 45-60% for readability

  return hslToHex(hue, saturation, lightness);
}

// Convert HSL to Hex
function hslToHex(h, s, l) {
  l /= 100;
  const a = s * Math.min(l, 1 - l) / 100;
  const f = n => {
    const k = (n + h / 30) % 12;
    const color = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1);
    return Math.round(255 * color).toString(16).padStart(2, '0');
  };
  return `#${f(0)}${f(8)}${f(4)}`;
}

/* ==========================================================================
 * DOM RENDERING
 * ======================================================================== */

/** Escape strings before injecting as HTML. */
function esc(str) {
  const div = document.createElement("div");
  div.textContent = String(str);
  return div.innerHTML;
}

function renderStats(stats) {
  const container = document.getElementById("stats-container");
  if (!container) return;

  container.innerHTML = stats
    .map((card) => {
      const up = card.trend === "up";
      const trendColor = up ? "text-emerald-500" : "text-rose-500";
      const arrow = up ? "&#8593;" : "&#8595;"; // up / down
      const showDelta = card.delta !== "N/A";
      return `
        <article class="flex flex-col gap-3 rounded-xl bg-white p-6 shadow-sm">
          <h3 class="text-sm text-slate-500">${esc(card.label)}</h3>
          <p class="text-3xl font-medium text-slate-800">${esc(card.value)}</p>
          ${showDelta ? `
          <p class="flex items-center gap-1 text-sm ${trendColor}">
            <span aria-hidden="true">${arrow}</span><span>${esc(card.delta)}</span>
          </p>` : ''}
          <p class="text-xs text-slate-400">${esc(card.caption)}</p>
        </article>`;
    })
    .join("");
}

function renderOrders(orders) {
  const body = document.getElementById("orders-body");
  if (!body) return;

  // Clear existing content
  body.innerHTML = "";

  orders.forEach((o) => {
    const tr = document.createElement("tr");
    tr.className = "border-b border-slate-50 last:border-0 hover:bg-slate-50/60";

    // Customer name cell with note icon
    const customerNameCell = document.createElement("td");
    customerNameCell.className = "py-4 pr-4 text-sm text-slate-700";
    
    const customerNameSpan = document.createElement("span");
    customerNameSpan.textContent = o.customerName || "";
    customerNameCell.appendChild(customerNameSpan);

    // Add note icon if message exists
    if (o.message && o.message.trim()) {
      const noteIcon = document.createElement("i");
      noteIcon.className = "fas fa-exclamation-circle order-note-icon";
      noteIcon.dataset.message = o.message; // Store message securely in dataset
      noteIcon.style.marginRight = "8px";
      customerNameCell.appendChild(noteIcon);
    }

    // Address cell
    const addressCell = document.createElement("td");
    addressCell.className = "py-4 pr-4 text-sm text-slate-500";
    addressCell.textContent = o.address && o.address.trim() ? o.address : "-";

    // Request date cell
    const requestedAtCell = document.createElement("td");
    requestedAtCell.className = "py-4 pr-4 text-sm text-slate-500";
    requestedAtCell.textContent = o.requestedAt || "";

    // Service names cell
    const serviceNamesCell = document.createElement("td");
    serviceNamesCell.className = "py-4 pr-4 text-sm text-slate-500";
    serviceNamesCell.textContent = o.serviceNamesFormatted || "";

    // Quantity label cell
    const quantityLabelCell = document.createElement("td");
    quantityLabelCell.className = "py-4 pr-4 text-sm text-slate-500";
    quantityLabelCell.textContent = o.quantityLabel || "";

    // Price cell
    const priceCell = document.createElement("td");
    priceCell.className = "py-4 pr-4 text-sm text-slate-500";
    priceCell.textContent = o.price ? o.price.toLocaleString() + " دینار" : "Pending";

    // Status cell
    const statusCell = document.createElement("td");
    statusCell.className = "py-4 pr-4 text-sm";

    if (o.workStatus === "PENDING") {
      const statusDiv = document.createElement("div");
      statusDiv.className = "flex flex-col gap-2";

      const acceptBtn = document.createElement("button");
      acceptBtn.className = "glass-btn glass-btn-accept";
      acceptBtn.textContent = "قبوڵکردن";
      acceptBtn.onclick = () => acceptOrder(o.orderId);

      const rejectBtn = document.createElement("button");
      rejectBtn.className = "glass-btn glass-btn-reject";
      rejectBtn.textContent = "ڕەتکردنەوە";
      rejectBtn.onclick = () => rejectOrder(o.orderId);

      const priceBtn = document.createElement("button");
      priceBtn.className = "glass-btn glass-btn-price";
      priceBtn.textContent = "گۆڕینی نرخ";
      priceBtn.onclick = () => updatePrice(o.orderId);

      statusDiv.appendChild(acceptBtn);
      statusDiv.appendChild(rejectBtn);
      statusDiv.appendChild(priceBtn);
      statusCell.appendChild(statusDiv);
    } else if (o.workStatus === "ACCEPTED") {
      const statusDiv = document.createElement("div");
      statusDiv.className = "flex flex-col gap-2";

      const acceptedSpan = document.createElement("span");
      acceptedSpan.className = "text-emerald-600 font-medium";
      acceptedSpan.textContent = "وەرگیراو";

      const revertBtn = document.createElement("button");
      revertBtn.className = "glass-btn glass-btn-revert";
      revertBtn.textContent = "گەڕاندنەوە";
      revertBtn.onclick = () => revertOrder(o.orderId);

      statusDiv.appendChild(acceptedSpan);
      statusDiv.appendChild(revertBtn);
      statusCell.appendChild(statusDiv);
    } else if (o.workStatus === "REJECTED") {
      const statusDiv = document.createElement("div");
      statusDiv.className = "flex flex-col gap-2";

      const rejectedSpan = document.createElement("span");
      rejectedSpan.className = "text-rose-600 font-medium";
      rejectedSpan.textContent = "ڕەتکراوە";

      const revertBtn = document.createElement("button");
      revertBtn.className = "glass-btn glass-btn-revert";
      revertBtn.textContent = "گەڕاندنەوە";
      revertBtn.onclick = () => revertOrder(o.orderId);

      statusDiv.appendChild(rejectedSpan);
      statusDiv.appendChild(revertBtn);
      statusCell.appendChild(statusDiv);
    } else {
      statusCell.textContent = o.workStatus || "";
    }

    tr.appendChild(customerNameCell);
    tr.appendChild(addressCell);
    tr.appendChild(requestedAtCell);
    tr.appendChild(serviceNamesCell);
    tr.appendChild(quantityLabelCell);
    tr.appendChild(priceCell);
    tr.appendChild(statusCell);

    body.appendChild(tr);
  });
}

function renderSalesLegend(channels) {
  const legend = document.getElementById("sales-legend");
  if (!legend) return;

  legend.innerHTML = channels
    .map(
      (c) => `
      <li class="flex items-center justify-between text-sm">
        <span class="flex items-center gap-2 text-slate-600">
          <span class="inline-block h-2.5 w-2.5 rounded-full" style="background-color:${esc(c.color)}"></span>
          ${esc(c.label)}
        </span>
        <span class="text-slate-400">${esc(c.amount.toLocaleString())}</span>
      </li>`
    )
    .join("");
}

function renderPagination(ordersPage) {
  const paginationInfo = document.getElementById("pagination-info");
  const prevButton = document.getElementById("prev-page");
  const nextButton = document.getElementById("next-page");
  
  if (!paginationInfo || !prevButton || !nextButton) {
    console.error("Pagination elements not found");
    return;
  }
  
  console.log("Rendering pagination with normalized object:", ordersPage);
  
  // Update pagination state from normalized response
  currentPage = ordersPage.number;
  totalPages = ordersPage.totalPages;
  const totalElements = ordersPage.totalElements;
  const pageSize = ordersPage.size;
  
  // Display page info (e.g., "Page 1 of 5 (50 total)")
  const startItem = currentPage * pageSize + 1;
  const endItem = Math.min((currentPage + 1) * pageSize, totalElements);
  paginationInfo.textContent = `پەڕە ${currentPage + 1} لە ${totalPages} (${totalElements} کۆی گشتی)`;
  
  // Enable/disable buttons based on page position
  prevButton.disabled = ordersPage.isFirst;
  nextButton.disabled = ordersPage.isLast;
}

/* ---- Chart renderers (guarded so a Chart.js failure never breaks the page) -- */

let profitChartInstance = null;
let salesChartInstance = null;

// Kurdish month name mapping for localization
const kurdishMonths = {
  "January": "کانوونی دووەم", "February": "شوبات", "March": "ئازار",
  "April": "نیسان", "May": "ئایار", "June": "حوزەیران",
  "July": "تەممووز", "August": "ئاب", "September": "ئەیلوول",
  "October": "تشرینی یەکەم", "November": "تشرینی دووەم", "December": "کانوونی یەکەم",
  "Jan": "کانوونی دووەم", "Feb": "شوبات", "Mar": "ئازار", "Apr": "نیسان",
  "Jun": "حوزەیران", "Jul": "تەممووز", "Aug": "ئاب", "Sep": "ئەیلوول",
  "Oct": "تشرینی یەکەم", "Nov": "تشرینی دووەم", "Dec": "کانوونی یەکەم"
};

// Render monthly profit chart (replaces weekly profit summary)
async function renderMonthlyProfitChart() {
  const canvas = document.getElementById("profitChart");
  if (!canvas || typeof Chart === "undefined") return;

  // Destroy existing chart if it exists
  if (profitChartInstance) {
    profitChartInstance.destroy();
  }

  // Fetch monthly profits from API
  const response = await fetch('/api/v1/admin/dashboard/monthly-profits?months=12');
  if (!response.ok) {
    throw new Error(`Failed to fetch monthly profits: ${response.status}`);
  }

  const profits = await response.json();
  const labels = profits.map(p => kurdishMonths[p.month] || p.month);
  const data = profits.map(p => p.profit);
  const previousYearData = profits.map(p => p.previousYearProfit);
  
  profitChartInstance = new Chart(canvas.getContext("2d"), {
    type: "line",
    data: {
      labels: labels,
      datasets: [
        {
          label: "قازانجی مانگانەی ئەمساڵ",
          data: data,
          borderColor: "#3B82F6",
          backgroundColor: "rgba(59,130,246,0.12)",
          borderWidth: 3, fill: true, tension: 0.4, pointRadius: 0,
        },
        {
          label: "ساڵی ڕابردوو",
          data: previousYearData,
          borderColor: "#CBD5E1",
          borderDash: [5, 5], borderWidth: 2, fill: false, tension: 0.4, pointRadius: 0,
        },
      ],
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: { position: "top", labels: { usePointStyle: true } },
        tooltip: {
          backgroundColor: "#FFFFFF",
          titleColor: "#1E293B",
          bodyColor: "#1E293B",
          borderColor: "#E2E8F0",
          borderWidth: 1,
          cornerRadius: 8,
          padding: 12,
          displayColors: false,
          callbacks: {
            label: function (context) {
              return context.parsed.y.toLocaleString() + " دینار";
            }
          }
        }
      },
      scales: {
        y: {
          beginAtZero: true,
          grid: { color: "#F1F5F9" },
          ticks: {
            color: "#94A3B8",
            callback: function (value) {
              return value.toLocaleString() + " دینار";
            }
          }
        },
        x: {
          grid: { display: false },
          ticks: { color: "#1E293B", font: { family: undefined } }
        },
      },
    },
  });
  
  // Update summary text
  const summaryContainer = document.getElementById("profit-summary");
  if (summaryContainer) {
    const currentMonthProfit = profits[profits.length - 1]?.profit || 0;
    summaryContainer.innerHTML = `قازانجی ئەم مانگە: ${currentMonthProfit.toLocaleString()} دینار`;
    summaryContainer.style.color = currentMonthProfit >= 0 ? "green" : "red";
  }
}

function renderSalesChart(channels) {
  const canvas = document.getElementById("salesChart");
  if (!canvas || typeof Chart === "undefined") return;

  // Destroy existing chart if it exists
  if (salesChartInstance) {
    salesChartInstance.destroy();
  }

  salesChartInstance = new Chart(canvas.getContext("2d"), {
    type: "doughnut",
    data: {
      labels: channels.map((c) => c.label),
      datasets: [
        {
          data: channels.map((c) => c.amount),
          backgroundColor: channels.map((c) => c.color),
          borderWidth: 2, borderColor: "#fff",
        },
      ],
    },
    options: {
      responsive: true, maintainAspectRatio: false, cutout: "68%",
      plugins: { legend: { display: false } },
      onClick: (event, elements) => {
        if (elements.length > 0) {
          const index = elements[0].index;
          const clickedLabel = channels[index].label;
          if (clickedLabel === "ئەوانی تر") {
            openOthersModal();
          }
        }
      },
    },
  });
}

/* ==========================================================================
 * OTHERS MODAL FUNCTIONS
 * ======================================================================== */

function openOthersModal() {
  const modal = document.getElementById("others-modal");
  const modalBody = document.getElementById("others-modal-body");

  if (!modal || !modalBody) return;

  // Populate modal with others services data
  if (othersServicesData.length === 0) {
    modalBody.innerHTML = "<p class='text-slate-500'>هیچ خزمەتگوزارییەک نییە لەم بەشەدا</p>";
  } else {
    modalBody.innerHTML = othersServicesData
      .map(item => `
        <div class="flex justify-between items-center py-2 border-b border-slate-100 last:border-0">
          <span class="text-slate-700">${item.label}</span>
          <span class="font-medium text-slate-900">${item.count}</span>
        </div>
      `)
      .join("");
  }

  modal.classList.add("show");
}

function closeOthersModal() {
  const modal = document.getElementById("others-modal");
  if (modal) {
    modal.classList.remove("show");
  }
}

// Event listeners for modal close
document.addEventListener("DOMContentLoaded", () => {
  // Close button
  const closeBtn = document.getElementById("close-others-modal");
  if (closeBtn) {
    closeBtn.addEventListener("click", closeOthersModal);
  }

  // Background click
  const modal = document.getElementById("others-modal");
  if (modal) {
    modal.addEventListener("click", (e) => {
      if (e.target === modal) {
        closeOthersModal();
      }
    });
  }

  // Escape key
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      closeOthersModal();
    }
  });
});

/* ==========================================================================
 * LOADING & ERROR STATES
 * ======================================================================== */

function showLoading(sectionId) {
  const container = document.getElementById(sectionId);
  if (!container) return;
  container.innerHTML = `<div class="flex items-center justify-center h-full text-slate-400">
    <span class="animate-pulse">Loading...</span>
  </div>`;
}

function showError(sectionId, message) {
  const container = document.getElementById(sectionId);
  if (!container) return;
  container.innerHTML = `<div class="flex items-center justify-center h-full text-rose-500 text-sm">
    <span>${esc(message)}</span>
  </div>`;
}

function showRefreshError() {
  let indicator = document.getElementById("refresh-error-indicator");
  if (!indicator) {
    indicator = document.createElement("div");
    indicator.id = "refresh-error-indicator";
    indicator.className = "fixed bottom-4 right-4 bg-rose-500 text-white px-3 py-1 rounded text-xs shadow-lg";
    document.body.appendChild(indicator);
  }
  indicator.textContent = "Refresh failed - showing last data";
  indicator.style.display = "block";
  setTimeout(() => {
    indicator.style.display = "none";
  }, 3000);
}

/* ==========================================================================
 * BOOTSTRAP
 * ======================================================================== */

let isInitialLoad = true;
let currentPage = 0;
let totalPages = 0;
const pageSize = 10;

async function loadDashboard(isRefresh = false) {
  const errors = [];

  // Stats
  try {
    const stats = await fetchDashboardStats();
    renderStats(stats);
  } catch (err) {
    if (err.message === "Session expired") return; // Redirect already handled
    if (isRefresh) {
      showRefreshError();
    } else {
      showError("stats-container", "Failed to load stats");
    }
    errors.push("stats");
  }

  // Monthly profit chart
  try {
    await renderMonthlyProfitChart();
  } catch (err) {
    if (err.message === "Session expired") return;
    if (isRefresh) {
      showRefreshError();
    } else {
      showError("profitChart", "Failed to load monthly profits");
    }
    errors.push("profit");
  }

  // Top services (donut chart + legend)
  try {
    const channels = await fetchSalesChannels();
    renderSalesLegend(channels);
    renderSalesChart(channels);
  } catch (err) {
    if (err.message === "Session expired") return;
    if (isRefresh) {
      showRefreshError();
    } else {
      showError("salesChart", "Failed to load services");
    }
    errors.push("services");
  }

  // Requests list (paginated)
  try {
    const ordersPage = await fetchOrders(currentPage, pageSize);
    renderOrders(ordersPage.content);
    renderPagination(ordersPage);
  } catch (err) {
    if (err.message === "Session expired") return;
    if (isRefresh) {
      showRefreshError();
    } else {
      showError("orders-body", "Failed to load requests");
    }
    errors.push("orders");
  }

  isInitialLoad = false;
}

async function initDashboard() {
  // Show loading states on initial load
  if (isInitialLoad) {
    showLoading("stats-container");
    showLoading("profitChart");
    showLoading("salesChart");
    showLoading("orders-body");
  }

  await loadDashboard(false);

  // Start auto-refresh after initial load
  setInterval(() => {
    loadDashboard(true);
  }, AUTO_REFRESH_INTERVAL);
}

document.addEventListener("DOMContentLoaded", initDashboard);

/* ==========================================================================
 * NOTIFICATION SYSTEM
 * ======================================================================== */

const NOTIFICATION_API_BASE = "/api/v1/notifications";
const NOTIFICATION_POLL_INTERVAL = 300000; // 5 minutes

async function fetchUnreadCount() {
  try {
    const res = await fetch(`${NOTIFICATION_API_BASE}/unread-count`);
    if (!res.ok) {
      console.error("Failed to fetch unread count");
      return 0;
    }
    const data = await res.json();
    return data.count || 0;
  } catch (err) {
    console.error("Error fetching unread count:", err);
    return 0;
  }
}

async function updateNotificationBadge() {
  const count = await fetchUnreadCount();
  const badge = document.getElementById("notificationBadge");
  const notificationBtn = document.getElementById("notificationBtn");
  
  if (!badge || !notificationBtn) return;
  
  if (count > 0) {
    badge.textContent = count > 9 ? "9+" : count;
    badge.classList.remove("hidden");
    notificationBtn.classList.add("glow-red");
  } else {
    badge.classList.add("hidden");
    notificationBtn.classList.remove("glow-red");
  }
}

async function fetchNotifications() {
  try {
    const res = await fetch(NOTIFICATION_API_BASE);
    if (!res.ok) {
      console.error("Failed to fetch notifications");
      return [];
    }
    const data = await res.json();
    return data;
  } catch (err) {
    console.error("Error fetching notifications:", err);
    return [];
  }
}

function renderNotifications(notifications) {
  const notificationList = document.getElementById("notificationList");
  const noNotifications = document.getElementById("noNotifications");
  
  if (!notificationList || !noNotifications) return;
  
  if (!notifications || notifications.length === 0) {
    notificationList.innerHTML = "";
    noNotifications.classList.remove("hidden");
    return;
  }
  
  noNotifications.classList.add("hidden");
  
  notificationList.innerHTML = notifications.map(notification => {
    const typeLabel = notification.type === "DAILY" ? "ڕاپۆرتی ڕۆژانە" :
                      notification.type === "WEEKLY" ? "ڕاپۆرتی هەفتانە" :
                      notification.type === "MONTHLY" ? "ڕاپۆرتی مانگانە" : notification.type;
    
    const date = new Date(notification.createdAt).toLocaleString("ku-IQ");
    const readClass = notification.isRead ? "read" : "unread";
    
    return `
      <div class="notification-item ${readClass}" data-id="${notification.id}" onclick="markNotificationAsRead(${notification.id})">
        <div class="notification-type">${esc(typeLabel)}</div>
        <div class="notification-content">${esc(notification.content)}</div>
        <div class="notification-date">${esc(date)}</div>
      </div>
    `;
  }).join("");
}

async function markNotificationAsRead(notificationId) {
  try {
    const res = await fetch(`${NOTIFICATION_API_BASE}/${notificationId}/read`, {
      method: "POST"
    });
    
    if (!res.ok) {
      console.error("Failed to mark notification as read");
      return;
    }
    
    // Update UI to remove unread status
    const notificationItem = document.querySelector(`.notification-item[data-id="${notificationId}"]`);
    if (notificationItem) {
      notificationItem.classList.remove("unread");
      notificationItem.classList.add("read");
    }
    
    // Update badge count
    await updateNotificationBadge();
  } catch (err) {
    console.error("Error marking notification as read:", err);
  }
}

async function toggleNotificationDropdown() {
  const dropdown = document.getElementById("notificationDropdown");
  if (!dropdown) {
    console.error("Notification dropdown element not found");
    return;
  }
  
  const isShown = dropdown.classList.contains("show");
  
  if (!isShown) {
    // Fetch and render notifications when opening dropdown
    const notifications = await fetchNotifications();
    renderNotifications(notifications);
    dropdown.classList.add("show");
  } else {
    dropdown.classList.remove("show");
  }
}

// Initialize notification system using Event Delegation
function initNotificationSystem() {
  const dropdown = document.getElementById("notificationDropdown");
  
  console.log("Initializing notification system with Event Delegation...");
  console.log("Notification dropdown found:", !!dropdown);
  
  // Event Delegation: Attach listener to document.body
  document.body.addEventListener("click", async function(e) {
    const notificationBtn = e.target.closest("#notificationBtn");
    
    if (notificationBtn) {
      console.log("Bell clicked via Event Delegation");
      e.preventDefault();
      e.stopPropagation();
      await toggleNotificationDropdown();
      return;
    }
    
    // Close dropdown when clicking outside
    if (dropdown && !dropdown.classList.contains("hidden")) {
      const notificationContainer = document.querySelector(".notification-container");
      if (notificationContainer && !notificationContainer.contains(e.target)) {
        dropdown.classList.remove("show");
        dropdown.classList.add("hidden");
      }
    }
  });
  
  // Prevent clicks inside dropdown from bubbling to document
  if (dropdown) {
    dropdown.addEventListener("click", function(e) {
      e.stopPropagation();
    });
  }
  
  // Initial badge update
  updateNotificationBadge();
  
  // Poll for unread count every 5 minutes
  setInterval(updateNotificationBadge, NOTIFICATION_POLL_INTERVAL);
}

// Initialize notification system on DOMContentLoaded
if (document.readyState === 'loading') {
  document.addEventListener("DOMContentLoaded", initNotificationSystem);
} else {
  // DOM already loaded, initialize immediately
  initNotificationSystem();
}

/* ==========================================================================
 * CUSTOMER NOTE MODAL FUNCTIONS
 * ======================================================================== */

function openCustomerNoteModal(message) {
  const modal = document.getElementById("customerNoteModal");
  const noteText = document.getElementById("customer-note-text");
  
  if (!modal || !noteText) return;
  
  // Zero Trust: Use textContent to prevent XSS
  noteText.textContent = message;
  
  modal.classList.remove("hidden");
  modal.classList.add("show");
}

function closeCustomerNoteModal() {
  const modal = document.getElementById("customerNoteModal");
  if (modal) {
    modal.classList.remove("show");
    modal.classList.add("hidden");
  }
}

// Event Delegation: Single listener on orders table for note icon clicks
function initCustomerNoteModal() {
  const ordersBody = document.getElementById("orders-body");
  if (!ordersBody) return;
  
  ordersBody.addEventListener("click", function(e) {
    const noteIcon = e.target.closest(".order-note-icon");
    if (noteIcon) {
      const message = noteIcon.dataset.message;
      if (message) {
        openCustomerNoteModal(message);
      }
    }
  });
  
  // Close button listeners
  const closeXBtn = document.getElementById("close-customer-note-modal");
  const closeBtn = document.getElementById("close-customer-note-btn");
  
  if (closeXBtn) {
    closeXBtn.addEventListener("click", closeCustomerNoteModal);
  }
  
  if (closeBtn) {
    closeBtn.addEventListener("click", closeCustomerNoteModal);
  }
  
  // Close on background click
  const modal = document.getElementById("customerNoteModal");
  if (modal) {
    modal.addEventListener("click", function(e) {
      if (e.target === modal) {
        closeCustomerNoteModal();
      }
    });
  }
  
  // Close on Escape key
  document.addEventListener("keydown", function(e) {
    if (e.key === "Escape") {
      closeCustomerNoteModal();
    }
  });
}

// Initialize customer note modal on DOMContentLoaded
if (document.readyState === 'loading') {
  document.addEventListener("DOMContentLoaded", initCustomerNoteModal);
} else {
  initCustomerNoteModal();
}

// ---- Admin Authentication Logic ----
var adminMenu = document.getElementById("adminMenu");
var adminToggle = document.getElementById("adminToggle");
var adminDropdown = document.querySelector(".admin-dropdown");
var logoutBtn = document.getElementById("logoutBtn");

function setAuthenticatedState(isAuthenticated) {
  if (isAuthenticated) {
    if (adminMenu) adminMenu.classList.remove("hidden");
  } else {
    if (adminMenu) adminMenu.classList.add("hidden");
  }
}

// Check auth state on load
fetch("/api/v1/auth/check", { method: "GET" })
  .then(function (res) {
    if (res.ok) {
      setAuthenticatedState(true);
    } else {
      // Redirect to home if not authenticated
      window.location.href = "/";
    }
  })
  .catch(function () {
    // Redirect to home on error
    window.location.href = "/";
  });

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

// Close dropdown if clicked outside
document.addEventListener("click", function (e) {
  if (adminMenu && !adminMenu.contains(e.target) && !adminDropdown.classList.contains("hidden")) {
    adminDropdown.classList.add("hidden");
  }
});

// Prevent clicks inside dropdown from bubbling to document
if (adminDropdown) {
  adminDropdown.addEventListener("click", function (e) {
    e.stopPropagation();
  });
}

if (logoutBtn) {
  logoutBtn.addEventListener("click", function () {
    fetch("/api/v1/auth/logout", { method: "POST" })
      .then(function () {
        setAuthenticatedState(false);
        adminDropdown.classList.add("hidden");
        window.location.href = "/";
      });
  });
}

/* ==========================================================================
 * ORDER ACTION FUNCTIONS
 * ======================================================================== */

let currentConflictOrderId = null;

async function acceptOrder(orderId) {
  try {
    const res = await fetch(`${API_BASE}/orders/${orderId}/accept`, {
      method: "POST"
    });

    if (res.status === 409) {
      showConflictModal(orderId, "accept");
      return;
    }

    if (!res.ok) {
      const errorText = await res.text();
      alert("هەڵە: " + errorText);
      return;
    }

    // Refresh orders on success (maintain current page)
    const ordersPage = await fetchOrders(currentPage, pageSize);
    renderOrders(ordersPage.content);
    renderPagination(ordersPage);
  } catch (err) {
    console.error("Error accepting order:", err);
    alert("هەڵەیەک ڕوویدا لە کاتی وەرگرتنی ئۆردەرەکە");
  }
}

async function rejectOrder(orderId) {
  try {
    // Show rejection modal with callback to refresh orders on success
    showRejectionModal(orderId, async () => {
      // Refresh orders on successful rejection (maintain current page)
      const ordersPage = await fetchOrders(currentPage, pageSize);
      renderOrders(ordersPage.content);
      renderPagination(ordersPage);
    });
  } catch (err) {
    console.error("Error rejecting order:", err);
    alert("هەڵەیەک ڕوویدا لە کاتی ڕەتکردنەوەی ئۆردەرەکە");
  }
}

async function updatePrice(orderId) {
  const newPrice = prompt("تکایە نرخی نوێ بنووسە (بە دینار):");
  if (newPrice === null || newPrice === "") {
    return;
  }

  const priceNum = parseFloat(newPrice);
  if (isNaN(priceNum) || priceNum <= 0) {
    alert("تکایە نرخێکی دروست بنووسە");
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/orders/${orderId}/price?price=${priceNum}`, {
      method: "POST"
    });

    if (res.status === 409) {
      showConflictModal(orderId, "price", priceNum);
      return;
    }

    if (!res.ok) {
      const errorText = await res.text();
      alert("هەڵە: " + errorText);
      return;
    }

    // Refresh orders on success (maintain current page)
    const ordersPage = await fetchOrders(currentPage, pageSize);
    renderOrders(ordersPage.content);
    renderPagination(ordersPage);
  } catch (err) {
    console.error("Error updating price:", err);
    alert("هەڵەیەک ڕوویدا لە کاتی گۆڕینی نرخ");
  }
}

async function revertOrder(orderId) {
  if (!confirm("ئایا دڵنیایت کە دەتەوێت ئەم داواکارییە بگەڕێنیتەوە بۆ دۆخی PENDING؟")) {
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/orders/${orderId}/revert`, {
      method: 'POST'
    });

    if (res.status === 409) {
      showConflictModal(orderId, "revert");
      return;
    }

    if (!res.ok) {
      const errorText = await res.text();
      alert("هەڵە: " + errorText);
      return;
    }

    // Refresh orders on success (maintain current page)
    const ordersPage = await fetchOrders(currentPage, pageSize);
    renderOrders(ordersPage.content);
    renderPagination(ordersPage);
  } catch (err) {
    console.error('Error reverting order:', err);
    alert('هەڵە لە گەڕاندنەوەی داواکارییەکە: ' + err.message);
  }
}

/* ==========================================================================
 * CONFLICT MODAL HANDLING
 * ======================================================================== */

function showConflictModal(orderId, actionType, priceValue = null) {
  currentConflictOrderId = orderId;
  const modal = document.getElementById("conflictModal");
  const actionsContainer = document.getElementById("conflictModalActions");

  // Clear previous buttons
  actionsContainer.innerHTML = "";

  // Add retry buttons
  const acceptBtn = document.createElement("button");
  acceptBtn.className = "glass-btn glass-btn-accept";
  acceptBtn.textContent = "قبوڵکردن";
  acceptBtn.onclick = function () {
    hideConflictModal();
    acceptOrder(orderId);
  };

  const rejectBtn = document.createElement("button");
  rejectBtn.className = "glass-btn glass-btn-reject";
  rejectBtn.textContent = "ڕەتکردنەوە";
  rejectBtn.onclick = function () {
    hideConflictModal();
    rejectOrder(orderId);
  };

  const priceBtn = document.createElement("button");
  priceBtn.className = "glass-btn glass-btn-price";
  priceBtn.textContent = "گۆڕینی نرخ";
  priceBtn.onclick = function () {
    hideConflictModal();
    updatePrice(orderId);
  };

  actionsContainer.appendChild(acceptBtn);
  actionsContainer.appendChild(rejectBtn);
  actionsContainer.appendChild(priceBtn);

  // Show modal
  modal.classList.add("active");
}

function hideConflictModal() {
  const modal = document.getElementById("conflictModal");
  modal.classList.remove("active");
}

// Wire up modal close button
document.addEventListener("DOMContentLoaded", function () {
  const closeBtn = document.getElementById("conflictModalClose");
  if (closeBtn) {
    closeBtn.addEventListener("click", hideConflictModal);
  }

  // Close modal on backdrop click
  const modal = document.getElementById("conflictModal");
  if (modal) {
    modal.addEventListener("click", function (e) {
      if (e.target === modal) {
        hideConflictModal();
      }
    });
  }
  
  // Wire up pagination buttons
  const prevButton = document.getElementById("prev-page");
  const nextButton = document.getElementById("next-page");
  
  if (prevButton) {
    prevButton.addEventListener("click", async function () {
      if (currentPage > 0) {
        currentPage--;
        const ordersPage = await fetchOrders(currentPage, pageSize);
        renderOrders(ordersPage.content);
        renderPagination(ordersPage);
      }
    });
  }
  
  if (nextButton) {
    nextButton.addEventListener("click", async function () {
      if (currentPage < totalPages - 1) {
        currentPage++;
        const ordersPage = await fetchOrders(currentPage, pageSize);
        renderOrders(ordersPage.content);
        renderPagination(ordersPage);
      }
    });
  }
});

