// Global state for POS cart
let posCart = [];

// Global state for POS history pagination
let posHistoryState = {
  currentPage: 0,
  totalPages: 0,
  totalElements: 0,
  loading: false
};

/**
 * Dynamic Price Calculation: P(q) = (B / S) * q
 * Mathematical determinism for Zero Trust Architecture
 * 
 * @param {Object} serviceItem - Service object from DOM dataset
 * @param {number} userQuantity - Quantity requested by user
 * @returns {number} Calculated price
 */
function calculateDynamicPrice(serviceItem, userQuantity) {
    const basePrice = parseFloat(serviceItem.activePrice);
    if (isNaN(basePrice)) {
        console.error("Critical: Invalid base price from API.");
        return 0;
    }

    if (serviceItem.measurementUnit === 'PER_PERSON') {
        // Fallback to 10 ONLY if backend omits it, to prevent division by zero or NaN
        const setSize = (serviceItem.sofaStandardSetSize && serviceItem.sofaStandardSetSize > 0) 
                        ? parseInt(serviceItem.sofaStandardSetSize) 
                        : 10; 
        const pricePerUnit = basePrice / setSize;
        return pricePerUnit * userQuantity;
    }
    
    // Default calculation for PER_PIECE, PER_METER, etc.
    return basePrice * userQuantity;
}

// Kurdish unit label mappings for modal input prompts
const UNIT_LABELS = {
  'PER_METER': 'چەند مەتر؟',
  'PER_SQUARE_METER': 'چەند مەتر؟',
  'PER_PIECE': 'چەند دانە؟',
  'COUNT': 'چەند دانە؟',
  'PER_PERSON': 'چەند نەفەر؟',
  'HOURLY': 'چەند کاتژمێر؟',
  'PER_KILOGRAM': 'چەند کیلۆ؟',
  'PER_LITER': 'چەند لتر؟',
  'JOB': 'چەند کار؟'
};

// Kurdish unit placeholder mappings for modal input examples
const UNIT_PLACEHOLDERS = {
  'PER_METER': 'بۆ نموونە: 3.5 مەتر',
  'PER_SQUARE_METER': 'بۆ نموونە: 3.5 مەتر',
  'PER_PIECE': 'بۆ نموونە: 2 دانە',
  'COUNT': 'بۆ نموونە: 2 دانە',
  'PER_PERSON': 'بۆ نموونە: 2 نەفەر',
  'HOURLY': 'بۆ نموونە: 2 کاتژمێر',
  'PER_KILOGRAM': 'بۆ نموونە: 2 کیلۆ',
  'PER_LITER': 'بۆ نموونە: 2 لتر',
  'JOB': 'بۆ نموونە: 1 کار'
};

// Unit type configurations for input validation (Zero Trust: frontend precision control)
const UNIT_CONFIG = {
  // Integer-only units (you can't have 1.5 pieces)
  integer: ['PER_PIECE', 'PER_PERSON', 'COUNT', 'JOB'],
  // Floating-point units (you can have 3.5 meters)
  decimal: ['PER_METER', 'PER_SQUARE_METER', 'PER_KILOGRAM', 'PER_LITER', 'HOURLY']
};

// DOM Elements
const serviceModal = document.getElementById('service-modal');
const serviceForm = document.getElementById('service-form');
const modalCloseBtn = document.getElementById('modal-close');
const modalCancelBtn = document.getElementById('modal-cancel');
const modalServiceName = document.getElementById('modal-service-name');
const modalServiceId = document.getElementById('modal-service-id');
const modalServiceNameHidden = document.getElementById('modal-service-name-hidden');
const modalBasePrice = document.getElementById('modal-base-price');
const modalDefaultUnit = document.getElementById('modal-default-unit');
const modalQuantity = document.getElementById('modal-quantity');
const modalQuantityLabel = document.getElementById('modal-quantity-label');
const modalUnitLabel = document.getElementById('modal-unit-label');
const modalNegotiatedPriceField = document.querySelector('.pos-negotiated-price-field');
const modalNegotiatedPrice = document.getElementById('modal-negotiated-price');

// Cart DOM Elements
const cartItemsContainer = document.getElementById('cart-items');
const cartItemInputsContainer = document.getElementById('cart-item-inputs');
const grandTotalElement = document.getElementById('grand-total');
const executeOrderBtn = document.getElementById('execute-order-btn');

// Customer Input DOM Elements (now in left panel)
const posCustomerNameInput = document.getElementById('pos-customer-name');
const posCustomerPhoneInput = document.getElementById('pos-customer-phone');
const posCustomerAddressInput = document.getElementById('pos-customer-address');
const posCustomerNotesInput = document.getElementById('pos-customer-notes');

// Custom Price Modal DOM Elements
const customPriceModal = document.getElementById('custom-price-modal');
const customPriceInput = document.getElementById('custom-price-input');
const customPriceModalClose = document.getElementById('custom-price-modal-close');
const customPriceCancel = document.getElementById('custom-price-cancel');
const customPriceConfirm = document.getElementById('custom-price-confirm');

// Store the currently selected card for custom price modal
let selectedCardForCustomPrice = null;

// Unit type labels mapping (for display in cart)
const unitLabels = {
  'PER_METER': 'مەتر',
  'PER_SQUARE_METER': 'مەتری دووجا',
  'PER_PIECE': 'دانە',
  'PER_PERSON': 'نەفەر',
  'COUNT': 'دانە',
  'HOURLY': 'کاتژمێر',
  'PER_KILOGRAM': 'کیلۆگرام',
  'PER_LITER': 'لتر',
  'JOB': 'کار'
};

// ── DYNAMIC SERVICE FETCHING & RENDERING ───────────────────────────────────────
async function fetchAndRenderServices() {
  const servicesGrid = document.getElementById('services-grid');

  try {
    // Fetch from unified Active Services API (Single Source of Truth)
    const response = await fetch('/api/v1/services/active', {
      credentials: 'include' // Include HttpOnly JWT cookie for authentication
    });
    if (!response.ok) {
      throw new Error('Failed to fetch services: ' + response.status);
    }

    const services = await response.json();

    // Validate id exists for all services (Service.id as single source of truth)
    const invalidServices = services.filter(s => !s.id);
    if (invalidServices.length > 0) {
      console.error('Critical: Services missing id:', invalidServices);
      AppDialog.alert({ title: 'هەڵە', message: 'Error: Some services have invalid configuration. Please refresh.', type: 'error' });
      return;
    }

    // Clear loading state
    servicesGrid.textContent = '';

    // Dynamically generate service cards using safe DOM manipulation
    services.forEach((service, index) => {
      const card = createPosServiceCard(service, index);
      servicesGrid.appendChild(card);
    });

    console.log('Services loaded and rendered:', services.length);

  } catch (error) {
    console.error('Error fetching services:', error);
    servicesGrid.textContent = '';
    
    const errorDiv = document.createElement('div');
    errorDiv.className = 'pos-error-state';
    
    const errorIcon = document.createElement('i');
    errorIcon.className = 'fa-solid fa-triangle-exclamation';
    
    const errorP1 = document.createElement('p');
    errorP1.textContent = 'هەڵە لە بارکردنی خزمەتگوزارییەکان';
    
    const errorP2 = document.createElement('p');
    errorP2.className = 'error-detail';
    errorP2.textContent = error.message;
    
    errorDiv.appendChild(errorIcon);
    errorDiv.appendChild(errorP1);
    errorDiv.appendChild(errorP2);
    servicesGrid.appendChild(errorDiv);
  }
}

/**
 * Create a single POS service card element safely
 */
function createPosServiceCard(service, index) {
  const card = document.createElement('div');
  card.className = 'pos-service-card';
  card.dataset.serviceId = service.id;
  card.dataset.serviceName = service.kurdishName;
  card.dataset.englishName = service.englishName;
  card.dataset.measurementUnit = service.measurementUnit;
  card.dataset.activePrice = service.activePrice;
  card.dataset.discountedPrice = service.discountedPrice || '';
  card.dataset.discountActive = service.discountActive;
  card.dataset.customPriced = service.customPriced;
  card.dataset.sofaStandardSetSize = service.sofaStandardSetSize || 1;
  card.dataset.coreServiceId = service.id;  // Service.id is now the single source of truth
  card.dataset.shortcut = (index + 1).toString();  // Keyboard shortcut: Alt+1, Alt+2, etc.

  // Icon
  const iconDiv = document.createElement('div');
  iconDiv.className = 'pos-service-icon';
  const iconElement = document.createElement('i');
  iconElement.className = `fa-solid ${service.iconUrl || 'fa-circle'}`;
  iconDiv.appendChild(iconElement);

  // Service info
  const infoDiv = document.createElement('div');
  infoDiv.className = 'pos-service-info';
  
  const nameElement = document.createElement('h3');
  nameElement.className = 'pos-service-name';
  nameElement.innerHTML = `${service.kurdishName} <kbd>Alt+${index + 1}</kbd>`;
  
  const priceElement = document.createElement('p');
  priceElement.className = 'pos-service-price';
  
  // Price display logic
  if (service.customPriced) {
    const priceSpan = document.createElement('span');
    priceSpan.className = 'price-text custom-price-label';
    priceSpan.textContent = 'نرخی دیاری نەکراوە';
    priceElement.appendChild(priceSpan);
  } else if (service.activePrice && service.activePrice > 0) {
    const unitLabel = unitLabels[service.measurementUnit] || service.measurementUnit;
    
    if (service.discountActive && service.discountedPrice && service.discountedPrice < service.activePrice) {
      // Discounted price display
      const formattedBasePrice = service.activePrice.toLocaleString();
      const formattedDiscountPrice = service.discountedPrice.toLocaleString();
      
      const del = document.createElement('del');
      del.className = 'pos-price-strikethrough';
      del.textContent = formattedBasePrice + ' IQD/' + unitLabel;
      
      const discountSpan = document.createElement('span');
      discountSpan.className = 'pos-price-discounted';
      discountSpan.textContent = formattedDiscountPrice + ' IQD/' + unitLabel;
      
      priceElement.appendChild(del);
      priceElement.appendChild(discountSpan);
    } else {
      // Regular price display
      const formattedPrice = service.activePrice.toLocaleString();
      const priceSpan = document.createElement('span');
      priceSpan.className = 'price-text';
      priceSpan.textContent = formattedPrice + ' IQD / ' + unitLabel;
      priceElement.appendChild(priceSpan);
    }
  } else {
    const priceSpan = document.createElement('span');
    priceSpan.className = 'price-text';
    priceSpan.textContent = 'نرخەکەی دیاری دەکرێت';
    priceElement.appendChild(priceSpan);
  }

  infoDiv.appendChild(nameElement);
  infoDiv.appendChild(priceElement);

  card.appendChild(iconDiv);
  card.appendChild(infoDiv);

  return card;
}

// ── EVENT DELEGATION FOR DYNAMIC SERVICE CARDS ───────────────────────────────
// Attach click listener to parent container (delegation pattern)
document.getElementById('services-grid').addEventListener('click', (e) => {
  // Find the closest service card ancestor
  const card = e.target.closest('.pos-service-card');
  if (card) {
    // Check if this is a custom-priced service
    const isCustomPriced = card.dataset.isCustomPriced === 'true';
    
    if (isCustomPriced) {
      // Intercept: Show custom price modal instead of prompt
      selectedCardForCustomPrice = card;
      customPriceInput.value = ''; // Clear previous input
      customPriceModal.classList.add('active');
      customPriceInput.focus();
    } else {
      // Normal flow - open service modal
      openServiceModal(card);
    }
  }
});

// Custom Price Modal Event Listeners
customPriceModalClose.addEventListener('click', closeCustomPriceModal);
customPriceCancel.addEventListener('click', closeCustomPriceModal);

customPriceConfirm.addEventListener('click', () => {
  const customPrice = customPriceInput.value;

  if (customPrice && customPrice !== '') {
    // Use strict validation to reject partially numeric strings
    const priceValid = typeof isValidNumericString === 'function'
      ? isValidNumericString(customPrice, false)
      : !isNaN(parseFloat(customPrice)) && parseFloat(customPrice) > 0;

    if (priceValid) {
      const priceValue = parseFloat(customPrice);
      // Valid price - add directly to cart with custom price
      addCustomPricedServiceToCart(selectedCardForCustomPrice, priceValue);
      closeCustomPriceModal();
    } else {
      AppDialog.alert({ title: 'ئاگاداری', message: 'تکایە نرخێکی درووست بنووسە', type: 'warning' });
    }
  } else {
    AppDialog.alert({ title: 'ئاگاداری', message: 'تکایە نرخێک بنووسە', type: 'warning' });
  }
});

// Close custom price modal when clicking backdrop
customPriceModal.addEventListener('click', (e) => {
  if (e.target === customPriceModal || e.target.classList.contains('pos-modal-backdrop')) {
    closeCustomPriceModal();
  }
});

function closeCustomPriceModal() {
  customPriceModal.classList.remove('active');
  customPriceInput.value = '';
  selectedCardForCustomPrice = null;
}

// Add custom-priced service directly to cart (bypasses modal)
function addCustomPricedServiceToCart(card, customPrice) {
  const serviceId = parseInt(card.dataset.serviceId);
  const coreServiceId = parseInt(card.dataset.coreServiceId);  // No fallback - must exist
  const serviceName = card.dataset.serviceName;
  const measurementUnit = card.dataset.measurementUnit ? card.dataset.measurementUnit.toUpperCase() : 'PER_PIECE';
  
  // Custom-priced services are always quantity = 1 (per job)
  const quantity = 1;
  const unitPrice = customPrice;
  const totalPrice = customPrice;
  
  // Add to cart (Zero Trust: frontend calculates for UI display only, backend will recalculate)
  const newItem = {
    rowId: Date.now(),
    serviceId: serviceId,
    coreServiceId: coreServiceId,
    serviceName: serviceName,
    quantity: quantity,
    unitName: measurementUnit,
    unitPrice: unitPrice,
    totalPrice: totalPrice
  };
  
  posCart.unshift(newItem);  // Add to top of cart (newest first)
  renderCart();
  renderDynamicInputs();
  validateCartForSubmission();
}

// ── INITIALIZE: Fetch services on DOMContentLoaded ─────────────────────────────
document.addEventListener('DOMContentLoaded', fetchAndRenderServices);

// Execute Order button - submits order directly
executeOrderBtn.addEventListener('click', submitOrder);

// ── BACKEND INTEGRATION: Submit Order Function ───────────────────────────────
async function submitOrder() {
  // Validate cart is not empty
  if (posCart.length === 0) {
    AppDialog.alert({ title: 'ئاگاداری', message: 'تکایە سەرەتا خزمەتگوزارییەک زیاد بکە بۆ لیستەکە.', type: 'warning' });
    return;
  }

  // Validate customer inputs
  const customerName = posCustomerNameInput.value.trim();
  const customerPhone = posCustomerPhoneInput.value.trim();
  const customerAddress = posCustomerAddressInput.value.trim();
  const customerNotes = posCustomerNotesInput.value.trim();

  if (!customerName || !customerPhone) {
    AppDialog.alert({ title: 'ئاگاداری', message: 'تکایە ناوی کڕیار و ژمارەی تەلەفۆن بنووسە.', type: 'warning' });
    posCustomerNameInput.focus();
    return;
  }

  // HttpOnly cookie is used for authentication - no localStorage token needed
  // Backend validates auth via HttpOnly JWT cookie automatically
  const createdBy = 'pos_operator'; // Default operator for POS orders

  // Construct OrderRequestDTO payload (Zero Trust Architecture)
  // CRITICAL FIX: Include idempotencyKey in request body for duplicate prevention
  // CRITICAL: Send coreServiceId (actual Service entity ID) and quantity - backend MUST recalculate prices
  // Frontend unitPrice and totalPrice are for UI display only, not trusted by backend
  const orderRequest = {
    customerName: customerName,
    phoneNumber: customerPhone,
    address: customerAddress || null,
    notes: customerNotes || null,
    createdBy: createdBy,
    idempotencyKey: getIdempotencyKey('pos_idempotency_key'),  // Use global utility with storage key
    items: posCart.map(item => {
      if (!item.coreServiceId) {
        console.error('Critical: coreServiceId is missing for item:', item);
        throw new Error('Service ID missing - cannot submit order');
      }
      return {
        serviceId: item.coreServiceId,  // ALWAYS use coreServiceId
        quantity: item.quantity,
        unitName: item.unitName ? item.unitName.toUpperCase() : 'PER_PIECE',
        unitPrice: item.unitPrice,
        totalPrice: item.totalPrice
      };
    })
  };

  // Loading State: Disable button and change text
  const originalChildren = Array.from(executeOrderBtn.childNodes);
  executeOrderBtn.disabled = true;
  executeOrderBtn.textContent = '';
  
  const spinnerIcon = document.createElement('i');
  spinnerIcon.className = 'fa-solid fa-spinner fa-spin';
  
  const loadingText = document.createTextNode(' Processing...');
  
  executeOrderBtn.appendChild(spinnerIcon);
  executeOrderBtn.appendChild(loadingText);

  try {
    // Send POST request to backend
    // CRITICAL FIX: Idempotency key is now in request body, not header
    const response = await fetch('/api/v1/orders', {
      method: 'POST',
      credentials: 'include', // Include HttpOnly JWT cookie for authentication
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(orderRequest)
    });

    if (response.ok) {
      // Success - 201 Created
      const savedOrder = await response.json();

      // Store cart items for receipt before clearing
      const cartItemsForReceipt = [...posCart];

      // Clear idempotency key after successful order
      clearIdempotencyKey('pos_idempotency_key');

      // Clear cart state
      posCart = [];

      // Clear customer inputs
      posCustomerNameInput.value = '';
      posCustomerPhoneInput.value = '';
      posCustomerAddressInput.value = '';
      posCustomerNotesInput.value = '';

      // Re-render cart UI (state-driven)
      renderCart();

      // Generate and show receipt using local cart items
      generateReceipt(savedOrder, cartItemsForReceipt, customerName, customerPhone, customerAddress, customerNotes);

    } else if (response.status === 409) {
      // Conflict - Optimistic locking error
      // Show conflict modal with retry option
      showPosConflictModal(submitOrder);
      return;
    } else if (response.status === 400) {
      // Bad Request - validation error
      const errorData = await response.json().catch(() => ({}));
      AppDialog.alert({ title: 'هەڵە', message: 'Validation Error: ' + (errorData.message || 'Please check your input and try again.'), type: 'error' });
    } else if (response.status === 500) {
      // Internal Server Error
      AppDialog.alert({ title: 'هەڵە', message: 'Server Error: An unexpected error occurred. Please try again later.', type: 'error' });
    } else {
      // Other errors
      AppDialog.alert({ title: 'هەڵە', message: 'Error: Failed to create order. Status: ' + response.status, type: 'error' });
    }

  } catch (error) {
    // Network error or fetch failure
    console.error('Error creating order:', error);
    AppDialog.alert({ title: 'هەڵە', message: 'Network Error: Unable to connect to the server. Please check your connection and try again.', type: 'error' });
  } finally {
    // Re-enable button and restore original text
    executeOrderBtn.disabled = false;
    executeOrderBtn.textContent = '';
    originalChildren.forEach(child => executeOrderBtn.appendChild(child));
  }
}

// History Modal Functions
async function showHistoryModal() {
  const historyModal = document.getElementById('history-modal');
  const historyLoading = document.getElementById('history-loading');
  const historyList = document.getElementById('history-list');
  
  if (!historyModal) return;
  
  // Show loading state
  historyLoading.classList.remove('hidden');
  historyList.textContent = '';
  historyModal.classList.add('active');
  
  try {
    const response = await fetch(`/api/v1/orders/pos-orders?page=${posHistoryState.currentPage}&size=10`, {
      credentials: 'include' // Include HttpOnly JWT cookie for authentication
    });
    
    if (!response.ok) {
      throw new Error(`Failed to fetch history: ${response.status}`);
    }
    
    const pageData = await response.json();
    const orders = pageData.content || [];
    
    // Update pagination state
    posHistoryState.currentPage = pageData.number || 0;
    posHistoryState.totalPages = pageData.totalPages || 0;
    posHistoryState.totalElements = pageData.totalElements || 0;
    
    // Hide loading
    historyLoading.classList.add('hidden');
    
    if (orders.length === 0) {
      const emptyP = document.createElement('p');
      emptyP.className = 'text-muted';
      emptyP.textContent = 'هیچ مامەڵەیەک نییە لە مێژوودا';
      historyList.appendChild(emptyP);
      renderPaginationControls();
      return;
    }
    
    // Render history items
    orders.forEach(order => {
      const historyItem = document.createElement('div');
      historyItem.className = 'history-item';
      
      const date = new Date(order.createdAt).toLocaleDateString('en-GB', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      });
      
      const headerDiv = document.createElement('div');
      headerDiv.className = 'history-item-header';
      
      const idSpan = document.createElement('span');
      idSpan.className = 'history-item-id';
      idSpan.textContent = '#' + order.id;
      
      const dateSpan = document.createElement('span');
      dateSpan.className = 'history-item-date';
      dateSpan.textContent = date;
      
      headerDiv.appendChild(idSpan);
      headerDiv.appendChild(dateSpan);
      
      const customerDiv = document.createElement('div');
      customerDiv.className = 'history-item-customer';
      
      const userIcon = document.createElement('i');
      userIcon.className = 'fa-solid fa-user';
      
      const customerSpan = document.createElement('span');
      customerSpan.textContent = order.customerName;
      
      customerDiv.appendChild(userIcon);
      customerDiv.appendChild(customerSpan);
      
      const totalDiv = document.createElement('div');
      totalDiv.className = 'history-item-total';
      totalDiv.textContent = (order.grandTotal ? order.grandTotal.toFixed(2) : '0.00') + ' IQD';
      
      const actionsDiv = document.createElement('div');
      actionsDiv.className = 'history-item-actions';
      
      const reprintBtn = document.createElement('button');
      reprintBtn.className = 'history-item-btn reprint';
      reprintBtn.onclick = function() { reprintOrder(order); };
      
      const reprintIcon = document.createElement('i');
      reprintIcon.className = 'fa-solid fa-print';
      
      const reprintText = document.createTextNode(' چاپکردنەوە');
      
      reprintBtn.appendChild(reprintIcon);
      reprintBtn.appendChild(reprintText);
      
      const rejectBtn = document.createElement('button');
      rejectBtn.className = 'history-item-btn reject';
      rejectBtn.onclick = function() { handleRejectFromHistory(order.id); };
      
      const rejectIcon = document.createElement('i');
      rejectIcon.className = 'fa-solid fa-xmark';
      
      const rejectText = document.createTextNode(' ڕەتکردنەوە');
      
      rejectBtn.appendChild(rejectIcon);
      rejectBtn.appendChild(rejectText);
      
      actionsDiv.appendChild(reprintBtn);
      actionsDiv.appendChild(rejectBtn);
      
      historyItem.appendChild(headerDiv);
      historyItem.appendChild(customerDiv);
      historyItem.appendChild(totalDiv);
      historyItem.appendChild(actionsDiv);
      
      historyList.appendChild(historyItem);
    });
    
    // Render pagination controls
    renderPaginationControls();
    
  } catch (error) {
    console.error('Error fetching history:', error);
    historyLoading.classList.add('hidden');
    historyList.textContent = '';
    const errorP = document.createElement('p');
    errorP.className = 'text-muted';
    errorP.textContent = 'هەڵە لە بارکردنی مێژوو';
    historyList.appendChild(errorP);
  }
}

// Render pagination controls for history modal
function renderPaginationControls() {
  const historyList = document.getElementById('history-list');
  
  // Remove existing pagination controls
  const existingControls = historyList.querySelector('.history-pagination');
  if (existingControls) {
    existingControls.remove();
  }
  
  // Don't render if no pages
  if (posHistoryState.totalPages <= 1) {
    return;
  }
  
  // Create pagination container
  const paginationDiv = document.createElement('div');
  paginationDiv.className = 'history-pagination';
  
  // Prev button
  const prevBtn = document.createElement('button');
  prevBtn.className = 'pagination-btn';
  prevBtn.textContent = 'پێشوو';
  prevBtn.disabled = posHistoryState.currentPage === 0;
  prevBtn.onclick = () => {
    if (posHistoryState.currentPage > 0) {
      posHistoryState.currentPage--;
      showHistoryModal();
    }
  };
  
  // Page info
  const pageInfo = document.createElement('span');
  pageInfo.className = 'pagination-info';
  pageInfo.textContent = `پەڕە ${posHistoryState.currentPage + 1} لە ${posHistoryState.totalPages}`;
  
  // Next button
  const nextBtn = document.createElement('button');
  nextBtn.className = 'pagination-btn';
  nextBtn.textContent = 'دواتر';
  nextBtn.disabled = posHistoryState.currentPage >= posHistoryState.totalPages - 1;
  nextBtn.onclick = () => {
    if (posHistoryState.currentPage < posHistoryState.totalPages - 1) {
      posHistoryState.currentPage++;
      showHistoryModal();
    }
  };
  
  paginationDiv.appendChild(prevBtn);
  paginationDiv.appendChild(pageInfo);
  paginationDiv.appendChild(nextBtn);
  
  historyList.appendChild(paginationDiv);
}

function closeHistoryModal() {
  const historyModal = document.getElementById('history-modal');
  if (historyModal) {
    historyModal.classList.remove('active');
  }
}

// Handle reject button click from history modal
// Closes history modal first, then opens rejection modal to avoid z-index stacking issues
function handleRejectFromHistory(orderId) {
  closeHistoryModal();
  
  // Small delay to allow history modal to close smoothly
  setTimeout(() => {
    showRejectionModal(orderId, () => {
      // After successful rejection, refresh history modal (reset to page 0)
      posHistoryState.currentPage = 0;
      showHistoryModal();
    });
  }, 150);
}

// Add event listener for history button
document.addEventListener('DOMContentLoaded', () => {
  const historyBtn = document.getElementById('btn-show-history');
  if (historyBtn) {
    historyBtn.addEventListener('click', showHistoryModal);
  }
});

// ── RECEIPT PRINTER (animated, Replay + Print Copy) ──────────────────────────

// Kurdish labels + shop identity shared by the sale receipt and reprints.
function buildReceiptOptions(data) {
  return Object.assign({
    merchant: 'خزمەتگوزاری غەسلی لایف',
    merchantSubtext: 'پسولەی داواکاری',
    currency: 'IQD',
    statusTitle: 'پارەدان تەواو بوو',
    statusSubtitle: 'پسولە دەرچوو',
    message: 'سوپاس بۆ هەڵبژاردنی ئێمە\n3674 970 0771\n4248 146 0770',
    labels: {
      statusIdle: 'چاوەڕوانی',
      statusPrinting: 'لە چاپکردندایە...',
      statusCompleted: 'تەواو بوو',
      orderLabel: 'ژمارەی داواکاری',
      dateLabel: 'بەروار',
      paymentLabel: 'شێوازی پارەدان',
      customerLabel: 'کڕیار',
      phoneLabel: 'تەلەفۆن',
      addressLabel: 'ناونیشان',
      notesLabel: 'تێبینی',
      subtotalLabel: 'کۆی خزمەتگوزارییەکان',
      taxLabel: 'باج',
      discountLabel: 'داشکاندن',
      totalLabel: 'کۆی گشتی',
      replay: 'دووبارە چاپکردن',
      printCopy: 'چاپکردنی کۆپی',
      close: 'داخستن'
    }
  }, data || {});
}

// Map cart items (local or stored order items) into receipt line items.
function buildReceiptItems(items) {
  return (items || []).map(item => ({
    name: item.serviceName || item.name || '',
    description: unitLabels[item.unitName] || item.unitName || '',
    qty: Number(item.quantity != null ? item.quantity : item.qty) || 0,
    price: Number(item.unitPrice != null ? item.unitPrice : item.price) || 0,
    total: Number(item.totalPrice != null ? item.totalPrice : item.total) || 0
  }));
}

function generateReceipt(order, cartItems, customerName, customerPhone, customerAddress, customerNotes) {
  const items = buildReceiptItems(cartItems);
  const subtotal = items.reduce((sum, it) => sum + it.total, 0);
  const total = (order && order.grandTotal != null) ? Number(order.grandTotal) : subtotal;

  ReceiptPrinter.open(buildReceiptOptions({
    orderNumber: '#' + (order ? order.id : ''),
    date: new Date(),
    customer: {
      name: customerName || '',
      phone: customerPhone || '',
      address: customerAddress || '',
      notes: customerNotes || ''
    },
    items: items,
    subtotal: subtotal,
    total: total
  }));
}

// Reprint an existing order from POS history using stored order data.
function reprintOrder(order) {
  const items = buildReceiptItems((order.items || []).map(it => ({
    serviceName: (it.serviceCategory && (it.serviceCategory.kurdishName || it.serviceCategory.englishName)) || '',
    unitName: it.unitName,
    quantity: it.quantity,
    unitPrice: it.unitPrice,
    totalPrice: it.totalPrice
  })));
  const subtotal = items.reduce((sum, it) => sum + it.total, 0);
  const total = (order && order.grandTotal != null) ? Number(order.grandTotal) : subtotal;

  ReceiptPrinter.open(buildReceiptOptions({
    orderNumber: '#' + (order ? order.id : ''),
    date: (order && order.createdAt) ? new Date(order.createdAt) : new Date(),
    customer: {
      name: (order && order.customerName) || '',
      phone: (order && order.phoneNumber) || '',
      address: (order && order.address) || ''
    },
    items: items,
    subtotal: subtotal,
    total: total
  }));
}

// Open Service Selection Modal
function openServiceModal(card) {
  // Read data attributes from the clicked card (dynamic from API)
  const serviceId = card.dataset.serviceId;
  const coreServiceId = parseInt(card.dataset.coreServiceId);  // No fallback - must exist
  const serviceName = card.dataset.serviceName;
  const englishName = card.dataset.englishName;
  const measurementUnit = card.dataset.measurementUnit ? card.dataset.measurementUnit.toUpperCase() : 'PER_PIECE';
  const activePrice = card.dataset.activePrice;
  const customPriced = card.dataset.customPriced === 'true';

  // Set modal title
  modalServiceName.textContent = serviceName;

  // Set hidden form fields
  modalServiceId.value = serviceId;
  modalServiceId.dataset.coreServiceId = coreServiceId;
  modalServiceNameHidden.value = serviceName;
  modalBasePrice.value = activePrice;
  modalDefaultUnit.value = measurementUnit;

  // Configure quantity input based on MeasurementUnit (Dynamic Contextual Modal)
  configureQuantityInputForModal(measurementUnit);

  // CRITICAL CONSTRAINT: Check if service has fixed price or requires negotiated price
  if (customPriced || !activePrice || activePrice === '' || activePrice === '0' || activePrice === 'null') {
    // Service requires negotiated price - show negotiated price input
    modalNegotiatedPriceField.classList.remove('hidden');
    modalNegotiatedPrice.required = true;
    modalNegotiatedPrice.value = '';
  } else {
    // Service has fixed price - hide negotiated price input
    modalNegotiatedPriceField.classList.add('hidden');
    modalNegotiatedPrice.required = false;
    modalNegotiatedPrice.value = '';
  }

  // Reset quantity input
  modalQuantity.value = '';
  if (document.getElementById('modal-quantity-field').style.display !== 'none') {
    modalQuantity.focus();
  }

  // Show modal
  serviceModal.classList.add('active');
}

/**
 * Configure quantity input based on MeasurementUnit (Dynamic Contextual Modal)
 * Kurdish labels and precision control based on unit type
 */
function configureQuantityInputForModal(measurementUnit) {
  const quantityField = document.getElementById('modal-quantity-field');
  const quantityLabel = document.getElementById('modal-quantity-label');
  const quantityInput = document.getElementById('modal-quantity');
  const unitLabelSpan = document.getElementById('modal-unit-label');

  const isIntegerUnit = UNIT_CONFIG.integer.includes(measurementUnit);
  const kurdishLabel = UNIT_LABELS[measurementUnit] || measurementUnit;
  const displayUnitLabel = unitLabels[measurementUnit] || measurementUnit;
  const placeholderText = UNIT_PLACEHOLDERS[measurementUnit] || measurementUnit;

  if (isIntegerUnit) {
    // Integer-only units (you can't have 1.5 pieces)
    quantityInput.step = '1';
    quantityInput.min = '1';
    quantityLabel.textContent = kurdishLabel;
  } else {
    // Floating-point units (you can have 3.5 meters)
    // Special case: HOURLY uses step="0.5" for half-hour increments
    if (measurementUnit === 'HOURLY') {
      quantityInput.step = '0.5';
    } else {
      quantityInput.step = '0.1';
    }
    quantityInput.min = '0.1';
    quantityLabel.textContent = kurdishLabel;
  }

  // Set unit-specific placeholder
  quantityInput.setAttribute('placeholder', placeholderText);

  unitLabelSpan.textContent = displayUnitLabel;
  quantityField.style.display = '';
}

// Close Service Modal
function closeServiceModal() {
  serviceModal.classList.remove('active');
  serviceForm.reset();
}

// Modal close button
modalCloseBtn.addEventListener('click', closeServiceModal);

// Modal cancel button
modalCancelBtn.addEventListener('click', closeServiceModal);

// Close modal when clicking backdrop
serviceModal.addEventListener('click', (e) => {
  if (e.target === serviceModal || e.target.classList.contains('pos-modal-backdrop')) {
    closeServiceModal();
  }
});

// Handle Service Form Submission (named function for edit support)
serviceForm.addEventListener('submit', handleServiceFormSubmit);

// Render Cart Function (Granular DOM Updates - data-id reconciliation)
function renderCart() {
  // Get existing DOM elements with their data-row-id
  const existingItems = new Map();
  cartItemsContainer.querySelectorAll('[data-row-id]').forEach(el => {
    existingItems.set(parseInt(el.dataset.rowId), el);
  });

  // Get current state IDs
  const currentStateIds = new Set(posCart.map(item => item.rowId));

  // Remove items not in current state
  existingItems.forEach((el, rowId) => {
    if (!currentStateIds.has(rowId)) {
      el.remove();
    }
  });

  // Check if cart is empty
  if (posCart.length === 0) {
    cartItemsContainer.textContent = '';
    
    const emptyDiv = document.createElement('div');
    emptyDiv.className = 'pos-cart-empty';
    
    const emptyIcon = document.createElement('i');
    emptyIcon.className = 'fa-solid fa-basket-shopping';
    
    const emptyP = document.createElement('p');
    emptyP.textContent = 'هیچ کاڵاکێک لە لیستەکەدا نییە';
    
    emptyDiv.appendChild(emptyIcon);
    emptyDiv.appendChild(emptyP);
    cartItemsContainer.appendChild(emptyDiv);
    
    grandTotalElement.textContent = '0 IQD';
    executeOrderBtn.disabled = true;
    
    // Clear dynamic inputs when cart is empty
    const cartItemInputsContainer = document.getElementById('cart-item-inputs');
    if (cartItemInputsContainer) cartItemInputsContainer.innerHTML = '';
    
    return;
  }

  // Add or update items
  posCart.forEach(item => {
    let itemEl = existingItems.get(item.rowId);

    if (!itemEl) {
      // Create new element
      itemEl = createCartItemElement(item);
      cartItemsContainer.prepend(itemEl);  // Add to top (newest first)
    } else {
      // Update existing element (granular update)
      updateCartItemElement(itemEl, item);
    }
  });

  // Zero Trust: Calculate grand total for UI display only
  let grandTotal = 0;
  posCart.forEach(item => {
    grandTotal = Math.round((grandTotal + item.totalPrice) * 100) / 100;
  });

  // Update grand total
  grandTotalElement.textContent = grandTotal.toFixed(2) + ' IQD';
  
  // Validate cart state for submission
  validateCartForSubmission();
}

// Validate cart for submission (checks cart items and customer info)
function validateCartForSubmission() {
  const hasItems = posCart.length > 0;
  const hasCustomerName = posCustomerNameInput && posCustomerNameInput.value.trim() !== '';
  const hasCustomerPhone = posCustomerPhoneInput && posCustomerPhoneInput.value.trim() !== '';
  
  executeOrderBtn.disabled = !(hasItems && hasCustomerName && hasCustomerPhone);
}

// Create cart item element with data-row-id
function createCartItemElement(item) {
  const formattedQuantity = item.quantity % 1 === 0 ? item.quantity : item.quantity.toFixed(2);
  const formattedUnitPrice = item.unitPrice.toFixed(2);
  const formattedTotalPrice = item.totalPrice.toFixed(2);
  const unitLabel = unitLabels[item.unitName] || item.unitName;

  const div = document.createElement('div');
  div.className = 'pos-cart-item';
  div.setAttribute('data-row-id', item.rowId);

  // Info section
  const infoDiv = document.createElement('div');
  infoDiv.className = 'pos-cart-item-info';

  const nameDiv = document.createElement('div');
  nameDiv.className = 'pos-cart-item-name';
  nameDiv.textContent = item.serviceName;

  const detailsDiv = document.createElement('div');
  detailsDiv.className = 'pos-cart-item-details';
  detailsDiv.textContent = `${formattedQuantity} ${unitLabel} × ${formattedUnitPrice} IQD`;

  infoDiv.appendChild(nameDiv);
  infoDiv.appendChild(detailsDiv);

  // Price section
  const priceDiv = document.createElement('div');
  priceDiv.className = 'pos-cart-item-price';
  priceDiv.textContent = `${formattedTotalPrice} IQD`;

  // Actions section
  const actionsDiv = document.createElement('div');
  actionsDiv.className = 'pos-cart-item-actions';

  const editBtn = document.createElement('button');
  editBtn.className = 'pos-cart-item-btn edit';
  editBtn.title = 'دەستکاریکردن';
  editBtn.onclick = () => editItem(item.rowId);
  
  const editIcon = document.createElement('i');
  editIcon.className = 'fa-solid fa-pen';
  editBtn.appendChild(editIcon);

  const deleteBtn = document.createElement('button');
  deleteBtn.className = 'pos-cart-item-btn delete';
  deleteBtn.title = 'سڕینەوە';
  deleteBtn.onclick = () => removeFromCart(item.rowId);
  
  const deleteIcon = document.createElement('i');
  deleteIcon.className = 'fa-solid fa-trash';
  deleteBtn.appendChild(deleteIcon);

  actionsDiv.appendChild(editBtn);
  actionsDiv.appendChild(deleteBtn);

  div.appendChild(infoDiv);
  div.appendChild(priceDiv);
  div.appendChild(actionsDiv);

  return div;
}

// Update existing cart item element (granular update)
function updateCartItemElement(el, item) {
  const formattedQuantity = item.quantity % 1 === 0 ? item.quantity : item.quantity.toFixed(2);
  const formattedUnitPrice = item.unitPrice.toFixed(2);
  const formattedTotalPrice = item.totalPrice.toFixed(2);
  const unitLabel = unitLabels[item.unitName] || item.unitName;

  // Update text content safely
  el.querySelector('.pos-cart-item-name').textContent = item.serviceName;
  el.querySelector('.pos-cart-item-details').textContent = `${formattedQuantity} ${unitLabel} × ${formattedUnitPrice} IQD`;
  el.querySelector('.pos-cart-item-price').textContent = `${formattedTotalPrice} IQD`;
}

// Remove from Cart Function
function removeFromCart(rowId) {
  // Filter array to remove the specific item
  posCart = posCart.filter(item => item.rowId !== rowId);

  // Re-render cart
  renderCart();
  renderDynamicInputs();
  
  // Validate cart state after item removal
  validateCartForSubmission();
}

// Render dynamic quantity inputs for cart items (HCI focus management)
function renderDynamicInputs() {
  if (!cartItemInputsContainer) return;
  cartItemInputsContainer.innerHTML = '';
  
  if (posCart.length === 0) return;
  
  posCart.forEach((item, index) => {
    const inputField = document.createElement('div');
    inputField.className = 'pos-form-field';
    
    const label = document.createElement('label');
    label.textContent = `${item.serviceName} (${unitLabels[item.unitName] || item.unitName}):`;
    inputField.appendChild(label);
    
    const input = document.createElement('input');
    input.type = 'text';
    input.inputmode = 'decimal';
    input.dataset.rowId = item.rowId;
    input.placeholder = 'بڕ بنووسە';
    input.value = item.quantity % 1 === 0 ? item.quantity : item.quantity.toFixed(2);
    
    // Add input change listener
    input.addEventListener('input', (e) => {
      const value = e.target.value;
      if (value === '' || value === '.') {
        updateItemQuantity(item.rowId, null);
        return;
      }
      
      const cleanValue = value.replace(/[^0-9.]/g, '');
      if (cleanValue !== value) e.target.value = cleanValue;
      updateItemQuantity(item.rowId, cleanValue || null);
    });
    
    inputField.appendChild(input);
    cartItemInputsContainer.appendChild(inputField);
    
    // Focus on the last input after render (HCI optimization)
    if (index === posCart.length - 1) {
      setTimeout(() => {
        input.focus();
        input.select();
      }, 50);
    }
  });
}

// Update item quantity from inline input
function updateItemQuantity(rowId, quantity) {
  const item = posCart.find(i => i.rowId === rowId);
  if (item) {
    const newQuantity = quantity ? parseFloat(quantity) : null;
    
    if (newQuantity && newQuantity > 0) {
      // Recalculate price with new quantity
      const serviceItem = {
        activePrice: item.unitPrice * item.quantity, // Reverse to get base price
        measurementUnit: item.unitName,
        sofaStandardSetSize: null
      };
      
      // For PER_PERSON, we need the original calculation
      // Simplified: just recalculate based on unit price
      item.quantity = newQuantity;
      item.totalPrice = Math.round(item.unitPrice * newQuantity * 100) / 100;
    } else {
      item.quantity = null;
      item.totalPrice = 0;
    }
    
    renderCart();
    validateCartForSubmission();
  }
}

// Edit Item Function
function editItem(rowId) {
  // Find the item in the cart
  const item = posCart.find(item => item.rowId === rowId);

  if (!item) {
    console.error('Item not found with rowId:', rowId);
    return;
  }

  // Open the service modal with the item's data
  modalServiceName.textContent = item.serviceName;
  modalServiceId.value = item.serviceId;
  modalServiceNameHidden.value = item.serviceName;
  modalBasePrice.value = item.unitPrice; // Use current unit price (for UI display only)
  modalDefaultUnit.value = item.unitName;

  // Set unit label
  const unitLabel = unitLabels[item.unitName] || item.unitName;
  modalUnitLabel.textContent = unitLabel;

  // Get the service card to retrieve measurementUnit for dynamic configuration
  const serviceCard = document.querySelector(`.pos-service-card[data-service-id="${item.serviceId}"]`);
  const measurementUnit = serviceCard ? serviceCard.dataset.measurementUnit : item.unitName;
  const customPriced = serviceCard ? serviceCard.dataset.customPriced === 'true' : false;

  // CRITICAL: Apply dynamic quantity field configuration based on MeasurementUnit
  configureQuantityInputForModal(measurementUnit);

  // Check if this is a negotiated price service
  if (customPriced) {
    // Service requires negotiated price - show negotiated price input
    modalNegotiatedPriceField.classList.remove('hidden');
    modalNegotiatedPrice.required = true;
    modalNegotiatedPrice.value = item.unitPrice;
  } else {
    // Service has fixed price - hide negotiated price input
    modalNegotiatedPriceField.classList.add('hidden');
    modalNegotiatedPrice.required = false;
    modalNegotiatedPrice.value = '';
  }

  // Pre-fill quantity
  modalQuantity.value = item.quantity;

  // Store the rowId being edited (for form submission)
  modalServiceId.dataset.editingRowId = rowId;

  // Show modal
  serviceModal.classList.add('active');
}

// Handle Service Form Submission (supports both new items and edits)
function handleServiceFormSubmit(e) {
  e.preventDefault();

  // Get form values (already converted from Kurdish numerals by global-utils.js)
  const serviceId = parseInt(modalServiceId.value);
  const coreServiceId = parseInt(modalServiceId.dataset.coreServiceId);  // No fallback - must exist
  const serviceName = modalServiceNameHidden.value;
  const activePrice = modalBasePrice.value;
  const measurementUnit = modalDefaultUnit.value ? modalDefaultUnit.value.toUpperCase() : 'PER_PIECE';
  const quantityStr = modalQuantity.value;
  const negotiatedPriceStr = modalNegotiatedPrice.value;
  const sofaStandardSetSize = modalServiceId.dataset.sofaStandardSetSize ? parseInt(modalServiceId.dataset.sofaStandardSetSize) : null;
  const editingRowId = modalServiceId.dataset.editingRowId;

  // Validate numeric inputs with strict validation (after Kurdish numeral conversion)
  // Use isValidNumericString if available, otherwise fallback to parseFloat
  const quantityValid = typeof isValidNumericString === 'function'
    ? isValidNumericString(quantityStr, false)
    : !isNaN(parseFloat(quantityStr)) && parseFloat(quantityStr) > 0;

  if (!quantityValid) {
    AppDialog.alert({ title: 'ئاگاداری', message: 'تکایە بڕێکی دروست بنووسە', type: 'warning' });
    return;
  }

  const quantity = parseFloat(quantityStr);

  if (modalNegotiatedPrice.required) {
    const negotiatedPriceValid = typeof isValidNumericString === 'function'
      ? isValidNumericString(negotiatedPriceStr, false)
      : !isNaN(parseFloat(negotiatedPriceStr)) && parseFloat(negotiatedPriceStr) > 0;

    if (!negotiatedPriceValid) {
      AppDialog.alert({ title: 'ئاگاداری', message: 'تکایە نرخێکی دروست بنووسە', type: 'warning' });
      return;
    }
  }

  const negotiatedPrice = negotiatedPriceStr ? parseFloat(negotiatedPriceStr) : null;

  // Zero Trust: Dynamic price calculation using P(q) = (B / S) * q
  let unitPrice;
  let totalPrice;

  if (!activePrice || activePrice === '' || activePrice === '0' || activePrice === 'null') {
    // Custom-priced service - use negotiated price from user input
    unitPrice = negotiatedPrice;
    totalPrice = Math.round(quantity * unitPrice * 100) / 100;
  } else {
    // Fixed-price service - use dynamic calculation from API data
    const serviceItem = {
      activePrice: parseFloat(activePrice),
      measurementUnit: measurementUnit,
      sofaStandardSetSize: sofaStandardSetSize
    };
    totalPrice = calculateDynamicPrice(serviceItem, quantity);
    unitPrice = Math.round((totalPrice / quantity) * 100) / 100;
  }

  // Check if we're editing an existing item
  if (editingRowId) {
    // Update existing item
    const itemIndex = posCart.findIndex(item => item.rowId === parseInt(editingRowId));
    if (itemIndex !== -1) {
      posCart[itemIndex] = {
        rowId: parseInt(editingRowId),
        serviceId: serviceId,
        coreServiceId: coreServiceId,
        serviceName: serviceName,
        quantity: quantity,
        unitName: measurementUnit,
        unitPrice: unitPrice,
        totalPrice: totalPrice
      };
    }
    // Clear the editing flag
    delete modalServiceId.dataset.editingRowId;
  } else {
    // Add new item
    const newItem = {
      rowId: Date.now(), // Unique identifier for targeted deletions/edits
      serviceId: serviceId,
      coreServiceId: coreServiceId,
      serviceName: serviceName,
      quantity: quantity,
      unitName: measurementUnit,
      unitPrice: unitPrice,
      totalPrice: totalPrice
    };

    // Add to cart
    posCart.push(newItem);
  }

  // Render cart (state-driven UI)
  renderCart();
  renderDynamicInputs();

  // Validate cart state after item addition
  validateCartForSubmission();

  // Close modal
  closeServiceModal();
}

// ── CONFLICT MODAL HANDLING FOR POS ───────────────────────────────────────────
let currentPosRetryFunction = null;

function showPosConflictModal(retryFunction) {
  currentPosRetryFunction = retryFunction;
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
    hidePosConflictModal();
    if (currentPosRetryFunction) {
      currentPosRetryFunction();
    }
  };
  
  // Add cancel button
  const cancelBtn = document.createElement('button');
  cancelBtn.className = 'btn btn-secondary';
  cancelBtn.textContent = 'پاشگەزبوونەوە';
  cancelBtn.onclick = function () {
    hidePosConflictModal();
    // Reset button state
    executeOrderBtn.disabled = false;
    executeOrderBtn.textContent = '';
    
    const checkIcon = document.createElement('i');
    checkIcon.className = 'fa-solid fa-check';
    
    const btnText = document.createTextNode(' جێبەجێکردنی داواکاری');
    
    executeOrderBtn.appendChild(checkIcon);
    executeOrderBtn.appendChild(btnText);
  };
  
  actionsContainer.appendChild(retryBtn);
  actionsContainer.appendChild(cancelBtn);
  
  // Show modal
  conflictModal.classList.add('active');
}

function hidePosConflictModal() {
  const conflictModal = document.getElementById('conflict-modal');
  if (conflictModal) {
    conflictModal.classList.remove('active');
  }
  currentPosRetryFunction = null;
}

// Wire up conflict modal close button
document.addEventListener('DOMContentLoaded', function () {
  const conflictCloseBtn = document.getElementById('conflict-modal-close');
  if (conflictCloseBtn) {
    conflictCloseBtn.addEventListener('click', hidePosConflictModal);
  }
  
  // Close modal on backdrop click
  const conflictModal = document.getElementById('conflict-modal');
  if (conflictModal) {
    conflictModal.addEventListener('click', function (e) {
      if (e.target === conflictModal || e.target.classList.contains('pos-modal-backdrop')) {
        hidePosConflictModal();
        // Reset button state
        executeOrderBtn.disabled = false;
        executeOrderBtn.textContent = '';
        
        const checkIcon = document.createElement('i');
        checkIcon.className = 'fa-solid fa-check';
        
        const btnText = document.createTextNode(' جێبەجێکردنی داواکاری');
        
        executeOrderBtn.appendChild(checkIcon);
        executeOrderBtn.appendChild(btnText);
      }
    });
  }
});

// Admin menu toggle (reuse existing dashboard logic)
const adminToggle = document.getElementById('adminToggle');
const adminDropdown = document.querySelector('.admin-dropdown');

if (adminToggle && adminDropdown) {
  adminToggle.addEventListener('click', (e) => {
    e.stopPropagation();
    adminDropdown.classList.toggle('hidden');
  });

  // Close dropdown when clicking outside
  document.addEventListener('click', (e) => {
    if (!adminToggle.contains(e.target) && !adminDropdown.contains(e.target)) {
      adminDropdown.classList.add('hidden');
    }
  });
}

// Logout button (reuse existing logic)
const logoutBtn = document.getElementById('logoutBtn');
if (logoutBtn) {
  logoutBtn.addEventListener('click', () => {
    // HttpOnly cookie is cleared by backend logout endpoint
    // Redirect to home after logout
    window.location.href = '/index.html';
  });
}

// Customer input validation - call validateCartForSubmission on every keystroke
if (posCustomerNameInput) {
  posCustomerNameInput.addEventListener('input', validateCartForSubmission);
}
if (posCustomerPhoneInput) {
  posCustomerPhoneInput.addEventListener('input', validateCartForSubmission);
}

// ── GLOBAL KEYBOARD SHORTCUTS (O(1) Memory Pattern) ─────────────────────────────
// Single document-level listener for all keyboard shortcuts
document.addEventListener('keydown', function(e) {
  const activeElement = document.activeElement;
  const isTextInput = activeElement && (
    activeElement.tagName === 'INPUT' ||
    activeElement.tagName === 'TEXTAREA' ||
    activeElement.isContentEditable
  );

  // Escape: Clear cart completely
  if (e.key === 'Escape') {
    e.preventDefault();
    clearCart();
    return;
  }

  // Ctrl+Enter: Submit order (only if button exists and enabled)
  if (e.ctrlKey && e.key === 'Enter') {
    const submitBtn = document.getElementById('execute-order-btn');
    if (submitBtn && !submitBtn.disabled) {
      e.preventDefault();
      submitBtn.click();
    }
    return;
  }

  // Alt+[1-9]: Service shortcuts (only if Alt is strictly held)
  if (e.altKey && /^[1-9]$/.test(e.key)) {
    e.preventDefault();
    
    // Dynamic DOM query - no service knowledge required
    const targetService = document.querySelector(`[data-shortcut="${e.key}"]`);
    if (targetService) {
      // Extract service data directly from DOM dataset
      const serviceData = {
        id: targetService.dataset.serviceId,
        coreServiceId: targetService.dataset.coreServiceId,
        kurdishName: targetService.dataset.serviceName,
        englishName: targetService.dataset.englishName,
        measurementUnit: targetService.dataset.measurementUnit,
        activePrice: parseFloat(targetService.dataset.activePrice),
        discountedPrice: parseFloat(targetService.dataset.discountedPrice) || null,
        discountActive: targetService.dataset.discountActive === 'true',
        customPriced: targetService.dataset.customPriced === 'true',
        sofaStandardSetSize: parseFloat(targetService.dataset.sofaStandardSetSize) || 1
      };
      
      // Check if custom-priced service
      if (serviceData.customPriced) {
        selectedCardForCustomPrice = targetService;
        customPriceInput.value = '';
        customPriceModal.classList.add('active');
        customPriceInput.focus();
      } else {
        // Open service modal with data from DOM
        openServiceModalWithData(serviceData);
      }
    }
  }
});

// Clear cart function for Escape key
function clearCart() {
  posCart = [];
  renderCart();
  renderDynamicInputs();
  
  // Clear customer inputs
  if (posCustomerNameInput) posCustomerNameInput.value = '';
  if (posCustomerPhoneInput) posCustomerPhoneInput.value = '';
  if (posCustomerAddressInput) posCustomerAddressInput.value = '';
  if (posCustomerNotesInput) posCustomerNotesInput.value = '';
  
  // Validate cart state after clearing
  validateCartForSubmission();
}

// Open service modal using data from DOM (no state dependency)
function openServiceModalWithData(serviceData) {
  modalServiceName.textContent = serviceData.kurdishName;
  modalServiceId.value = serviceData.id;
  modalServiceId.dataset.coreServiceId = serviceData.coreServiceId;
  modalServiceNameHidden.value = serviceData.kurdishName;
  modalBasePrice.value = serviceData.activePrice;
  modalDefaultUnit.value = serviceData.measurementUnit;
  
  // Configure quantity input based on MeasurementUnit (Dynamic Contextual Modal)
  configureQuantityInputForModal(serviceData.measurementUnit);
  
  // CRITICAL CONSTRAINT: Check if service has fixed price or requires negotiated price
  if (serviceData.customPriced || !serviceData.activePrice || serviceData.activePrice === '' || serviceData.activePrice === '0' || serviceData.activePrice === 'null') {
    // Service requires negotiated price - show negotiated price input
    modalNegotiatedPriceField.classList.remove('hidden');
    modalNegotiatedPrice.required = true;
    modalNegotiatedPrice.value = '';
  } else {
    // Service has fixed price - hide negotiated price input
    modalNegotiatedPriceField.classList.add('hidden');
    modalNegotiatedPrice.required = false;
    modalNegotiatedPrice.value = '';
  }
  
  // Reset quantity input
  modalQuantity.value = '';
  
  // Clear editing flag
  delete modalServiceId.dataset.editingRowId;
  
  // Show modal
  serviceModal.classList.add('active');
  
  // Focus on quantity input after modal render cycle
  setTimeout(() => {
    modalQuantity.focus();
  }, 50);
}
